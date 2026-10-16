package com.aladdin.geniebox.feature.setup

import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    var statusMessage by remember { mutableStateOf(AppLanguage.text(R.string.ui_choose_a_game_folder_on_your_phone_usb)) }
    var isProcessing by remember { mutableStateOf(false) }
    var scannedGames by remember { mutableStateOf<Int?>(null) }

    // SAF 目錄選擇器
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scannedGames = null
            isProcessing = true
            statusMessage = AppLanguage.text(R.string.ui_checking_the_folder_and_requesting_persistent_access)
            try {
                // 保存持久化 URI 權限
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                coroutineScope.launch {
                    try {
                        val sourceId = uri.lastPathSegment?.replace(":", "_") ?: "usb_source"
                        app.romSourceRepository.registerSource(
                            id = sourceId,
                            displayName = AppLanguage.text(R.string.ui_game_storage_value, sourceId),
                            treeUri = uri.toString()
                        )
                        statusMessage = AppLanguage.text(R.string.ui_building_your_library_please_wait)
                        var scanError: String? = null
                        val count = app.romScanner.scanSource(sourceId, uri) { progress ->
                            if (progress.error != null) scanError = progress.error
                            statusMessage = progress.error ?: AppLanguage.text(
                                R.string.ui_scanning_value_value_games, progress.currentFile, progress.scannedCount
                            )
                        }
                        if (scanError != null) {
                            statusMessage = AppLanguage.text(R.string.ui_scan_failed_value, scanError)
                        } else {
                            scannedGames = count
                            statusMessage = AppLanguage.text(
                                if (count == 0) R.string.ux_scan_empty else R.string.ux_scan_summary, count
                            )
                        }
                    } catch (e: Exception) {
                        statusMessage = AppLanguage.text(R.string.ui_scan_failed_value, e.localizedMessage)
                    } finally {
                        isProcessing = false
                    }
                }
            } catch (e: Exception) {
                isProcessing = false
                statusMessage = AppLanguage.text(R.string.ui_unable_to_get_access_value, e.localizedMessage)
            }
        } else {
            statusMessage = AppLanguage.text(R.string.ui_no_folder_selected_if_your_tv_has_no)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.widthIn(max = 640.dp)
        ) {
            Text(
                text = AppLanguage.text(R.string.ui_bring_your_memories_along),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = AppLanguage.text(R.string.ui_geniebox_your_portable_game_collection_n) +
                       AppLanguage.text(R.string.ui_nes_game_boy_snes_ps1_and_psp_n) +
                       AppLanguage.text(R.string.ui_pick_a_game_keep_playing),
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
                    text = AppLanguage.text(R.string.ui_use_a_folder_on_your_phone_or_external),
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FocusableButton(
                    text = if (isProcessing) AppLanguage.text(R.string.ui_organizing_your_games) else AppLanguage.text(R.string.ui_add_game_folder),
                    isPrimary = true,
                    onClick = {
                        if (!isProcessing) {
                            openDocumentTreeLauncher.launch(null)
                        }
                    }
                )

                FocusableButton(
                    text = AppLanguage.text(if (scannedGames != null) R.string.ux_continue_library else R.string.ui_explore_the_library),
                    enabled = !isProcessing,
                    onClick = onSetupComplete
                )
            }
        }
    }
}
