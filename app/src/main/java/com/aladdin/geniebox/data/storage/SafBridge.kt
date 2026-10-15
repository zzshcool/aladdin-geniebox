package com.aladdin.geniebox.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.util.zip.ZipInputStream

class SafBridge(private val context: Context, private val storageManager: StorageManager) {

    companion object {
        private const val TAG = "SafBridge"
        private const val MAX_ZIP_UNCOMPRESSED_BYTES = 128 * 1024 * 1024L // 128 MB 單檔上限
        private const val MAX_STAGING_TOTAL_BYTES = 8 * 1024 * 1024 * 1024L // 8 GB 快取上限
    }

    /**
     * 以 gameId 建立獨立子目錄，防止不同來源或相同檔名之 ROM 互相污染。
     * 支援 sourceLastModified 比對 (R2-12)，同大小 ROM 更新能及時失效。
     */
    fun stageFile(
        gameId: String,
        uri: Uri,
        destFileName: String,
        expectedSizeBytes: Long = -1L,
        sourceLastModified: Long = 0L,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): File? {
        enforceStagingCapacityLimit()

        val gameDir = File(storageManager.romStagingDir, gameId).apply { mkdirs() }
        val destFile = File(gameDir, destFileName)
        val metaFile = File(gameDir, "$destFileName.meta")
        destFile.parentFile?.mkdirs()
        metaFile.parentFile?.mkdirs()

        // 檢查快取有效性 (R2-12, R3-08)：若檔案已存在且大小及最後修改時間吻合，直接命中
        if (destFile.exists()) {
            var isValid = true
            if (expectedSizeBytes > 0L && destFile.length() != expectedSizeBytes) {
                isValid = false
            }
            if (sourceLastModified > 0L) {
                if (!metaFile.exists()) {
                    isValid = false
                } else {
                    val cachedTime = metaFile.readText().trim().toLongOrNull() ?: 0L
                    if (cachedTime != sourceLastModified) {
                        isValid = false
                    }
                }
            }
            if (isValid) {
                return destFile
            } else {
                destFile.delete()
                metaFile.delete()
            }
        }

        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val totalBytes = if (expectedSizeBytes > 0L) expectedSizeBytes else pfd.statSize
            pfd.close()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        if (isCancelled()) {
                            outputStream.close()
                            destFile.delete()
                            metaFile.delete()
                            return null
                        }
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read
                        onProgress(bytesCopied, totalBytes)
                    }
                    outputStream.flush()
                }
            }
            if (sourceLastModified > 0L) {
                metaFile.writeText(sourceLastModified.toString())
            }
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed staging file from URI: $uri", e)
            if (destFile.exists()) destFile.delete()
            if (metaFile.exists()) metaFile.delete()
            null
        }
    }

    /**
     * 從樹狀 URI 導航解析相對路徑所屬的父目錄 DocumentFile (R2-04, R3-02, R4-03)
     * 基準規則與 RomScanner 保持完全一致：只要 rootDoc 底下存在 GenieROMs/ 目錄，
     * 掃描基準即為 GenieROMs/，除非傳入的 relativePath 本身已帶有 GenieROMs 前綴。
     */
    fun resolveParentDirDoc(treeUri: Uri, relativePath: String): DocumentFile? {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val cleanPath = relativePath.replace('\\', '/')
            val segments = cleanPath.split('/').filter { it.isNotEmpty() }

            // 基準補償 (R4-03)：與 RomScanner 規則完全一致
            // RomScanner 只要 rootDoc.findFile("GenieROMs") 存在且為目錄，就固定以 GenieROMs 作為 targetRoot。
            val genieRomsDir = rootDoc.findFile("GenieROMs")
            var current = if (genieRomsDir != null && genieRomsDir.isDirectory &&
                (segments.isEmpty() || segments[0] != "GenieROMs")
            ) {
                genieRomsDir
            } else {
                rootDoc
            }

            if (segments.size <= 1) {
                return current
            }

            for (i in 0 until segments.size - 1) {
                val seg = segments[i]
                val next = current.findFile(seg)
                if (next != null && next.isDirectory) {
                    current = next
                } else {
                    return null
                }
            }
            current
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving parent DocumentFile for $relativePath", e)
            null
        }
    }

    /**
     * 支援 PS1 CUE 與所有相依 BIN 軌道一同 stage 到同個 gameId 目錄 (R2-04, R2-12, R3-02, R4-04)
     * 相依 BIN 缺失或複製失敗時明確回報 null，不回傳殘缺光碟入口。
     * 支援相依 BIN 帶有子目錄（例如 tracks/track01.bin）之層級解析與安全驗證。
     */
    fun stageCueWithBinFiles(
        gameId: String,
        cueUri: Uri,
        cueFileName: String,
        parentDirDoc: DocumentFile?,
        sourceLastModified: Long = 0L,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): File? {
        val stagedCue = stageFile(
            gameId = gameId,
            uri = cueUri,
            destFileName = cueFileName,
            sourceLastModified = sourceLastModified,
            onProgress = onProgress,
            isCancelled = isCancelled
        ) ?: return null

        val cueContent = try {
            stagedCue.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading staged cue text", e)
            stagedCue.delete()
            return null
        }

        val referencedBins = parseCueReferencedFiles(cueContent)
        if (referencedBins.isNotEmpty()) {
            if (parentDirDoc == null || !parentDirDoc.isDirectory) {
                Log.e(TAG, "Parent directory missing for CUE referenced BINs: $cueFileName")
                stagedCue.delete()
                return null
            }

            val cueDirPart = if (cueFileName.replace('\\', '/').contains('/')) {
                cueFileName.replace('\\', '/').substringBeforeLast('/')
            } else ""

            for (rawBinName in referencedBins) {
                val cleanBinName = rawBinName.replace('\\', '/').trim()
                if (cleanBinName.contains("..") || cleanBinName.startsWith("/")) {
                    Log.e(TAG, "Path traversal rejected in CUE referenced BIN: $cleanBinName")
                    stagedCue.delete()
                    return null
                }

                val destBinName = if (cueDirPart.isNotEmpty()) "$cueDirPart/$cleanBinName" else cleanBinName
                val (binDoc, _) = resolveDocInHierarchy(parentDirDoc, cleanBinName)
                if (binDoc != null && binDoc.exists()) {
                    val stagedBin = stageFile(
                        gameId = gameId,
                        uri = binDoc.uri,
                        destFileName = destBinName,
                        expectedSizeBytes = binDoc.length(),
                        sourceLastModified = binDoc.lastModified(),
                        isCancelled = isCancelled
                    )
                    if (stagedBin == null) {
                        Log.e(TAG, "Failed staging referenced PS1 BIN track: $cleanBinName")
                        stagedCue.delete()
                        return null
                    }
                } else {
                    Log.e(TAG, "Referenced PS1 BIN track missing from source: $cleanBinName")
                    stagedCue.delete()
                    return null
                }
            }
        }
        return stagedCue
    }

    /**
     * 支援 M3U 多片光碟清單解析與所有光碟 (含其 CUE/BIN) 一同 stage 到同個 gameId 目錄 (R2-05, R3-03)
     * 完整保留 M3U 條目所定義的子目錄結構，缺任一片立即回報失敗並清理暫存。
     */
    fun stageM3uWithDiscs(
        gameId: String,
        m3uUri: Uri,
        m3uFileName: String,
        parentDirDoc: DocumentFile?,
        sourceLastModified: Long = 0L,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): File? {
        val stagedM3u = stageFile(
            gameId = gameId,
            uri = m3uUri,
            destFileName = m3uFileName,
            sourceLastModified = sourceLastModified,
            onProgress = onProgress,
            isCancelled = isCancelled
        ) ?: return null

        val m3uContent = try {
            stagedM3u.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading staged m3u text", e)
            stagedM3u.delete()
            return null
        }

        val entries = parseM3uEntries(m3uContent)
        if (entries.isEmpty()) {
            Log.w(TAG, "M3U playlist is empty: $m3uFileName")
            stagedM3u.delete()
            return null
        }

        if (parentDirDoc == null || !parentDirDoc.isDirectory) {
            Log.e(TAG, "Parent directory missing for M3U discs: $m3uFileName")
            stagedM3u.delete()
            return null
        }

        for (entryName in entries) {
            val cleanEntry = entryName.replace('\\', '/').trim()
            if (cleanEntry.contains("..") || cleanEntry.startsWith("/")) {
                Log.e(TAG, "Path traversal rejected in M3U entry: $cleanEntry")
                stagedM3u.delete()
                return null
            }

            // 依相對目錄向下解析該光碟所在之 DocumentFile 與父 DocumentFile
            val (discDoc, entryParentDoc) = resolveDocInHierarchy(parentDirDoc, cleanEntry)
            if (discDoc == null || !discDoc.exists() || entryParentDoc == null) {
                Log.e(TAG, "Referenced M3U disc entry missing from source: $cleanEntry")
                stagedM3u.delete()
                return null
            }

            val ext = cleanEntry.substringAfterLast('.', "").lowercase()
            val stagedDisc = if (ext == "cue") {
                stageCueWithBinFiles(
                    gameId = gameId,
                    cueUri = discDoc.uri,
                    cueFileName = cleanEntry,
                    parentDirDoc = entryParentDoc,
                    sourceLastModified = discDoc.lastModified(),
                    isCancelled = isCancelled
                )
            } else {
                stageFile(
                    gameId = gameId,
                    uri = discDoc.uri,
                    destFileName = cleanEntry,
                    expectedSizeBytes = discDoc.length(),
                    sourceLastModified = discDoc.lastModified(),
                    isCancelled = isCancelled
                )
            }

            if (stagedDisc == null) {
                Log.e(TAG, "Failed staging M3U disc entry: $cleanEntry")
                stagedM3u.delete()
                return null
            }
        }
        return stagedM3u
    }

    private fun resolveDocInHierarchy(baseDoc: DocumentFile, relativePath: String): Pair<DocumentFile?, DocumentFile?> {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null to null
        var curDir = baseDoc
        for (i in 0 until parts.size - 1) {
            val next = curDir.findFile(parts[i]) ?: return null to null
            if (!next.isDirectory) return null to null
            curDir = next
        }
        val fileDoc = curDir.findFile(parts.last())
        return fileDoc to curDir
    }

    /**
     * 解析 PS1 CUE 內容，提取所有相依的 BIN 檔案名
     */
    fun parseCueReferencedFiles(cueText: String): List<String> {
        val fileNames = mutableListOf<String>()
        val regex = Regex("""FILE\s+"([^"]+)"\s+BINARY""", RegexOption.IGNORE_CASE)
        regex.findAll(cueText).forEach { match ->
            val binName = match.groupValues[1]
            fileNames.add(binName)
        }
        return fileNames
    }

    /**
     * 解析 M3U 播放清單中的多片清單
     */
    fun parseM3uEntries(m3uText: String): List<String> {
        return m3uText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

    /**
     * 安全解壓單一 ROM ZIP 檔案（防 Zip Bomb 與路徑穿越），輸出至指定 gameId 目錄
     */
    fun extractSingleRomFromZip(gameId: String, zipUri: Uri, allowedExtensions: List<String>): File? {
        val targetDir = File(storageManager.romStagingDir, gameId).apply { mkdirs() }
        return try {
            context.contentResolver.openInputStream(zipUri)?.use { rawStream ->
                ZipInputStream(BufferedInputStream(rawStream)).use { zis ->
                    var entry = zis.nextEntry
                    var foundEntryName: String? = null
                    var extractedFile: File? = null

                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        if (!entry.isDirectory && !name.contains("..") && !name.startsWith("/")) {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (allowedExtensions.contains(ext)) {
                                if (foundEntryName != null) {
                                    Log.w(TAG, "Zip contains multiple ROMs, rejecting: $name")
                                    extractedFile?.delete()
                                    return null
                                }
                                foundEntryName = name
                                val out = File(targetDir, File(name).name)
                                var totalBytes = 0L
                                val buffer = ByteArray(32 * 1024)
                                FileOutputStream(out).use { fos ->
                                    var read: Int
                                    while (zis.read(buffer).also { read = it } != -1) {
                                        totalBytes += read
                                        if (totalBytes > MAX_ZIP_UNCOMPRESSED_BYTES) {
                                            Log.e(TAG, "Zip Bomb protection triggered on $name")
                                            fos.close()
                                            out.delete()
                                            return null
                                        }
                                        fos.write(buffer, 0, read)
                                    }
                                    fos.flush()
                                }
                                extractedFile = out
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    extractedFile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting zip: $zipUri", e)
            null
        }
    }

    private fun enforceStagingCapacityLimit() {
        val stagingRoot = storageManager.romStagingDir
        val files = stagingRoot.walkTopDown().filter { it.isFile }.toList()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes > MAX_STAGING_TOTAL_BYTES) {
            // 超過上限，刪除最舊的快取資料夾
            val dirs = stagingRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() }
            if (dirs != null) {
                for (d in dirs) {
                    val sz = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    d.deleteRecursively()
                    totalBytes -= sz
                    if (totalBytes < MAX_STAGING_TOTAL_BYTES * 0.75) break
                }
            }
        }
    }
}
