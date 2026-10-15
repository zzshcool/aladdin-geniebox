package com.aladdin.geniebox.domain.model

data class RomSource(
    val id: String,                         // 穩定來源 ID (例如 volume UUID 或持久 URI 摘要)
    val displayName: String,                // 顯示名稱 (例如 "USB 隨身碟 (SanDisk)" 或 "SD 卡")
    val treeUri: String,                    // SAF 樹狀持久化 URI
    val isConnected: Boolean = true,        // 當前是否在線可存取
    val lastScannedTime: Long = 0L,         // 最後掃描完成時間戳記
    val totalGamesCount: Int = 0            // 該來源涵蓋的遊戲數量
)
