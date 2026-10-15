package com.aladdin.geniebox.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.app.GenieApplication
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val app = GenieApplication.instance
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var actionMessage by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        app.storageManager.exportBackup(os)
                    } ?: false
                } catch (e: Exception) {
                    false
                }
                actionMessage = if (success) "存檔與即時進度已成功匯出至指定儲存空間！" else "匯出存檔失敗，請確認儲存空間權限。"
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = try {
                    context.contentResolver.openInputStream(uri)?.use { `is` ->
                        app.storageManager.importBackup(`is`)
                    } ?: false
                } catch (e: Exception) {
                    false
                }
                actionMessage = if (success) "存檔還原成功！" else "匯入存檔失敗，備份檔可能損毀或格式不合。"
            }
        }
    }

    val addSourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // 若系統無法取得持久化權限，仍嘗試註冊當次來源
                }
                val sourceId = uri.lastPathSegment?.replace(":", "_") ?: "rom_source"
                val displayName = "遊戲儲存 ($sourceId)"
                app.romSourceRepository.registerSource(sourceId, displayName, uri.toString())
                actionMessage = "已成功加入遊戲目錄：$displayName，正在背景掃描中…"
                isScanning = true
                var scanError: String? = null
                try {
                    val count = app.romScanner.scanSource(sourceId, uri) { prog ->
                        if (prog.isFinished && prog.error != null) {
                            scanError = prog.error
                        }
                        actionMessage = prog.error ?: "掃描中：${prog.scannedCount} 款遊戲"
                    }
                    actionMessage = scanError?.let { "掃描失敗：$it" }
                        ?: "掃描完成，共 $count 款遊戲！"
                } catch (e: Exception) {
                    actionMessage = "掃描失敗：${e.message}"
                } finally {
                    isScanning = false
                }
            }
        }
    }

    val playerProfiles by app.inputManager.playerProfiles.collectAsState()
    val sources by app.romSourceRepository.getAllSources().collectAsState(initial = emptyList())

    val currentAspect by app.settingsRepository.aspectRatioMode.collectAsState(initial = 0)
    val currentBilinear by app.settingsRepository.bilinearFilter.collectAsState(initial = false)
    val showFpsHud by app.settingsRepository.showFpsHud.collectAsState(initial = false)
    val virtualGamepadMode by app.settingsRepository.virtualGamepadMode.collectAsState(initial = 0)
    val isTvDevice = remember { app.settingsRepository.isTvDevice() }

    val ps1BiosReady = remember { app.coreRegistry.checkPs1BiosAvailable() }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "設定與系統管理",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AladdinGold
                )
                FocusableButton(
                    text = "返回遊戲庫",
                    onClick = onNavigateBack
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (actionMessage.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardDarkFocused, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = actionMessage, color = AladdinGoldLight, fontSize = 14.sp)
                    }
                }
            }

            // 1. 手把與多玩家 (P1–P4) 管理
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("控制器指派 (P1–P4)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            "支援 USB Hub 四手把、四藍牙手把或混用。按手把任意鍵可自動綁定至空槽位。",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            playerProfiles.forEach { p ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (p.isConnected) CardDarkFocused else BgDark, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("玩家 ${p.playerId}", fontWeight = FontWeight.Bold, color = if (p.isConnected) AccentGreen else TextTertiary)
                                        Text(p.deviceName, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                                        Text(if (p.isConnected) "已連線" else "未偵測到手把", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        FocusableButton(
                            text = "重新偵測並整理控制器",
                            onClick = {
                                app.inputManager.refreshConnectedDevices()
                                actionMessage = "已重新掃描系統輸入裝置。"
                            }
                        )
                    }
                }
            }

            // 2. 遊戲 ROM 目錄來源管理 (支援手機內部儲存 / USB 隨身碟)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("遊戲 ROM 目錄來源", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            "支援 USB 隨身碟、SD 卡以及手機本機儲存空間。請選取包含各主機遊戲的根目錄（例如 GenieROMs/ 或含有 nes、gba、gb、sfc、ps1、psp 之資料夾）。",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        FocusableButton(
                            text = "+ 新增遊戲資料夾 (手機內部儲存 / USB 隨身碟)",
                            onClick = {
                                addSourceLauncher.launch(null)
                            }
                        )

                        if (sources.isEmpty()) {
                            Text("目前尚未加入任何來源目錄。請點擊上方按鈕授權選取遊戲資料夾。", color = TextTertiary, fontSize = 13.sp)
                        } else {
                            sources.forEach { src ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardDarkFocused, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(src.displayName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(if (src.isConnected) "狀態：在線" else "狀態：已拔除 (離線)", fontSize = 12.sp, color = if (src.isConnected) AccentGreen else AccentRed)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FocusableButton(
                                            text = if (isScanning) "掃描中…" else "重新掃描",
                                            onClick = {
                                                if (!isScanning) {
                                                    isScanning = true
                                                    coroutineScope.launch {
                                                        actionMessage = "開始掃描 ${src.displayName}…"
                                                        var scanError: String? = null
                                                        try {
                                                            val count = app.romScanner.scanSource(src.id, Uri.parse(src.treeUri)) { prog ->
                                                                if (prog.isFinished && prog.error != null) {
                                                                    scanError = prog.error
                                                                }
                                                                actionMessage = prog.error ?: "已找到 ${prog.scannedCount} 款遊戲"
                                                            }
                                                            actionMessage = scanError?.let { "掃描失敗：$it" }
                                                                ?: "掃描完成，共 $count 款遊戲！"
                                                        } catch (e: Exception) {
                                                            actionMessage = "掃描失敗：${e.message}"
                                                        } finally {
                                                            isScanning = false
                                                        }
                                                    }
                                                }
                                            }
                                        )

                                        FocusableButton(
                                            text = "移除來源",
                                            onClick = {
                                                coroutineScope.launch {
                                                    app.romSourceRepository.removeSource(src.id)
                                                    actionMessage = "已移除來源 ${src.displayName}。"
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. PS1 BIOS 與核心支援狀態
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BIOS 與模擬核心狀態", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PS1 BIOS：", color = TextSecondary, fontSize = 14.sp)
                            Text(
                                text = if (ps1BiosReady) "已就緒 (使用真實 BIOS)" else "缺少真實 BIOS (目前降級使用 HLE 模擬)",
                                color = if (ps1BiosReady) AccentGreen else AccentRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            "真實 BIOS 請放置於內部 system/ 資料夾 (支援 scph5501.bin, scph5500.bin, scph5502.bin)。",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            }

            // 4. 影音與視訊渲染設定
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("視訊與彩現設定", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("畫面比例：", color = TextSecondary, fontSize = 14.sp)
                            val modes = listOf("原始比例 (預設)", "整數縮放", "全螢幕拉伸")
                            modes.forEachIndexed { idx, label ->
                                FocusableButton(
                                    text = label,
                                    isPrimary = (currentAspect == idx),
                                    onClick = {
                                        coroutineScope.launch {
                                            app.settingsRepository.setAspectRatioMode(idx)
                                            app.emulatorSession.setAspectRatioMode(idx)
                                        }
                                    }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("平滑濾鏡：", color = TextSecondary, fontSize = 14.sp)
                            FocusableButton(
                                text = if (currentBilinear) "雙線性平滑 (開)" else "最近鄰原始像素 (關)",
                                isPrimary = currentBilinear,
                                onClick = {
                                    coroutineScope.launch {
                                        val next = !currentBilinear
                                        app.settingsRepository.setBilinearFilter(next)
                                        app.emulatorSession.setFilterMode(next)
                                    }
                                }
                            )

                            FocusableButton(
                                text = if (showFpsHud) "FPS HUD (顯示)" else "FPS HUD (隱藏)",
                                isPrimary = showFpsHud,
                                onClick = {
                                    coroutineScope.launch {
                                        app.settingsRepository.setShowFpsHud(!showFpsHud)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 5. 觸控虛擬手把與裝置模式
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("觸控虛擬手把與裝置適配", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            "目前系統偵測模式：${if (isTvDevice) "電視盒 / Android TV (大螢幕遙控)" else "手機 / 平板 (支援觸控螢幕)"}",
                            fontSize = 13.sp,
                            color = if (isTvDevice) AladdinGold else AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("虛擬手把顯示：", color = TextSecondary, fontSize = 14.sp)
                            val gamepadModes = listOf("自動 (TV隱藏/手機智慧)", "強制顯示", "強制隱藏")
                            gamepadModes.forEachIndexed { idx, label ->
                                FocusableButton(
                                    text = label,
                                    isPrimary = (virtualGamepadMode == idx),
                                    onClick = {
                                        coroutineScope.launch {
                                            app.settingsRepository.setVirtualGamepadMode(idx)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 6. 存檔與備份管理
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("存檔與資料安全", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            "所有遊戲原生 SRAM 與即時存檔皆保存在內部安全存儲空間，不受隨身碟插拔影響。",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FocusableButton(
                                text = "匯出存檔備份 (SAF)",
                                onClick = {
                                    val defaultName = "genie_backup_${System.currentTimeMillis()}.zip"
                                    exportBackupLauncher.launch(defaultName)
                                }
                            )

                            FocusableButton(
                                text = "匯入還原備份 (Zip)",
                                onClick = {
                                    importBackupLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                }
                            )

                            FocusableButton(
                                text = "清理 ROM 快取",
                                onClick = {
                                    val bytes = app.storageManager.clearRomStagingCache()
                                    actionMessage = "已釋放快取空間：${bytes / (1024 * 1024)} MB。"
                                }
                            )
                        }
                    }
                }
            }

            // 6. 關於與版權宣告
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("關於 阿拉丁 GenieBox", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text("主專案授權：GNU General Public License v3.0 (GPL-3.0)", fontSize = 13.sp, color = TextPrimary)
                        Text("支援核心：FCEUmm (GPL-2.0)、Gambatte (GPL-2.0)、mGBA (MPL-2.0)、Snes9x (Non-commercial)、PCSX ReARMed (GPL-2.0)、PPSSPP (GPL-2.0)", fontSize = 12.sp, color = TextTertiary)
                        Text("本軟體離線執行，無網路遙測、無廣告，不內建任何未授權商業 ROM 或 BIOS。", fontSize = 12.sp, color = TextTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
