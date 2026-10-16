package com.aladdin.geniebox.domain.model

enum class PlatformType(
    val id: String,
    val primaryExtension: String,
    val supportedExtensions: List<String>,
    val defaultCoreId: String,
    val supportsMultitap4: Boolean,
    val requiresBios: Boolean
) {
    NES(
        id = "nes",
        primaryExtension = ".nes",
        supportedExtensions = listOf("nes", "fds", "unf", "unif", "zip"),
        defaultCoreId = "fceumm",
        supportsMultitap4 = true,
        requiresBios = false
    ),
    GB(
        id = "gb",
        primaryExtension = ".gb",
        supportedExtensions = listOf("gb", "gbc", "dmg", "zip"),
        defaultCoreId = "gambatte",
        supportsMultitap4 = false, // GB/GBC 通信線第一版不支援四人
        requiresBios = false
    ),
    GBA(
        id = "gba",
        primaryExtension = ".gba",
        supportedExtensions = listOf("gba", "agb", "bin", "zip"),
        defaultCoreId = "mgba",
        supportsMultitap4 = false, // GBA 通信線第一版不支援四人
        requiresBios = false
    ),
    SFC(
        id = "sfc",
        primaryExtension = ".sfc",
        supportedExtensions = listOf("sfc", "smc", "fig", "zip"),
        defaultCoreId = "snes9x",
        supportsMultitap4 = true,
        requiresBios = false
    ),
    PS1(
        id = "ps1",
        primaryExtension = ".cue",
        supportedExtensions = listOf("cue", "chd", "m3u", "pbp", "bin"),
        defaultCoreId = "pcsx_rearmed",
        supportsMultitap4 = true,
        requiresBios = true // 建議使用真實 PS1 BIOS (如 scph5501.bin)
    ),
    PSP(
        id = "psp",
        primaryExtension = ".iso",
        supportedExtensions = listOf("iso", "cso", "pbp"),
        defaultCoreId = "ppsspp",
        supportsMultitap4 = false, // PSP 無線對戰不宣稱四人
        requiresBios = false
    );

    companion object {
        fun fromExtension(ext: String): PlatformType? {
            val clean = ext.removePrefix(".").lowercase()
            return entries.firstOrNull { p -> p.supportedExtensions.contains(clean) }
        }

        fun fromId(id: String): PlatformType? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}
