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

class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"
        private const val MIN_FREE_BYTES = 50 * 1024 * 1024L // 50 MB 安全空間預警
    }

    val systemDir: File get() = File(context.filesDir, "system").apply { mkdirs() }
    val ppssppSystemDir: File get() = File(systemDir, "PPSSPP").apply { mkdirs() }
    val coreAssetsDir: File get() = File(context.filesDir, "core-assets").apply { mkdirs() }
    val screenshotsDir: File get() = File(context.filesDir, "screenshots").apply { mkdirs() }
    val romStagingDir: File get() = File(context.cacheDir, "rom-staging").apply { mkdirs() }
    val thumbnailsDir: File get() = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    private val ppssppDeployLock = Any()

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
                if (backupFile.exists()) backupFile.delete()
                targetFile.renameTo(backupFile)
            }

            val success = tempFile.renameTo(targetFile)
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

        if (!targetFile.exists()) return null
        return try {
            FileInputStream(targetFile).use { it.readBytes() }
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
                val metadataJson = buildString {
                    append("{\n  \"saves\": [\n")
                    saveEntities.forEachIndexed { idx, s ->
                        append("    {\"id\": \"${s.id}\", \"gameId\": \"${s.gameId}\", \"coreId\": \"${s.coreId}\", \"coreVersion\": \"${s.coreVersion}\", \"saveType\": \"${s.saveType}\", \"slotIndex\": ${s.slotIndex}, \"filePath\": \"${s.filePath.replace('\\', '/')}\", \"thumbnailPath\": ${if (s.thumbnailPath != null) "\"${s.thumbnailPath.replace('\\', '/')}\"" else "null"}, \"sizeBytes\": ${s.sizeBytes}, \"timestamp\": ${s.timestamp}, \"formatVersion\": ${s.formatVersion}}")
                        if (idx < saveEntities.size - 1) append(",")
                        append("\n")
                    }
                    append("  ],\n  \"favorites\": [\n")
                    favEntities.forEachIndexed { idx, f ->
                        append("    {\"gameId\": \"${f.gameId}\", \"addedTime\": ${f.addedTime}}")
                        if (idx < favEntities.size - 1) append(",")
                        append("\n")
                    }
                    append("  ]\n}")
                }
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
    fun importBackup(inputStream: InputStream): Boolean {
        val destBase = context.filesDir
        val tempRestoreDir = File(context.cacheDir, "backup-restore-temp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val rollbackDir = File(context.cacheDir, "backup-rollback-${System.currentTimeMillis()}").apply {
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
        val backedUpOriginalFiles = mutableListOf<Pair<File, File>>() // (targetFile, backupFile)
        val newlyCreatedTargets = mutableListOf<File>()

        return try {
            // 階段 1.1：解壓至臨時安全目錄並檢查防護
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > maxAllowedEntries) {
                        throw IllegalStateException("Too many files in backup archive: $entryCount")
                    }

                    val name = entry.name.replace('\\', '/')
                    // 防路徑穿越
                    if (name.contains("..") || name.startsWith("/")) {
                        Log.w(TAG, "Skipping malicious entry: $name")
                        entry = zis.nextEntry
                        continue
                    }

                    if (name == "metadata_records.json") {
                        val metaTarget = File(tempRestoreDir, "metadata_records.json")
                        FileOutputStream(metaTarget).use { zis.copyTo(it) }
                        metadataFile = metaTarget
                    } else if (name == "manifest.json") {
                        val manifestContent = zis.bufferedReader(Charsets.UTF_8).readText()
                        Log.i(TAG, "Found backup manifest: $manifestContent")
                    } else if (name.startsWith("saves/") || name.startsWith("states/")) {
                        val outFile = File(tempRestoreDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                val buf = ByteArray(32 * 1024)
                                var read: Int
                                while (zis.read(buf).also { read = it } != -1) {
                                    totalExtractedBytes += read
                                    if (totalExtractedBytes > maxAllowedBytes) {
                                        throw IllegalStateException("Zip bomb protection triggered: $totalExtractedBytes bytes")
                                    }
                                    fos.write(buf, 0, read)
                                }
                                fos.flush()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 階段 1.2：在套用前完整解析並校驗 metadata 實體（saves 與 favorites 全量欄位檢驗）(R4-02)
            val parsedMetadata: Pair<List<com.aladdin.geniebox.data.db.entity.SaveMetadataEntity>, List<com.aladdin.geniebox.data.db.entity.FavoriteEntity>>? =
                if (metadataFile != null && metadataFile!!.exists()) {
                    parseAndValidateMetadata(metadataFile!!.readText(Charsets.UTF_8), destBase)
                } else null

            // 階段 2：收集受影響檔案並建立可靠的 Rollback 副本 (R4-01)
            val stagedFiles = mutableListOf<Pair<File, File>>() // (tempFile, targetFile)
            tempRestoreDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(tempRestoreDir).path.replace('\\', '/')
                    if (relPath.startsWith("saves/") || relPath.startsWith("states/")) {
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
                    targetFile.copyTo(backupTmp, overwrite = true)
                    if (!backupTmp.renameTo(backupTarget)) {
                        backupTmp.copyTo(backupTarget, overwrite = true)
                        backupTmp.delete()
                    }
                    backedUpOriginalFiles.add(targetFile to backupTarget)
                }
            }

            // 階段 3：正式套用階段 (isApplying = true)
            isApplying = true

            // 3.1 覆寫檔案
            stagedFiles.forEach { (srcFile, targetFile) ->
                if (!targetFile.exists()) {
                    newlyCreatedTargets.add(targetFile)
                }
                targetFile.parentFile?.mkdirs()
                srcFile.copyTo(targetFile, overwrite = true)
            }

            // 3.2 寫入 Room 資料庫，使用單一 transaction 共同提交 saves 與 favorites (R4-02)
            if (parsedMetadata != null) {
                val app = context.applicationContext as? com.aladdin.geniebox.app.GenieApplication
                    ?: throw IllegalStateException("GenieApplication not available")
                val db = app.database
                val (savesList, favList) = parsedMetadata

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

            // 套用成功，清理暫存與 rollback 副本
            rollbackDir.deleteRecursively()
            tempRestoreDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import backup stream failed (isApplying=$isApplying)", e)

            if (isApplying) {
                // 只有在進入套用階段後出錯，才執行安全回滾 (R4-01)
                var rollbackSuccess = true
                try {
                    // 1. 刪除本次新建檔案
                    newlyCreatedTargets.forEach { file ->
                        if (file.exists()) file.delete()
                    }
                    // 2. 還原已備份的原始檔案
                    backedUpOriginalFiles.forEach { (targetFile, backupFile) ->
                        if (backupFile.exists()) {
                            backupFile.copyTo(targetFile, overwrite = true)
                        }
                    }
                    Log.i(TAG, "Rollback successfully restored all modified files")
                } catch (re: Exception) {
                    rollbackSuccess = false
                    Log.e(TAG, "CRITICAL: Rollback failed during recovery, preserving rollback directory: ${rollbackDir.absolutePath}", re)
                }

                // 若回滾完全成功才刪除 rollbackDir；若回滾失敗保留現場副本以供恢復 (R4-01)
                if (rollbackSuccess) {
                    rollbackDir.deleteRecursively()
                }
            } else {
                // 準備階段失敗，目標原檔根本未被修改，直接清除未完成快照，不觸發回滾 (R4-01)
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
                val localFilePath = File(destBase, relPath).absolutePath

                // 重建 thumbnailPath (R3-05, R3-09)
                val rawThumb = if (obj.has("thumbnailPath") && !obj.isNull("thumbnailPath")) {
                    obj.getString("thumbnailPath").replace('\\', '/')
                } else null

                val localThumbPath = if (!rawThumb.isNullOrEmpty() && rawThumb != "null") {
                    when {
                        rawThumb.contains("/thumbnails/") -> File(destBase, "thumbnails/" + rawThumb.substringAfter("/thumbnails/")).absolutePath
                        rawThumb.contains("/states/") -> File(destBase, "states/" + rawThumb.substringAfter("/states/")).absolutePath
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
