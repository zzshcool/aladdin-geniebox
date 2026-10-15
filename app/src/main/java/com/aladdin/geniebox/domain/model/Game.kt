package com.aladdin.geniebox.domain.model

data class Game(
    val id: String,                         // 穩定唯一的 Game ID (e.g. SHA256(sourceId:relativePath))
    val title: String,                      // 遊戲標題
    val platform: PlatformType,             // 平台 (NES, GB, GBA, SFC, PS1, PSP)
    val sourceId: String,                   // 來源 ID
    val relativePath: String,               // 來源根目錄之相對路徑
    val documentUri: String,                // Android SAF Document URI
    val entryType: EntryType = EntryType.FILE, // 單一檔案, ZIP, CUE, 或 M3U 播放清單
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val coverUri: String? = null,           // 封面圖片 URI 或本機暫存路徑
    val pinyinIndex: String = "",           // 拼音全拼與首字母索引
    val zhuyinIndex: String = "",           // 注音簡拼索引
    val isAvailable: Boolean = true,        // 外接來源連線時為 true，拔除後為 false (顯示離線)
    val isFavorite: Boolean = false,
    val lastPlayedTime: Long? = null
)

enum class EntryType {
    FILE,
    ZIP,
    CUE_BIN,
    M3U_DISC_SET
}
