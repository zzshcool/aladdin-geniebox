package com.aladdin.geniebox.domain.model

data class SaveMetadata(
    val id: String,                         // 存檔記錄主鍵
    val gameId: String,                     // 所屬遊戲 ID
    val coreId: String,                     // 存檔時的核心識別碼 (如 fceumm, mgba)
    val coreVersion: String,                // 核心版本 (防版本格式不相容)
    val formatVersion: Int = 1,             // GenieBox 存檔協定版本
    val saveType: SaveType,                 // 原生 SRAM / 手動即時存檔 / 自動續玩
    val slotIndex: Int = 0,                 // 槽位編號 (手動 1..5, 自動 0)
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String,                   // 內部儲存持久路徑 (files/saves/ 或 files/states/)
    val thumbnailPath: String? = null,      // 即時存檔截圖縮圖路徑
    val sizeBytes: Long = 0L
)

enum class SaveType {
    NATIVE_SRAM,    // 遊戲內建原生電池記憶卡 (SRAM/EEPROM/Flash/MemoryCard)
    MANUAL_STATE,   // 手動即時存檔 (Slot 1-5)
    AUTO_RESUME     // 退出時自動暫存 (自動續玩槽，不覆蓋手動槽)
}
