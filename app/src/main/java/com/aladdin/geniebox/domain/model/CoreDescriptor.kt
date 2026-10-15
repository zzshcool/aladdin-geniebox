package com.aladdin.geniebox.domain.model

data class CoreDescriptor(
    val coreId: String,                     // 核心 ID (fceumm, gambatte, mgba, snes9x, pcsx_rearmed, ppsspp)
    val platform: PlatformType,
    val displayName: String,
    val version: String,                    // 固定鎖定版本
    val soFileName: String,                 // 動態函式庫檔案名稱 (例如 libfceumm_libretro_android.so)
    val supportedAbis: List<String> = listOf("arm64-v8a"),
    val requiresBios: Boolean = false,
    val biosFiles: List<String> = emptyList(), // 例如 listOf("scph5501.bin", "scph5500.bin", "scph5502.bin")
    val supportsSaveState: Boolean = true,
    val supportsDiskSwap: Boolean = false,  // PS1 支援換片
    val upstreamUrl: String = "",
    val gitCommit: String = "",
    val license: String = "GPL-2.0"
)
