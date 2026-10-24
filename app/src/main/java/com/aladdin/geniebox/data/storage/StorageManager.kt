package com.aladdin.geniebox.data.storage

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.domain.model.SaveMetadata
import com.aladdin.geniebox.domain.model.SaveType
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import com.aladdin.geniebox.data.db.entity.SaveMetadataEntity
import com.aladdin.geniebox.data.db.entity.FavoriteEntity
import java.security.MessageDigest
import android.system.Os
import android.system.OsConstants

class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"
        private const val MIN_FREE_BYTES = 50 * 1024 * 1024L // 50 MB 安全空間預警
        private const val MAX_BACKUP_JSON_BYTES = 8 * 1024 * 1024L
        private const val MAX_DISC_REFERENCE_DEPTH = 64
    }

    val systemDir: File get() = File(context.filesDir, "system").apply { mkdirs() }
    val ppssppSystemDir: File get() = File(systemDir, "PPSSPP").apply { mkdirs() }
    val coreAssetsDir: File get() = File(context.filesDir, "core-assets").apply { mkdirs() }
    val screenshotsDir: File get() = File(context.filesDir, "screenshots").apply { mkdirs() }
    val romStagingDir: File get() = File(context.cacheDir, "rom-staging").apply { mkdirs() }
    val thumbnailsDir: File get() = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    private val ppssppDeployLock = Any()
    private val restoreLock = Any()
    private val restoreDir get() = File(context.filesDir, ".backup-restore")
    private val identityFile get() = File(context.filesDir, "backup-identities.json")

    private fun writeSynced(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
    }

    private fun copySynced(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() }
        }
        syncDirectory(target.parentFile!!)
    }

    private fun syncDirectory(dir: File) {
        val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }

    private fun replaceFrom(source: File, target: File) {
        val temp = File(target.parentFile, "${target.name}.restore-tmp")
        copySynced(source, temp)
        Os.rename(temp.absolutePath, target.absolutePath)
        syncDirectory(target.parentFile!!)
    }

    private fun finishRestore() {
        val retired = File(context.filesDir, ".backup-restore-complete-${java.util.UUID.randomUUID()}")
        Os.rename(restoreDir.absolutePath, retired.absolutePath)
        syncDirectory(context.filesDir)
        retired.deleteRecursively()
    }

    private fun confinedSaveFile(base: File, path: String): File {
        require(path.startsWith("saves/") || path.startsWith("states/")) { "Invalid save path" }
        val file = File(base, path).canonicalFile
        require(listOf("saves", "states").any {
            file.path.startsWith(File(base, it).canonicalPath + File.separator)
        }) { "Invalid save path" }
        return file
    }

    private fun metadataJson(saves: List<SaveMetadataEntity>, favorites: List<FavoriteEntity>): JSONObject =
        JSONObject().put("saves", JSONArray().apply {
            saves.forEach { s -> put(JSONObject().put("id", s.id).put("gameId", s.gameId)
                .put("coreId", s.coreId).put("coreVersion", s.coreVersion).put("saveType", s.saveType)
                .put("slotIndex", s.slotIndex).put("filePath", s.filePath)
                .put("thumbnailPath", s.thumbnailPath ?: JSONObject.NULL).put("sizeBytes", s.sizeBytes)
                .put("timestamp", s.timestamp).put("formatVersion", s.formatVersion)) }
        }).put("favorites", JSONArray().apply {
            favorites.forEach { f -> put(JSONObject().put("gameId", f.gameId).put("addedTime", f.addedTime)) }
        })

    private fun writeRestoreJournal(
        staged: List<Pair<File, File>>,
        incoming: Pair<List<SaveMetadataEntity>, List<FavoriteEntity>>?
    ) {
        val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
        val saveIds = incoming?.first?.map { it.id }.orEmpty().toSet()
        val favoriteIds = incoming?.second?.map { it.gameId }.orEmpty().toSet()
        val old = runBlocking {
            metadataJson(db.saveMetadataDao().getAllSavesSync().filter { it.id in saveIds },
                db.favoriteDao().getAllFavoritesSync().filter { it.gameId in favoriteIds })
        }
        val journal = JSONObject().put("oldMetadata", old)
            .put("saveIds", JSONArray(saveIds.toList())).put("favoriteIds", JSONArray(favoriteIds.toList()))
            .put("files", JSONArray().apply {
                staged.forEach { (_, target) -> put(JSONObject()
                    .put("path", target.relativeTo(context.filesDir).invariantSeparatorsPath)
                    .put("existed", target.exists())) }
            })
        val bytes = journal.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_JSON_BYTES) { "Restore journal too large" }
        val temp = File(restoreDir, "journal.tmp")
        writeSynced(temp, bytes)
        Os.rename(temp.absolutePath, File(restoreDir, "journal.json").absolutePath)
        syncDirectory(restoreDir)
        syncDirectory(context.filesDir)
    }

    /** Recover files and metadata together before any game can read or write saves. */
    fun recoverPendingRestore(): Unit = synchronized(restoreLock) {
        if (!restoreDir.exists()) return@synchronized
        val journalFile = File(restoreDir, "journal.json")
        if (File(restoreDir, "committed").exists() || !journalFile.exists()) {
            finishRestore()
            return@synchronized
        }
        require(journalFile.length() <= MAX_BACKUP_JSON_BYTES)
        val journal = JSONObject(journalFile.readText())
        val files = journal.getJSONArray("files")
        for (index in 0 until files.length()) {
            val entry = files.getJSONObject(index)
            val path = entry.getString("path")
            val target = if (path == identityFile.name) identityFile else confinedSaveFile(context.filesDir, path)
            if (entry.getBoolean("existed")) {
                replaceFrom(File(restoreDir, path), target)
            } else if (target.exists()) {
                check(target.delete()) { "Cannot remove incomplete restore file" }
                syncDirectory(target.parentFile!!)
            }
        }
        val old = parseAndValidateMetadata(journal.getJSONObject("oldMetadata").toString(), context.filesDir)
        val saves = journal.getJSONArray("saveIds").let { array -> (0 until array.length()).map { array.getString(it) } }
        val favorites = journal.getJSONArray("favoriteIds").let { array -> (0 until array.length()).map { array.getString(it) } }
        val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
        runBlocking {
            db.withTransaction {
                saves.chunked(500).forEach { db.gameDao().removeRestoredSaves(it) }
                favorites.chunked(500).forEach { db.gameDao().removeDocumentFavorites(it) }
                db.saveMetadataDao().insertAllSaves(old.first)
                db.favoriteDao().insertAllFavorites(old.second)
            }
        }
        finishRestore()
    }

    private fun readIdentities(): JSONObject {
        if (!identityFile.exists()) return JSONObject().put("games", JSONArray()).put("bindings", JSONObject())
        require(identityFile.length() <= MAX_BACKUP_JSON_BYTES)
        return JSONObject(identityFile.readText())
    }

    fun backupAliases(gameId: String): List<String> = synchronized(restoreLock) {
        val aliases = readIdentities().optJSONObject("bindings")?.optJSONArray(gameId) ?: return@synchronized emptyList()
        (0 until aliases.length()).map { aliases.getString(it) }
    }

    private fun fingerprint(game: com.aladdin.geniebox.data.db.entity.GameEntity): String? = try {
        val app = context.applicationContext as com.aladdin.geniebox.app.GenieApplication
        val digest = MessageDigest.getInstance("SHA-256")
        val visited = mutableSetOf<String>()
        fun append(uri: android.net.Uri, name: String, parent: androidx.documentfile.provider.DocumentFile?) {
            val key = com.aladdin.geniebox.data.db.dao.romDocumentKey(uri.toString())
            check(visited.add(key) && visited.size <= MAX_DISC_REFERENCE_DEPTH) { "Cyclic or oversized disc set" }
            val extension = name.substringAfterLast('.', "").lowercase()
            val isPlaylist = extension == "cue" || extension == "m3u"
            val text = if (isPlaylist) ByteArrayOutputStream() else null
            val member = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri) ?: error("ROM unavailable")
            input.use {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    member.update(buffer, 0, read)
                    if (text != null) {
                        require(text.size().toLong() + read <= MAX_BACKUP_JSON_BYTES) { "Disc playlist too large" }
                        text.write(buffer, 0, read)
                    }
                }
            }
            digest.update(member.digest())
            if (text != null) {
                val content = text.toString("UTF-8")
                val references = if (extension == "cue") app.safBridge.parseCueReferencedFiles(content)
                    else app.safBridge.parseM3uEntries(content)
                for (reference in references.distinct()) {
                    val path = reference.replace('\\', '/')
                    require(!path.startsWith("/") && !path.contains("..")) { "Invalid disc reference" }
                    val parts = path.split('/').filter { it.isNotEmpty() }
                    require(parts.isNotEmpty())
                    var directory = parent ?: error("Disc directory unavailable")
                    parts.dropLast(1).forEach { segment ->
                        directory = directory.findFile(segment)?.takeIf { it.isDirectory }
                            ?: error("Disc directory unavailable")
                    }
                    val file = directory.findFile(parts.last()) ?: error("Disc member unavailable")
                    append(file.uri, parts.last(), directory)
                }
            }
            visited.remove(key)
        }
        val name = File(game.relativePath).name
        val needsParent = name.substringAfterLast('.', "").lowercase() in setOf("cue", "m3u")
        val parent = if (needsParent) {
            val source = runBlocking { app.romSourceRepository.getSource(game.sourceId) }
                ?: error("ROM source unavailable")
            app.safBridge.resolveParentDirDoc(android.net.Uri.parse(source.treeUri), game.relativePath)
        } else null
        append(android.net.Uri.parse(game.documentUri), name, parent)
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot fingerprint ROM", e)
        null
    }

    private fun mergeIdentities(first: JSONObject, second: JSONObject): JSONObject {
        val records = linkedMapOf<String, JSONObject>()
        listOf(first, second).forEach { root ->
            val games = root.optJSONArray("games") ?: JSONArray()
            for (index in 0 until games.length()) {
                val record = games.getJSONObject(index)
                records[record.getString("id")] = record
            }
        }
        return JSONObject().put("games", JSONArray(records.values.toList()))
            .put("bindings", first.optJSONObject("bindings") ?: JSONObject())
            .put("checked", first.optJSONObject("checked") ?: JSONObject())
    }

    private fun exportGameIdentities(): JSONObject {
        val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
        val games = runBlocking { db.gameDao().getIndexedGames() }.map { it.entry.game }
        val records = JSONArray()
        games.forEach { game ->
            if (game.isAvailable) fingerprint(game)?.let { hash ->
                records.put(JSONObject().put("id", game.id).put("platform", game.platform)
                    .put("size", game.sizeBytes).put("sha256", hash))
            }
        }
        return mergeIdentities(readIdentities(), JSONObject().put("games", records))
    }

    private fun bindIdentities(root: JSONObject) {
        val records = root.getJSONArray("games")
        val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
        val games = runBlocking { db.gameDao().getIndexedGames() }.map { it.entry.game }
        val bindings = root.optJSONObject("bindings") ?: JSONObject()
        val checked = root.optJSONObject("checked") ?: JSONObject()
        val revision = MessageDigest.getInstance("SHA-256").digest(records.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        games.filter { it.isAvailable }.forEach { game ->
            val signature = "${game.sizeBytes}:${game.lastModified}:$revision"
            if (game.entryType !in setOf("CUE_BIN", "M3U_DISC_SET") &&
                game.lastModified > 0 && checked.optString(game.id) == signature) return@forEach
            val candidates = (0 until records.length()).map { records.getJSONObject(it) }
                .filter { it.getString("platform") == game.platform && it.getLong("size") == game.sizeBytes }
            if (candidates.isNotEmpty()) fingerprint(game)?.let { hash ->
                val aliases = candidates.filter { it.getString("sha256") == hash }.map { it.getString("id") }.distinct()
                bindings.put(game.id, JSONArray(aliases))
                checked.put(game.id, signature)
            }
        }
        root.put("bindings", bindings).put("checked", checked)
    }

    fun reconcileBackupGames(): Unit = synchronized(restoreLock) {
        if (!identityFile.exists()) return@synchronized
        recoverPendingRestore()
        val identities = readIdentities()
        bindIdentities(identities)
        val bytes = identities.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_JSON_BYTES)
        check(atomicWrite(identityFile, bytes)) { "Cannot persist ROM backup bindings" }
        val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
        runBlocking {
            val favorites = db.favoriteDao().getAllFavoritesSync()
            val bindings = identities.getJSONObject("bindings")
            bindings.keys().forEach { gameId ->
                val aliases = bindings.getJSONArray(gameId)
                val match = favorites.filter { favorite ->
                    (0 until aliases.length()).any { aliases.getString(it) == favorite.gameId }
                }.maxByOrNull { it.addedTime }
                if (match != null) db.favoriteDao().addFavorite(match.copy(gameId = gameId))
            }
        }
    }

    /**
     * 部署 PPSSPP 執行期資源至 files/system/PPSSPP
     * PPSSPP libretro 透過 GET_SYSTEM_DIRECTORY 加上 /PPSSPP 尋找 flash0、字型、shaders 與配置
     */
    fun deployPpssppAssetsIfNeeded(): Boolean = synchronized(ppssppDeployLock) {
        val targetDir = ppssppSystemDir
        val markerFile = File(targetDir, ".deployed_version")
        val currentVersion = "v1.17.1"

        if (markerFile.exists() && markerFile.readText().trim() == currentVersion && File(targetDir, "flash0/font").exists()) {
            return true
        }

        return try {
            copyAssetFolder(context.assets, "ppsspp", targetDir)
            markerFile.writeText(currentVersion)
            Log.i(TAG, "PPSSPP assets successfully deployed to ${targetDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy PPSSPP assets", e)
            false
        }
    }

    private fun copyAssetFolder(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val files = assetManager.list(assetPath) ?: return
        if (files.isEmpty()) {
            copyAssetFile(assetManager, assetPath, targetDir)
        } else {
            targetDir.mkdirs()
            for (filename in files) {
                val subAssetPath = if (assetPath.isEmpty()) filename else "$assetPath/$filename"
                val subTargetFile = File(targetDir, filename)
                val subChildren = assetManager.list(subAssetPath)
                if (subChildren != null && subChildren.isNotEmpty()) {
                    copyAssetFolder(assetManager, subAssetPath, subTargetFile)
                } else {
                    copyAssetFile(assetManager, subAssetPath, subTargetFile)
                }
            }
        }
    }

    private fun copyAssetFile(assetManager: android.content.res.AssetManager, assetPath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        assetManager.open(assetPath).use { input ->
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.delete()
                tmpFile.renameTo(targetFile)
            }
        }
    }

    fun getSramDir(platform: PlatformType, gameId: String): File {
        return File(context.filesDir, "saves/${platform.id}/$gameId").apply { mkdirs() }
    }

    fun getStateDir(coreId: String, gameId: String): File {
        return File(context.filesDir, "states/$coreId/$gameId").apply { mkdirs() }
    }

    fun checkStorageAvailable(requiredBytes: Long = MIN_FREE_BYTES): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val available = stat.availableBytes
            available > requiredBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect free storage bytes", e)
            true
        }
    }

    /**
     * 安全原子寫入：先寫入同目錄暫存檔 (.tmp)，再以 renameTo 原子替換，防止寫入中斷損毀既有存檔
     */
    fun atomicWrite(targetFile: File, data: ByteArray): Boolean {
        if (!checkStorageAvailable(data.size + MIN_FREE_BYTES)) {
            Log.e(TAG, "Write rejected: Insufficient internal storage capacity for ${targetFile.name}")
            return false
        }

        val parent = targetFile.parentFile ?: return false
        if (!parent.exists()) parent.mkdirs()

        val tempFile = File(parent, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
        val backupFile = File(parent, "${targetFile.name}.bak")

        return try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync() // 強制刷入磁碟
            }

            // 若舊檔存在，先建立備份
            if (targetFile.exists()) {
                android.system.Os.rename(targetFile.absolutePath, backupFile.absolutePath)
            }

            val success = tempFile.renameTo(targetFile)
            if (success) syncDirectory(parent)
            if (!success) {
                // 替換失敗，還原備份
                if (backupFile.exists()) backupFile.renameTo(targetFile)
                tempFile.delete()
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Atomic write failed for ${targetFile.absolutePath}", e)
            if (backupFile.exists() && !targetFile.exists()) {
                backupFile.renameTo(targetFile)
            }
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    fun writeSaveState(file: File, data: ByteArray, previous: SaveMetadata?, metadata: SaveMetadataEntity): Boolean =
        synchronized(restoreLock) {
            recoverPendingRestore()
            val targets = listOf(file, File("$file.bak"), File("$file.previous"))
            var applying = false
            var committed = false
            try {
                check(checkStorageAvailable(data.size + targets.sumOf { it.length() } + MIN_FREE_BYTES))
                check(restoreDir.mkdirs())
                targets.filter { it.exists() }.forEach { target ->
                    copySynced(target, File(restoreDir, target.relativeTo(context.filesDir).path))
                }
                restoreDir.walkBottomUp().filter { it.isDirectory }.forEach { syncDirectory(it) }
                writeRestoreJournal(targets.map { it to it }, listOf(metadata) to emptyList())
                applying = true
                if (previous != null) check(snapshotState(file, previous.coreVersion,
                    previous.formatVersion, previous.timestamp))
                check(atomicWrite(file, data))
                val db = (context.applicationContext as com.aladdin.geniebox.app.GenieApplication).database
                runBlocking { db.saveMetadataDao().insertOrUpdateSave(metadata) }
                writeSynced(File(restoreDir, "committed"), byteArrayOf(1))
                syncDirectory(restoreDir)
                committed = true
                finishRestore()
                true
            } catch (e: Exception) {
                Log.e(TAG, "State transaction failed", e)
                if (committed) return@synchronized true
                if (applying) recoverPendingRestore() else if (restoreDir.exists()) finishRestore()
                false
            }
        }

    // 保留上一份手動存檔及其核心版本；串流複製，避免額外載入整份狀態到記憶體。
    fun snapshotState(file: File, coreVersion: String, formatVersion: Int, timestamp: Long): Boolean {
        val source = file.takeIf { it.isFile } ?: File("${file.absolutePath}.bak")
        if (!source.isFile || source.length() !in 1..Int.MAX_VALUE.toLong() ||
            !checkStorageAvailable(source.length() + MIN_FREE_BYTES)) return false
        val snapshot = File("${file.absolutePath}.previous")
        val temp = File("${snapshot.absolutePath}.${java.util.UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(temp).use { stream ->
                val output = java.io.DataOutputStream(stream)
                output.writeUTF(coreVersion)
                output.writeInt(formatVersion)
                output.writeLong(timestamp)
                output.writeInt(source.length().toInt())
                FileInputStream(source).use { it.copyTo(output) }
                output.flush()
                stream.fd.sync()
            }
            Os.rename(temp.absolutePath, snapshot.absolutePath)
            syncDirectory(snapshot.parentFile!!)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot preserve previous state", e)
            false
        } finally {
            temp.delete()
        }
    }

    /**
     * 讀取存檔檔案，若目標檔不存在但存在 .bak 備份（因前次寫入中斷），自動執行 crash recovery 復原
     */
    fun readBytes(targetFile: File): ByteArray? {
        val parent = targetFile.parentFile
        val backupFile = if (parent != null) File(parent, "${targetFile.name}.bak") else null

        if (!targetFile.exists() && backupFile != null && backupFile.exists()) {
            Log.w(TAG, "Target file missing, recovering from backup: ${backupFile.name}")
            backupFile.renameTo(targetFile)
        }

        val readableFile = (if (targetFile.isFile) targetFile else backupFile?.takeIf { it.isFile })
            ?: return null
        return try {
            FileInputStream(readableFile).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file ${targetFile.absolutePath}", e)
            // 若目標損壞嘗試讀取備份
            if (backupFile != null && backupFile.exists()) {
                try {
                    FileInputStream(backupFile).use { it.readBytes() }
                } catch (be: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * 清理可回收的 ROM Staging 快取，嚴禁觸碰 saves/ 與 states/
     */
    /**
     * 清理可回收的 ROM Staging 快取，遞迴刪除非空目錄並準確累計釋放空間 (R2-13)
     */
    fun clearRomStagingCache(): Long {
        var reclaimed = 0L
        romStagingDir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                f.walkTopDown().forEach { child ->
                    if (child.isFile) reclaimed += child.length()
                }
                f.deleteRecursively()
            } else {
                reclaimed += f.length()
                f.delete()
            }
        }
        return reclaimed
    }

    /**
     * 匯出完整備份 (saves + states + metadata_records.json + manifest.json) 至 OutputStream (支援 SAF) (R2-14)
     */
    fun exportBackup(outputStream: OutputStream): Boolean {
        return try {
            recoverPendingRestore()
            val app = context.applicationContext as? com.aladdin.geniebox.app.GenieApplication
            val db = app?.database

            val saveEntities = runBlocking { db?.saveMetadataDao()?.getAllSavesSync() } ?: emptyList()
            val favEntities = runBlocking { db?.favoriteDao()?.getAllFavoritesSync() } ?: emptyList()

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                val baseDir = context.filesDir
                val fileList = mutableListOf<String>()
                var savesCount = 0
                var statesCount = 0

                listOf("saves", "states").forEach { subName ->
                    val subDir = File(baseDir, subName)
                    if (subDir.exists()) {
                        subDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relPath = file.relativeTo(baseDir).path.replace('\\', '/')
                                zos.putNextEntry(ZipEntry(relPath))
                                file.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                                fileList.add(relPath)
                                if (relPath.startsWith("saves/")) savesCount++
                                if (relPath.startsWith("states/")) statesCount++
                            }
                        }
                    }
                }

                // 匯出 metadata_records.json (R2-14, R3-05)
                val metadataJson = metadataJson(saveEntities, favEntities).toString()
                val identities = exportGameIdentities().toString().toByteArray(Charsets.UTF_8)
                require(metadataJson.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_JSON_BYTES)
                require(identities.size <= MAX_BACKUP_JSON_BYTES)
                zos.putNextEntry(ZipEntry("backup-identities.json"))
                zos.write(identities)
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("metadata_records.json"))
                zos.write(metadataJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 加入 manifest.json
                val manifestJson = """
                    {
                      "formatVersion": 1,
                      "app": "Aladdin GenieBox",
                      "exportTimestamp": ${System.currentTimeMillis()},
                      "savesCount": $savesCount,
                      "statesCount": $statesCount,
                      "metadataSavesCount": ${saveEntities.size},
                      "favoritesCount": ${favEntities.size},
                      "totalFiles": ${fileList.size}
                    }
                """.trimIndent()
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export backup stream failed", e)
            false
        }
    }

    fun exportBackup(destZipFile: File): Boolean {
        return try {
            FileOutputStream(destZipFile).use { exportBackup(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Export backup to file failed", e)
            false
        }
    }

    /**
     * 匯入備份 Zip 資料流 (支援 SAF)。
     * 採嚴格三階段事務流程 (R4-01, R4-02)：
     * 1. 隔離暫存解壓與全量預先校驗：防 Zip 炸彈、路徑穿越，並於覆寫前完全解析與驗證 saves/favorites metadata，若無效直接中止，原進度零改動。
     * 2. 安全建立 rollback 快照：備份檔先寫入 .tmp 再 rename，若備份準備中途失敗（如磁碟空間不足），絕不執行回復，避免以殘缺備份覆寫原檔。
     * 3. 協調式原子套用：覆寫檔案與資料庫以 Room withTransaction 共同協調；若發生任何例外，刪除新產物並自完整快照還原；若回滾異常則保留快照副本，防止資料損失。
     */
    fun importBackup(inputStream: InputStream): Boolean = synchronized(restoreLock) {
        recoverPendingRestore()
        val destBase = context.filesDir
        val tempRestoreDir = File(context.cacheDir, "backup-restore-temp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val rollbackDir = restoreDir.apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        var entryCount = 0
        var totalExtractedBytes = 0L
        val maxAllowedBytes = 1024 * 1024 * 1024L // 1 GB 防 Zip 炸彈
        val maxAllowedEntries = 5000
        var metadataFile: File? = null

        // 追蹤階段與受影響檔案，明確區分準備失敗與套用失敗 (R4-01)
        var isApplying = false

        return@synchronized try {
            // 階段 1.1：解壓至臨時安全目錄並檢查防護
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > maxAllowedEntries) {
                        throw IllegalStateException("Too many files in backup archive: $entryCount")
                    }

                    val name = entry.name.replace('\\', '/')
                    require(!name.contains("..") && !name.startsWith("/")) { "Invalid backup path" }
                    val isJson = name in setOf("metadata_records.json", "manifest.json", "backup-identities.json")
                    val accepted = isJson || name.startsWith("saves/") || name.startsWith("states/")
                    val outFile = if (accepted && !entry.isDirectory) File(tempRestoreDir, name) else null
                    outFile?.parentFile?.mkdirs()
                    val output = outFile?.outputStream()
                    try {
                        val buffer = ByteArray(32 * 1024)
                        var entryBytes = 0L
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            totalExtractedBytes += read
                            entryBytes += read
                            require(totalExtractedBytes <= maxAllowedBytes) { "Backup size limit exceeded" }
                            require(!isJson || entryBytes <= MAX_BACKUP_JSON_BYTES) { "Backup JSON too large" }
                            output?.write(buffer, 0, read)
                        }
                    } finally {
                        output?.close()
                    }
                    if (name == "metadata_records.json") metadataFile = outFile
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 階段 1.2：在套用前完整解析並校驗 metadata 實體（saves 與 favorites 全量欄位檢驗）(R4-02)
            var parsedMetadata: Pair<List<com.aladdin.geniebox.data.db.entity.SaveMetadataEntity>, List<com.aladdin.geniebox.data.db.entity.FavoriteEntity>>? =
                if (metadataFile != null && metadataFile!!.exists()) {
                    parseAndValidateMetadata(metadataFile!!.readText(Charsets.UTF_8), destBase)
                } else null

            val incomingIdentities = File(tempRestoreDir, "backup-identities.json")
            if (incomingIdentities.isFile) {
                val merged = mergeIdentities(readIdentities(), JSONObject(incomingIdentities.readText()))
                bindIdentities(merged)
                incomingIdentities.writeText(merged.toString())
                val favorites = parsedMetadata?.second?.toMutableList()
                if (favorites != null) {
                    val bindings = merged.getJSONObject("bindings")
                    val restored = favorites.toList()
                    bindings.keys().forEach { currentId ->
                        val aliases = bindings.getJSONArray(currentId)
                        val times = restored.filter { favorite ->
                            (0 until aliases.length()).any { aliases.getString(it) == favorite.gameId }
                        }.map { it.addedTime }
                        if (times.isNotEmpty()) favorites.add(
                            com.aladdin.geniebox.data.db.entity.FavoriteEntity(currentId, times.max())
                        )
                    }
                    parsedMetadata = parsedMetadata?.let { it.first to favorites.distinctBy { f -> f.gameId } }
                }
            }

            // 階段 2：收集受影響檔案並建立可靠的 Rollback 副本 (R4-01)
            val stagedFiles = mutableListOf<Pair<File, File>>() // (tempFile, targetFile)
            tempRestoreDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(tempRestoreDir).path.replace('\\', '/')
                    if (relPath.startsWith("saves/") || relPath.startsWith("states/") || relPath == "backup-identities.json") {
                        val targetFile = File(destBase, relPath)
                        stagedFiles.add(file to targetFile)
                    }
                }
            }

            // 準備 rollback 副本：寫入 .tmp 後 rename，若空間不足拋出例外時 isApplying 仍為 false
            stagedFiles.forEach { (_, targetFile) ->
                if (targetFile.exists()) {
                    val relPath = targetFile.relativeTo(destBase).path.replace('\\', '/')
                    val backupTarget = File(rollbackDir, relPath)
                    val backupTmp = File(rollbackDir, "$relPath.tmp")
                    backupTarget.parentFile?.mkdirs()
                    copySynced(targetFile, backupTmp)
                    Os.rename(backupTmp.absolutePath, backupTarget.absolutePath)
                }
            }
            rollbackDir.walkBottomUp().filter { it.isDirectory }.forEach { syncDirectory(it) }

            // 階段 3：正式套用階段 (isApplying = true)
            writeRestoreJournal(stagedFiles, parsedMetadata)
            isApplying = true

            // 3.1 覆寫檔案
            stagedFiles.forEach { (srcFile, targetFile) ->
                targetFile.parentFile?.mkdirs()
                replaceFrom(srcFile, targetFile)
            }

            // 3.2 寫入 Room 資料庫，使用單一 transaction 共同提交 saves 與 favorites (R4-02)
            if (parsedMetadata != null) {
                val app = context.applicationContext as? com.aladdin.geniebox.app.GenieApplication
                    ?: throw IllegalStateException("GenieApplication not available")
                val db = app.database
                val (savesList, favList) = parsedMetadata!!

                runBlocking {
                    db.withTransaction {
                        if (savesList.isNotEmpty()) {
                            db.saveMetadataDao().insertAllSaves(savesList)
                        }
                        if (favList.isNotEmpty()) {
                            db.favoriteDao().insertAllFavorites(favList)
                        }
                    }
                }
                Log.i(TAG, "Successfully restored database metadata for ${savesList.size} saves and ${favList.size} favorites")
            }

            writeSynced(File(rollbackDir, "committed"), byteArrayOf(1))
            syncDirectory(rollbackDir)
            // 套用成功，清理暫存與 rollback 副本
            finishRestore()
            tempRestoreDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import backup stream failed (isApplying=$isApplying)", e)

            if (isApplying) {
                try {
                    recoverPendingRestore()
                } catch (re: Exception) {
                    Log.e(TAG, "Restore recovery pending; preserving durable snapshot", re)
                }
            } else {
                rollbackDir.deleteRecursively()
            }

            tempRestoreDir.deleteRecursively()
            false
        }
    }

    private fun parseAndValidateMetadata(
        jsonStr: String,
        destBase: File
    ): Pair<List<com.aladdin.geniebox.data.db.entity.SaveMetadataEntity>, List<com.aladdin.geniebox.data.db.entity.FavoriteEntity>> {
        val root = JSONObject(jsonStr)
        val savesList = mutableListOf<com.aladdin.geniebox.data.db.entity.SaveMetadataEntity>()
        val favList = mutableListOf<com.aladdin.geniebox.data.db.entity.FavoriteEntity>()

        if (root.has("saves")) {
            val savesArr = root.getJSONArray("saves")
            for (i in 0 until savesArr.length()) {
                val obj = savesArr.getJSONObject(i)

                // 嚴格校驗必要欄位存在性與格式 (R4-02)
                val id = obj.getString("id")
                val gameId = obj.getString("gameId")
                val coreId = obj.getString("coreId")
                val saveType = obj.getString("saveType")
                val slotIndex = obj.getInt("slotIndex")
                val timestamp = obj.getLong("timestamp")
                val sizeBytes = obj.getLong("sizeBytes")
                val coreVersion = obj.optString("coreVersion", "1.0")
                val formatVersion = obj.optInt("formatVersion", 1)

                // 重建當前設備本機 filePath，避免跨設備/使用者無效路徑 (R3-09)
                val rawPath = obj.getString("filePath").replace('\\', '/')
                val relPath = when {
                    rawPath.contains("/saves/") -> "saves/" + rawPath.substringAfter("/saves/")
                    rawPath.contains("/states/") -> "states/" + rawPath.substringAfter("/states/")
                    rawPath.startsWith("saves/") -> rawPath
                    rawPath.startsWith("states/") -> rawPath
                    saveType == "NATIVE_SRAM" -> "saves/$coreId/$gameId.srm"
                    else -> "states/$coreId/$gameId/slot_$slotIndex.state"
                }
                val localFilePath = confinedSaveFile(destBase, relPath).absolutePath

                // 重建 thumbnailPath (R3-05, R3-09)
                val rawThumb = if (obj.has("thumbnailPath") && !obj.isNull("thumbnailPath")) {
                    obj.getString("thumbnailPath").replace('\\', '/')
                } else null

                val localThumbPath = if (!rawThumb.isNullOrEmpty() && rawThumb != "null") {
                    when {
                        rawThumb.contains("/thumbnails/") -> File(destBase, "thumbnails/" + rawThumb.substringAfter("/thumbnails/")).absolutePath
                        rawThumb.contains("/states/") -> confinedSaveFile(destBase, "states/" + rawThumb.substringAfter("/states/")).absolutePath
                        else -> File(destBase, rawThumb).absolutePath
                    }
                } else null

                savesList.add(
                    com.aladdin.geniebox.data.db.entity.SaveMetadataEntity(
                        id = id,
                        gameId = gameId,
                        coreId = coreId,
                        coreVersion = coreVersion,
                        formatVersion = formatVersion,
                        saveType = saveType,
                        slotIndex = slotIndex,
                        timestamp = timestamp,
                        filePath = localFilePath,
                        thumbnailPath = localThumbPath,
                        sizeBytes = sizeBytes
                    )
                )
            }
        }

        if (root.has("favorites")) {
            val favsArr = root.getJSONArray("favorites")
            for (i in 0 until favsArr.length()) {
                val obj = favsArr.getJSONObject(i)
                val gameId = obj.getString("gameId")
                val addedTime = obj.getLong("addedTime")

                favList.add(
                    com.aladdin.geniebox.data.db.entity.FavoriteEntity(
                        gameId = gameId,
                        addedTime = addedTime
                    )
                )
            }
        }

        return Pair(savesList, favList)
    }

    fun importBackup(srcZipFile: File): Boolean {
        return try {
            FileInputStream(srcZipFile).use { importBackup(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Import backup from file failed", e)
            false
        }
    }
}
