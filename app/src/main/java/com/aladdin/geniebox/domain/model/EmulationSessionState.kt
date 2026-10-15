package com.aladdin.geniebox.domain.model

sealed class EmulationSessionState {
    data object Idle : EmulationSessionState()
    data class Preparing(val message: String, val progress: Float = -1f) : EmulationSessionState()
    data class Loading(val gameTitle: String) : EmulationSessionState()
    data class Running(
        val game: Game,
        val fps: Float = 60.0f,
        val audioUnderruns: Long = 0L,
        val isPaused: Boolean = false
    ) : EmulationSessionState()
    data class Paused(val game: Game) : EmulationSessionState()
    data object Stopping : EmulationSessionState()
    data class Failed(
        val reason: FailureReason,
        val message: String,
        val detailedLog: String? = null
    ) : EmulationSessionState()
}

enum class FailureReason {
    MISSING_CORE,           // 缺少 core .so 產物
    UNSUPPORTED_ABI,        // 設備 ABI 不相容 (如需 arm64-v8a 但設備不支援)
    MISSING_BIOS,           // 缺少 PS1 必要 BIOS
    SOURCE_OFFLINE,         // 外接 USB/SD 來源已拔除
    ROM_CORRUPTED,          // ROM 格式損毀或空白
    LOW_STORAGE_SPACE,      // 內部儲存空間不足
    GRAPHICS_UNSUPPORTED,   // GLES / HW Render 能力不足
    UNKNOWN
}
