package com.aladdin.geniebox.feature.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.app.GenieApplication
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = GenieApplication.instance

    var statusMessage by remember { mutableStateOf("請選擇外接 USB 隨身碟或 SD 卡之 ROM 目錄") }
    var isProcessing by remember { mutableStateOf(false) }

    // SAF 目錄選擇器
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            statusMessage = "正在驗證外接目錄並取得永久存取權限…"
            try {
                // 保存持久化 URI 權限
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                coroutineScope.launch {
                    val sourceId = uri.lastPathSegment?.replace(":", "_") ?: "usb_source"
                    app.romSourceRepository.registerSource(
                        id = sourceId,
                        displayName = "外接遊戲儲存 ($sourceId)",
                        treeUri = uri.toString()
                    )

                    statusMessage = "正在建立遊戲庫索引，請稍候…"
                    app.romScanner.scanSource(sourceId, uri) { progress ->
                        statusMessage = "掃描中：${progress.currentFile} (${progress.scannedCount} 款遊戲)"
                    }

                    isProcessing = false
                    onSetupComplete()
                }
            } catch (e: Exception) {
                isProcessing = false
                statusMessage = "取得授權失敗：${e.localizedMessage}"
            }
        } else {
            statusMessage = "未選擇目錄。TV 若未提供系統選擇器，請確認已插入相容的 USB 隨身碟。"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.widthIn(max = 640.dp)
        ) {
            Text(
                text = "歡迎使用阿拉丁 GenieBox",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AladdinGold
            )

            Text(
                text = "專為電視盒與手把設計的開源復古遊戲主控台。\n" +
                       "支援 NES、GB/GBC、GBA、超任、PS1、PSP。\n" +
                       "所有模擬核心在同一 APK 內執行，存檔儲存於內部空間。",
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardDark, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "目錄標準建議：於 USB 根目錄建立「GenieROMs/」並依平台建立 nes/、gba/、sfc/、ps1/ 等資料夾。",
                    fontSize = 13.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = statusMessage,
                fontSize = 15.sp,
                color = if (isProcessing) AladdinGoldLight else TextPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FocusableButton(
                    text = if (isProcessing) "處理中…" else "選擇 ROM 儲存目錄",
                    isPrimary = true,
                    onClick = {
                        if (!isProcessing) {
                            openDocumentTreeLauncher.launch(null)
                        }
                    }
                )

                FocusableButton(
                    text = "稍後再設定 (進入主頁)",
                    onClick = onSetupComplete
                )
            }
        }
    }
}
