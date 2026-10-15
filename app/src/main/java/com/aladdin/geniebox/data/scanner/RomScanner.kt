package com.aladdin.geniebox.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.aladdin.geniebox.data.db.dao.GameDao
import com.aladdin.geniebox.data.db.dao.RomSourceDao
import com.aladdin.geniebox.data.db.entity.GameEntity
import com.aladdin.geniebox.domain.model.EntryType
import com.aladdin.geniebox.domain.model.PlatformType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

class RomScanner(
    private val context: Context,
    private val gameDao: GameDao,
    private val romSourceDao: RomSourceDao
) {
    companion object {
        private const val TAG = "RomScanner"
        private val COVER_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }

    data class ScanProgress(
        val sourceId: String,
        val scannedCount: Int,
        val currentFile: String,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    suspend fun scanSource(
        sourceId: String,
        treeUri: Uri,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc == null || !rootDoc.canRead()) {
            onProgress(ScanProgress(sourceId, 0, "", isFinished = true, error = "無法讀取外接儲存目錄"))
            return@withContext 0
        }

        val discoveredGames = mutableListOf<GameEntity>()
        var count = 0

        try {
            // 優先檢查是否有標準 GenieROMs 目錄
            val genieRomsDir = rootDoc.findFile("GenieROMs")
            val targetRoot = if (genieRomsDir != null && genieRomsDir.isDirectory) genieRomsDir else rootDoc

            // 進行遞迴掃描
            scanDirectory(
                dir = targetRoot,
                sourceId = sourceId,
                currentRelPath = "",
                outGames = discoveredGames,
                onFileScanned = { fileName ->
                    count++
                    if (count % 10 == 0) {
                        onProgress(ScanProgress(sourceId, count, fileName, isFinished = false))
                    }
                }
            )

            // 批次增量寫入資料庫
            if (discoveredGames.isNotEmpty()) {
                // 每批 100 筆寫入，避免 SQLite 參數上限
                discoveredGames.chunked(100).forEach { batch ->
                    gameDao.insertOrUpdateGames(batch)
                }
            }

            // 更新來源掃描時間
            romSourceDao.updateLastScannedTime(sourceId, System.currentTimeMillis())
            romSourceDao.updateConnectionStatus(sourceId, true)

            onProgress(ScanProgress(sourceId, discoveredGames.size, "掃描完成", isFinished = true))
            discoveredGames.size
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed for source $sourceId", e)
            onProgress(ScanProgress(sourceId, count, "", isFinished = true, error = e.localizedMessage))
            0
        }
    }

    private suspend fun scanDirectory(
        dir: DocumentFile,
        sourceId: String,
        currentRelPath: String,
        outGames: MutableList<GameEntity>,
        onFileScanned: suspend (String) -> Unit
    ) {
        val files = dir.listFiles()
        val cueReferencedBins = mutableSetOf<String>()

        // 第一階段：先收集目錄內所有 CUE 引用的 BIN 檔與封面圖
        val coverMap = mutableMapOf<String, String>() // baseName -> imageUri

        for (file in files) {
            val name = file.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()

            if (COVER_EXTENSIONS.contains(ext)) {
                val base = name.substringBeforeLast('.')
                coverMap[base.lowercase()] = file.uri.toString()
            } else if (ext == "cue") {
                // 讀取 CUE 內容，記錄關聯 BIN
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                            val match = Regex("""FILE\s+"([^"]+)"\s+BINARY""", RegexOption.IGNORE_CASE).find(line)
                            if (match != null) {
                                cueReferencedBins.add(match.groupValues[1].lowercase())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing cue file $name", e)
                }
            }
        }

        // 第二階段：處理遊戲檔案與子目錄
        for (file in files) {
            val name = file.name ?: continue
            if (file.isDirectory) {
                val subPath = if (currentRelPath.isEmpty()) name else "$currentRelPath/$name"
                scanDirectory(file, sourceId, subPath, outGames, onFileScanned)
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                val base = name.substringBeforeLast('.')

                // 如果這個檔案是被 CUE 參照的 BIN 軌道，則不作為獨立遊戲項目加入
                if (ext == "bin" && cueReferencedBins.contains(name.lowercase())) {
                    continue
                }

                // 辨識平台
                val platform = detectPlatform(currentRelPath, ext)
                if (platform != null) {
                    onFileScanned(name)

                    val relFilePath = if (currentRelPath.isEmpty()) name else "$currentRelPath/$name"
                    val stableId = generateStableId(sourceId, relFilePath)
                    val cover = coverMap[base.lowercase()]
                    val (pinyin, zhuyin) = PinyinZhuYinIndex.generateSearchIndex(base)

                    val entryType = when (ext) {
                        "zip" -> EntryType.ZIP
                        "cue" -> EntryType.CUE_BIN
                        "m3u" -> EntryType.M3U_DISC_SET
                        else -> EntryType.FILE
                    }

                    outGames.add(
                        GameEntity(
                            id = stableId,
                            title = base,
                            platform = platform.id,
                            sourceId = sourceId,
                            relativePath = relFilePath,
                            documentUri = file.uri.toString(),
                            entryType = entryType.name,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            coverUri = cover,
                            pinyinIndex = pinyin,
                            zhuyinIndex = zhuyin,
                            isAvailable = true
                        )
                    )
                }
            }
        }
    }

    private fun detectPlatform(dirPath: String, ext: String): PlatformType? {
        val cleanPath = dirPath.replace('\\', '/').lowercase()
        val segments = cleanPath.split('/').filter { it.isNotEmpty() }.toSet()

        // 依路徑 segment 精確判定平台 (R2-11，避免 sfc/gba 被誤判為 fc/gb)
        val platformFromDir = when {
            segments.contains("sfc") || segments.contains("snes") -> PlatformType.SFC
            segments.contains("gba") -> PlatformType.GBA
            segments.contains("gbc") || segments.contains("gb") -> PlatformType.GB
            segments.contains("nes") || segments.contains("fc") -> PlatformType.NES
            segments.contains("ps1") || segments.contains("psx") -> PlatformType.PS1
            segments.contains("psp") -> PlatformType.PSP
            else -> null
        }

        if (platformFromDir != null) {
            if (ext == "zip") {
                // ZIP 僅支援 NES, GB, GBA, SFC
                if (platformFromDir == PlatformType.NES ||
                    platformFromDir == PlatformType.GB ||
                    platformFromDir == PlatformType.GBA ||
                    platformFromDir == PlatformType.SFC) {
                    return platformFromDir
                }
            } else if (platformFromDir.supportedExtensions.contains(ext)) {
                return platformFromDir
            }
        }

        // 若不是標準資料夾，且為 zip，不任意盲目猜測平台 (規格第 3 節)
        if (ext == "zip") return null

        // 從固有副檔名推斷
        return PlatformType.fromExtension(ext)
    }

    private fun generateStableId(sourceId: String, relativePath: String): String {
        val input = "$sourceId:$relativePath"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
