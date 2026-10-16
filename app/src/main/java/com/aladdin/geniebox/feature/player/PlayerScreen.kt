package com.aladdin.geniebox.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aladdin.geniebox.app.GenieApplication
import com.aladdin.geniebox.domain.model.EmulationSessionState
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.component.VirtualGamepadOverlay
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    game: Game,
    onExitGame: () -> Unit
) {
    val app = GenieApplication.instance
    val coroutineScope = rememberCoroutineScope()

    val sessionState by app.emulatorSession.sessionState.collectAsState()
    val showFpsHud by app.settingsRepository.showFpsHud.collectAsState(initial = false)
    val touchOpacity by app.settingsRepository.touchOpacity.collectAsState(initial = 0.65f)
    val touchScale by app.settingsRepository.touchScale.collectAsState(initial = 1.0f)
    val touchVibration by app.settingsRepository.touchVibration.collectAsState(initial = true)

    val playerProfiles by app.inputManager.playerProfiles.collectAsState()
    val hasPhysicalGamepad = remember(playerProfiles) {
        playerProfiles.any { it.isConnected }
    }
    val virtualGamepadMode by app.settingsRepository.virtualGamepadMode.collectAsState(initial = 0)
    val isTvDevice = remember { app.settingsRepository.isTvDevice() }

    var showPauseMenu by remember { mutableStateOf(false) }
    var selectedSaveSlot by remember { mutableIntStateOf(1) }
    var pauseStatusText by remember { mutableStateOf("") }
    var totalDiscs by remember { mutableIntStateOf(0) }
    var currentDiscIndex by remember { mutableIntStateOf(0) }

    val shouldShowVirtualGamepad = remember(virtualGamepadMode, hasPhysicalGamepad, showPauseMenu, isTvDevice) {
        if (showPauseMenu) return@remember false
        when (virtualGamepadMode) {
            1 -> true // 強制顯示 (手機外接手把仍可觸控)
            2 -> false // 強制隱藏
            else -> { // 自動：TV 預設隱藏；手機在未接手把時顯示
                if (isTvDevice) false else !hasPhysicalGamepad
            }
        }
    }

    LaunchedEffect(showPauseMenu) {
        if (showPauseMenu) {
            totalDiscs = app.emulatorSession.getDiskCount()
        }
    }

    // 啟動遊戲
    LaunchedEffect(game) {
        app.emulatorSession.startGame(game, autoLoadResumeState = true)
    }

    // 返回鍵攔截：優先開啟暫停選單，避免直接退出
    BackHandler {
        if (showPauseMenu) {
            showPauseMenu = false
            app.inputManager.setMenuIntercept(false)
            app.emulatorSession.resume()
        } else {
            showPauseMenu = true
            app.inputManager.setMenuIntercept(true)
            app.emulatorSession.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 原生 SurfaceView 彩現視窗
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            app.emulatorSession.setSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            app.emulatorSession.setSurface(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            app.emulatorSession.surfaceDestroyed()
                        }
                    })
                }
            }
        )

        // 手機虛擬手把覆蓋 (依設備模式、外接手把狀態與使用者偏好動態顯示)
        if (shouldShowVirtualGamepad) {
            VirtualGamepadOverlay(
                platform = game.platform,
                adapter = app.inputManager,
                opacity = touchOpacity,
                scale = touchScale,
                vibrationEnabled = touchVibration
            )
        }

        if (!isTvDevice && !showPauseMenu && sessionState is EmulationSessionState.Running) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(48.dp),
                onClick = {
                    showPauseMenu = true
                    app.inputManager.setMenuIntercept(true)
                    app.emulatorSession.pause()
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "暫停，開啟返回選單",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 浮動診斷 HUD (FPS、音訊狀態)
        if (showFpsHud && sessionState is EmulationSessionState.Running) {
            val runState = sessionState as EmulationSessionState.Running
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "FPS: ${"%.1f".format(runState.fps)} | Underruns: ${runState.audioUnderruns}",
                    fontSize = 12.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 載入 / 準備中狀態
        when (val state = sessionState) {
            is EmulationSessionState.Preparing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AladdinGold)
                        Text(text = state.message, fontSize = 16.sp, color = TextPrimary)
                    }
                }
            }
            is EmulationSessionState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AladdinGold)
                        Text(text = "正在啟動 ${state.gameTitle}…", fontSize = 16.sp, color = TextPrimary)
                    }
                }
            }
            is EmulationSessionState.Failed -> {
                // 錯誤不可用狀態展示
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "無法執行遊戲",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                        Text(
                            text = state.message,
                            fontSize = 15.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FocusableButton(
                            text = "返回遊戲庫",
                            isPrimary = true,
                            onClick = {
                                coroutineScope.launch {
                                    app.emulatorSession.stopGame()
                                    onExitGame()
                                }
                            }
                        )
                    }
                }
            }
            else -> {}
        }

        // 暫停選單 (Pause Menu)
        if (showPauseMenu) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .heightIn(max = maxHeight)
                        .padding(vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .background(CardDark, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "遊戲暫停 - ${game.title}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AladdinGold
                    )

                    if (pauseStatusText.isNotEmpty()) {
                        Text(
                            text = pauseStatusText,
                            fontSize = 13.sp,
                            color = AladdinGoldLight
                        )
                    }

                    // 離開遊戲
                    FocusableButton(
                        text = "儲存並返回遊戲庫",
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                val res = app.emulatorSession.stopGame(forceDiscard = false)
                                if (res.success) {
                                    app.inputManager.setMenuIntercept(false)
                                    onExitGame()
                                } else {
                                    pauseStatusText = "存檔失敗：${res.errorMessage}。可選擇重試或強制退出。"
                                }
                            }
                        }
                    )

                    // 存檔槽位選擇 (1-5)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("存檔槽位：", fontSize = 14.sp, color = TextSecondary)
                        (1..5).forEach { slot ->
                            val isSel = (slot == selectedSaveSlot)
                            FocusableButton(
                                text = "槽 $slot",
                                isPrimary = isSel,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(36.dp),
                                onClick = { selectedSaveSlot = slot }
                            )
                        }
                    }

                    // PS1 / 多光碟換片介面 (R2-05)
                    if (totalDiscs > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("更換光碟：", fontSize = 14.sp, color = TextSecondary)
                            (0 until totalDiscs).forEach { discIdx ->
                                FocusableButton(
                                    text = "第 ${discIdx + 1} 片",
                                    isPrimary = (discIdx == currentDiscIndex),
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .height(36.dp),
                                    onClick = {
                                        coroutineScope.launch {
                                            val ok = app.emulatorSession.setDiskIndex(discIdx)
                                            if (ok) {
                                                currentDiscIndex = discIdx
                                                pauseStatusText = "已更換至第 ${discIdx + 1} 片光碟"
                                            } else {
                                                pauseStatusText = "更換光碟失敗"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    FocusableButton(
                        text = "繼續遊戲",
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showPauseMenu = false
                            app.inputManager.setMenuIntercept(false)
                            app.emulatorSession.resume()
                        }
                    )

                    FocusableButton(
                        text = "儲存進度 (Slot $selectedSaveSlot)",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                val ok = app.emulatorSession.saveManualState(selectedSaveSlot)
                                pauseStatusText = if (ok) "已成功儲存至槽位 $selectedSaveSlot" else "存檔失敗 (空間不足或核心未支援)"
                            }
                        }
                    )

                    FocusableButton(
                        text = "載入進度 (Slot $selectedSaveSlot)",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                val ok = app.emulatorSession.loadManualState(selectedSaveSlot)
                                if (ok) {
                                    pauseStatusText = "已成功載入槽位 $selectedSaveSlot"
                                    showPauseMenu = false
                                    app.inputManager.setMenuIntercept(false)
                                    app.emulatorSession.resume()
                                } else {
                                    pauseStatusText = "載入失敗 (該槽位無存檔或版本不相容)"
                                }
                            }
                        }
                    )

                    FocusableButton(
                        text = "重新開始 (Reset)",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            app.emulatorSession.reset()
                            showPauseMenu = false
                            app.inputManager.setMenuIntercept(false)
                            app.emulatorSession.resume()
                        }
                    )

                    if (pauseStatusText.contains("存檔失敗")) {
                        FocusableButton(
                            text = "強制放棄本次進度並離開",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                coroutineScope.launch {
                                    app.emulatorSession.stopGame(forceDiscard = true)
                                    app.inputManager.setMenuIntercept(false)
                                    onExitGame()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
