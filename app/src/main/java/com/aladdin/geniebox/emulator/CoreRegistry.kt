package com.aladdin.geniebox.emulator

import android.content.Context
import android.os.Build
import com.aladdin.geniebox.domain.model.CoreDescriptor
import com.aladdin.geniebox.domain.model.PlatformType
import java.io.File

class CoreRegistry(private val context: Context) {

    companion object {
        const val PS1_BIOS_SIZE_BYTES = 512 * 1024
    }

    val cores: Map<PlatformType, CoreDescriptor> = mapOf(
        PlatformType.NES to CoreDescriptor(
            coreId = "fceumm",
            platform = PlatformType.NES,
            displayName = "FCEUmm (NES/FC)",
            version = "commit-ac0d00a",
            soFileName = "libfceumm_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = false,
            upstreamUrl = "https://github.com/libretro/libretro-fceumm",
            gitCommit = "ac0d00a7fc01166f84c7bac01d1084ebfd2b865e",
            license = "GPL-2.0"
        ),
        PlatformType.GB to CoreDescriptor(
            coreId = "gambatte",
            platform = PlatformType.GB,
            displayName = "Gambatte (GB/GBC)",
            version = "commit-d9d6cd0",
            soFileName = "libgambatte_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = false,
            upstreamUrl = "https://github.com/libretro/gambatte-libretro",
            gitCommit = "d9d6cd06382d1ced30de34d56d3609452323dab1",
            license = "GPL-2.0"
        ),
        PlatformType.GBA to CoreDescriptor(
            coreId = "mgba",
            platform = PlatformType.GBA,
            displayName = "mGBA (GBA)",
            version = "0.10.3",
            soFileName = "libmgba_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = false,
            upstreamUrl = "https://github.com/mgba-emu/mgba",
            gitCommit = "1c61b54208ca6266129d0f2394c04bd8c44f98c5",
            license = "MPL-2.0"
        ),
        PlatformType.SFC to CoreDescriptor(
            coreId = "snes9x",
            platform = PlatformType.SFC,
            displayName = "Snes9x (SFC/SNES)",
            version = "1.63-1bcc369",
            soFileName = "libsnes9x_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = false,
            upstreamUrl = "https://github.com/snes9xgit/snes9x",
            gitCommit = "1bcc369e89f08243e0a462882fb1f3e42e51de3a",
            license = "Custom Snes9x License"
        ),
        PlatformType.PS1 to CoreDescriptor(
            coreId = "pcsx_rearmed",
            platform = PlatformType.PS1,
            displayName = "PCSX ReARMed (PS1)",
            version = "r23-c881679",
            soFileName = "libpcsx_rearmed_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = true,
            biosFiles = listOf("scph5501.bin", "scph5500.bin", "scph5502.bin"),
            supportsDiskSwap = true,
            upstreamUrl = "https://github.com/libretro/pcsx_rearmed",
            gitCommit = "c8816799b50388e61cfe237fe2cdbb7d8175f20a",
            license = "GPL-2.0"
        ),
        PlatformType.PSP to CoreDescriptor(
            coreId = "ppsspp",
            platform = PlatformType.PSP,
            displayName = "PPSSPP Libretro (PSP)",
            version = "v1.17.1",
            soFileName = "libppsspp_libretro_android.so",
            supportedAbis = listOf("arm64-v8a"),
            requiresBios = false,
            upstreamUrl = "https://github.com/hrydgard/ppsspp",
            gitCommit = "d479b74ed9c3e321bc3735da29bc125a2ac3b9b2",
            license = "GPL-2.0"
        )
    )

    /**
     * 檢查當前系統架構是否相容 arm64-v8a
     */
    fun isAbiSupported(): Boolean {
        val supportedAbis = Build.SUPPORTED_ABIS
        return supportedAbis.contains("arm64-v8a")
    }

    /**
     * 取得核心動態函式庫檔案路徑，依序搜尋 nativeLibraryDir、App 私有目錄與 APK 直接抽出備援
     */
    fun getCoreLibraryFile(platform: PlatformType): File? {
        val desc = cores[platform] ?: return null
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val fileInLib = File(libDir, desc.soFileName)
        if (fileInLib.exists()) return fileInLib

        // 亦可相容放置於 files/cores/ 目錄 (供本機除錯或特定封裝)
        val fileInApp = File(context.filesDir, "cores/${desc.soFileName}")
        if (fileInApp.exists()) return fileInApp

        // 備援：若系統未解壓 (如某些客製 ROM)，直接自 APK 封裝中解壓至 files/cores/
        val extracted = extractCoreFromApk(desc.soFileName, fileInApp)
        if (extracted != null && extracted.exists()) return extracted

        return null
    }

    private fun extractCoreFromApk(soFileName: String, targetFile: File): File? {
        return try {
            val apkPath = context.applicationInfo.sourceDir ?: return null
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return null

            java.util.zip.ZipFile(apkFile).use { zip ->
                val primaryEntry = zip.getEntry("lib/arm64-v8a/$soFileName")
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("/$soFileName") }
                    ?: return null

                targetFile.parentFile?.mkdirs()
                val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                zip.getInputStream(primaryEntry).use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmpFile.renameTo(targetFile) || (targetFile.delete() && tmpFile.renameTo(targetFile))) {
                    targetFile
                } else {
                    tmpFile
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CoreRegistry", "Failed to extract $soFileName from APK", e)
            null
        }
    }

    /**
     * 檢查 PS1 是否具備合法真實 BIOS
     */
    fun checkPs1BiosAvailable(): Boolean {
        val systemDir = File(context.filesDir, "system")
        if (!systemDir.exists()) return false
        val files = systemDir.listFiles() ?: return false
        val ps1Desc = cores[PlatformType.PS1] ?: return false
        return ps1Desc.biosFiles.any { req ->
            files.any { it.isFile && it.canRead() && it.length() == PS1_BIOS_SIZE_BYTES.toLong() && it.name.equals(req, ignoreCase = true) }
        }
    }
}
