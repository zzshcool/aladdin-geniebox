package com.aladdin.geniebox.feature.player

import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.PixelCopy
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import com.aladdin.geniebox.data.repository.SettingsRepository
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.component.VirtualGamepadOverlay
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip

@Composable
fun PlayerScreen(
    game: Game,
    onExitGame: () -> Unit
) {
    val app = GenieApplication.instance
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var gameSurface by remember { mutableStateOf<SurfaceView?>(null) }
    val core = app.coreRegistry.cores[game.platform]
    val availableSaves by remember(game.id) { app.saveRepository.getAvailableSavesForGame(game.id) }.collectAsState(initial = emptyList())
    val compatibleSaves = availableSaves.filter { it.coreId == core?.coreId && it.coreVersion == core?.version && it.formatVersion == 1 }
    val playbackSpeed by app.emulatorSession.playbackSpeed.collectAsState()
    val muteFastForward by app.emulatorSession.muteFastForward.collectAsState()
    var showResumePrompt by remember(game.id) { mutableStateOf(false) }
    var resumeTimestamp by remember(game.id) { mutableLongStateOf(0L) }

    val sessionState by app.emulatorSession.sessionState.collectAsState()
    val showFpsHud by app.settingsRepository.showFpsHud.collectAsState(initial = false)
    val touchOpacity by remember(game.platform) { app.settingsRepository.touchOpacity(game.platform) }.collectAsState(initial = 0.65f)
    val touchScale by remember(game.platform) { app.settingsRepository.touchScale(game.platform) }.collectAsState(initial = 1.0f)
    val touchVibration by remember(game.platform) { app.settingsRepository.touchVibration(game.platform) }.collectAsState(initial = true)

    val touchEdgeInset by remember(game.platform) { app.settingsRepository.touchEdgeInset(game.platform) }.collectAsState(initial = 8f)
    val touchBottomInset by remember(game.platform) { app.settingsRepository.touchBottomInset(game.platform) }.collectAsState(initial = 12f)

    val playerProfiles by app.inputManager.playerProfiles.collectAsState()
    val hasPhysicalGamepad = remember(playerProfiles) {
        playerProfiles.any { it.isConnected }
    }
    val virtualGamepadMode by app.settingsRepository.virtualGamepadMode.collectAsState(initial = 0)
    val isTvDevice = remember { app.settingsRepository.isTvDevice() }

    var showPauseMenu by remember { mutableStateOf(false) }
    var selectedSaveSlot by remember { mutableIntStateOf(1) }
    var pauseSection by remember { mutableIntStateOf(0) }
    var pendingOverwriteSlot by remember { mutableStateOf<Int?>(null) }
    var showRestartConfirmation by remember { mutableStateOf(false) }
    var pauseStatusText by remember { mutableStateOf("") }
    var operationInProgress by remember { mutableStateOf(false) }
    val checkpointSaving by app.emulatorSession.checkpointSaving.collectAsState()
    val menuBusy = operationInProgress || checkpointSaving
    var allowDiscard by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val resumeFocusRequester = remember { FocusRequester() }
    var totalDiscs by remember { mutableIntStateOf(0) }
    var currentDiscIndex by remember { mutableIntStateOf(0) }

    suspend fun saveWithPreview(slot: Int, stop: Boolean): Boolean = withContext(kotlinx.coroutines.NonCancellable) {
        app.emulatorSession.awaitPausedFrame()
        val previousPreview = availableSaves.firstOrNull { it.coreId == core?.coreId && it.slotIndex == slot }?.thumbnailPath
        val saveStartedAt = System.currentTimeMillis()
        val previewDir = withContext(Dispatchers.IO) { app.storageManager.getStateDir(core?.coreId ?: "unknown", game.id) }
        val preview = captureSavePreview(gameSurface, previewDir)
        var previewSaved = false
        try {
            val success = if (stop) {
                val result = app.emulatorSession.stopGame(forceDiscard = false, thumbnailPath = preview)
                previewSaved = app.saveRepository.getSavesForGame(game.id).first().any {
                    it.coreId == core?.coreId && it.slotIndex == 0 && it.timestamp >= saveStartedAt && it.thumbnailPath == preview
                }
                if (!result.success) {
                    pauseStatusText = AppLanguage.text(R.string.ui_save_failed_value_retry_or_leave_without_saving, result.errorMessage)
                }
                result.success
            } else {
                app.emulatorSession.saveManualState(slot, thumbnailPath = preview).also { previewSaved = it }
            }
            if (previewSaved && previousPreview != null) withContext(Dispatchers.IO) { File(previousPreview).delete() }
            success
        } finally {
            if (!previewSaved && preview != null) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { File(preview).delete() }
        }
    }

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


    fun runMenuOperation(saving: Boolean = false, action: suspend () -> Unit) {
        if (menuBusy) return
        operationInProgress = true
        coroutineScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pauseStatusText = AppLanguage.text(R.string.ux_action_failed, e.localizedMessage ?: "")
                if (saving) allowDiscard = true
            } finally {
                operationInProgress = false
            }
        }
    }

    fun saveSlot(slot: Int) {
        runMenuOperation(saving = true) {
            val ok = saveWithPreview(slot, stop = false)
            allowDiscard = !ok
            pauseStatusText = if (ok) AppLanguage.text(R.string.ui_saved_to_slot_value, slot)
                else AppLanguage.text(R.string.ui_save_failed_not_enough_space_or_unsupported_core)
        }
    }

    LaunchedEffect(showPauseMenu) {
        if (showPauseMenu) {
            totalDiscs = app.emulatorSession.getDiskCount()
            if (isTvDevice) {
                withFrameNanos { }
                runCatching { resumeFocusRequester.requestFocus() }
            }
        }
    }

    // 啟動遊戲
    LaunchedEffect(game.id) {
        if (app.emulatorSession.pauseActiveGame(game.id)) {
            showPauseMenu = true
            app.inputManager.setMenuIntercept(true)
            return@LaunchedEffect
        }
        app.emulatorSession.setPlaybackSpeed(app.settingsRepository.gameSpeed(game.id).first())
        app.emulatorSession.setMuteFastForward(app.settingsRepository.muteFastForward.first())
        val resumeSave = app.saveRepository.getAvailableSavesForGame(game.id).first().firstOrNull {
            it.slotIndex == 0 && it.coreId == core?.coreId && it.coreVersion == core?.version &&
                it.formatVersion == 1
        }
        if (app.settingsRepository.autoResume.first() && resumeSave != null) {
            resumeTimestamp = resumeSave.timestamp
            showResumePrompt = true
            app.inputManager.setMenuIntercept(true)
        } else {
            app.emulatorSession.startGame(game, autoLoadResumeState = false)
        }
    }

    LaunchedEffect(sessionState) {
        if (sessionState is EmulationSessionState.Paused) {
            showPauseMenu = true
            app.inputManager.setMenuIntercept(true)
        }
    }

    LaunchedEffect(game.id) {
        app.emulatorSession.checkpointError.collect { error ->
            if (error != null) {
                pauseStatusText = error
                allowDiscard = true
            }
        }
    }

    LaunchedEffect(game.id) {
        app.inputManager.disconnections.collect { player ->
            if (app.emulatorSession.sessionState.value is EmulationSessionState.Running) {
                app.emulatorSession.pause()
                app.inputManager.setMenuIntercept(true)
                pauseStatusText = AppLanguage.text(R.string.play_controller_disconnected, player)
                showPauseMenu = true
            }
        }
    }

    // 返回鍵攔截：優先開啟暫停選單，避免直接退出
    BackHandler {
        if (showResumePrompt) return@BackHandler
        if (menuBusy || showDiscardConfirmation || showRestartConfirmation || pendingOverwriteSlot != null) return@BackHandler
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
                    gameSurface = this
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
                edgeInset = touchEdgeInset,
                bottomInset = touchBottomInset,
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
                        contentDescription = AppLanguage.text(R.string.ui_pause_and_open_the_game_menu),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 浮動診斷 HUD (FPS、音訊狀態)
        if (playbackSpeed > 1f && !showPauseMenu && sessionState is EmulationSessionState.Running) {
            Text(
                text = "${playbackSpeed.toString().removeSuffix(".0")}×",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(8.dp)
            )
        }
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
                        Text(text = AppLanguage.text(R.string.ui_starting_value, state.gameTitle), fontSize = 16.sp, color = TextPrimary)
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
                            text = AppLanguage.text(R.string.ui_unable_to_start_game),
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
                            text = AppLanguage.text(R.string.ui_back_to_library),
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
            val selectedSave = availableSaves.firstOrNull { it.coreId == core?.coreId && it.slotIndex == selectedSaveSlot }
            val primaryActions: @Composable ColumnScope.() -> Unit = {
                FocusableButton(
                    text = AppLanguage.text(R.string.ui_resume_game),
                    modifier = Modifier.fillMaxWidth().focusRequester(resumeFocusRequester),
                    isPrimary = true,
                    enabled = !menuBusy,
                    onClick = {
                        showPauseMenu = false
                        app.inputManager.setMenuIntercept(false)
                        app.emulatorSession.resume()
                    }
                )
                FocusableButton(
                    text = AppLanguage.text(R.string.ui_save_and_return_to_library),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !menuBusy,
                    onClick = {
                        runMenuOperation(saving = true) {
                            val success = saveWithPreview(0, stop = true)
                            allowDiscard = !success
                            if (success) {
                                app.inputManager.setMenuIntercept(false)
                                onExitGame()
                            }
                        }
                    }
                )
                FocusableButton(
                    text = AppLanguage.text(R.string.ui_restart),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !menuBusy,
                    onClick = { showRestartConfirmation = true }
                )
                FocusableButton(
                    text = AppLanguage.text(R.string.ui_leave_without_saving),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !menuBusy,
                    onClick = { showDiscardConfirmation = true }
                )
            }
            val speedActions: @Composable ColumnScope.() -> Unit = {
                Text(AppLanguage.text(R.string.play_speed), color = TextPrimary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsRepository.GAME_SPEEDS.forEach { speed ->
                        FocusableButton(
                            text = "${speed.toString().removeSuffix(".0")}×",
                            isPrimary = playbackSpeed == speed,
                            enabled = !menuBusy,
                            onClick = {
                                runMenuOperation {
                                    app.settingsRepository.setGameSpeed(game.id, speed)
                                    app.emulatorSession.setPlaybackSpeed(speed)
                                }
                            }
                        )
                    }
                }
                Text(AppLanguage.text(R.string.play_speed_hint), color = TextSecondary, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(AppLanguage.text(R.string.play_mute_fast_forward), modifier = Modifier.weight(1f), color = TextPrimary)
                    Switch(
                        checked = muteFastForward,
                        enabled = !menuBusy,
                        onCheckedChange = { enabled ->
                            runMenuOperation {
                                app.settingsRepository.setMuteFastForward(enabled)
                                app.emulatorSession.setMuteFastForward(enabled)
                            }
                        }
                    )
                }
            }
            val stateActions: @Composable ColumnScope.() -> Unit = {
                (1..5).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { slot ->
                            val saved = availableSaves.firstOrNull { it.coreId == core?.coreId && it.slotIndex == slot }
                            val status = when {
                                saved == null -> AppLanguage.text(R.string.play_empty_slot)
                                saved !in compatibleSaves -> AppLanguage.text(R.string.play_incompatible_slot)
                                else -> AppLanguage.text(R.string.play_saved_slot)
                            }
                            var slotFocused by remember { mutableStateOf(false) }
                            val selected = slot == selectedSaveSlot
                            OutlinedButton(
                                modifier = Modifier.weight(1f).heightIn(min = 88.dp)
                                    .onFocusChanged { slotFocused = it.isFocused },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(if (selected || slotFocused) 2.dp else 1.dp,
                                    if (slotFocused || selected) AladdinGold else DividerDark),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) AladdinGold.copy(alpha = 0.12f) else CardDarkFocused,
                                    contentColor = TextPrimary),
                                contentPadding = PaddingValues(12.dp),
                                enabled = !menuBusy,
                                onClick = { selectedSaveSlot = slot }
                            ) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(AppLanguage.text(R.string.ui_slot_value, slot), fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f))
                                        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
                                    }
                                    Text(status, color = if (saved != null && saved in compatibleSaves) AccentGreen else TextSecondary,
                                        fontSize = 12.sp)
                                    saved?.let {
                                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timestamp)),
                                            color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (selectedSave != null) {
                    if (selectedSave.thumbnailPath == null) {
                        Text(AppLanguage.text(R.string.play_no_preview), color = TextSecondary, fontSize = 12.sp)
                    } else selectedSave.thumbnailPath.let { path ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(File(path))
                                .memoryCacheKey("$path:${selectedSave.timestamp}").build(),
                            contentDescription = AppLanguage.text(R.string.play_save_preview),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color.Black)
                        )
                    }
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(selectedSave.timestamp)),
                        color = TextSecondary, fontSize = 12.sp)
                } else {
                    Text(AppLanguage.text(R.string.play_empty_slot), color = TextSecondary, fontSize = 12.sp)
                }
                if (totalDiscs > 1) {
                    Text(AppLanguage.text(R.string.ui_change_disc), color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until totalDiscs).forEach { disc ->
                            FocusableButton(
                                text = AppLanguage.text(R.string.ui_disc_value, disc + 1),
                                isPrimary = disc == currentDiscIndex,
                                enabled = !menuBusy,
                                onClick = {
                                    runMenuOperation {
                                        val ok = app.emulatorSession.setDiskIndex(disc)
                                        if (ok) currentDiscIndex = disc
                                        pauseStatusText = if (ok) AppLanguage.text(R.string.ui_switched_to_disc_value, disc + 1)
                                            else AppLanguage.text(R.string.ui_unable_to_change_disc)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            val saveLoadActions: @Composable RowScope.() -> Unit = {
                FocusableButton(
                    text = AppLanguage.text(R.string.polish_save_short),
                    modifier = Modifier.weight(1f),
                    enabled = !menuBusy,
                    onClick = {
                        if (selectedSave != null) pendingOverwriteSlot = selectedSaveSlot else saveSlot(selectedSaveSlot)
                    }
                )
                FocusableButton(
                    text = AppLanguage.text(R.string.polish_load_short),
                    modifier = Modifier.weight(1f),
                    enabled = !menuBusy && selectedSave != null && selectedSave in compatibleSaves,
                    onClick = {
                        runMenuOperation {
                            val ok = app.emulatorSession.loadManualState(selectedSaveSlot)
                            if (ok) {
                                allowDiscard = false
                                showPauseMenu = false
                                app.inputManager.setMenuIntercept(false)
                                app.emulatorSession.resume()
                            } else {
                                pauseStatusText = AppLanguage.text(R.string.ui_load_failed_empty_slot_or_incompatible_version)
                            }
                        }
                    }
                )
            }
            val detailActions: @Composable ColumnScope.() -> Unit = {
                TabRow(selectedTabIndex = pauseSection, containerColor = CardDark, contentColor = AladdinGold) {
                    listOf(R.string.polish_saves, R.string.polish_speed_audio).forEachIndexed { index, label ->
                        Tab(selected = pauseSection == index, enabled = !menuBusy,
                            onClick = { pauseSection = index }, text = { Text(AppLanguage.text(label)) })
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pauseSection == 0) stateActions() else speedActions()
                }
                if (pauseSection == 0) {
                    Text(AppLanguage.text(R.string.play_selected_slot, selectedSaveSlot), color = TextPrimary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = saveLoadActions)
                }
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().background(BgDark.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                val twoColumns = maxWidth > maxHeight
                Column(
                    modifier = Modifier
                        .widthIn(max = if (twoColumns) 880.dp else 480.dp)
                        .fillMaxHeight(0.94f)
                        .background(CardDark, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        AppLanguage.text(R.string.ui_paused_value, game.title),
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (menuBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(AppLanguage.text(R.string.ux_processing), color = TextSecondary, fontSize = 12.sp)
                    } else if (pauseStatusText.isNotEmpty()) {
                        Text(pauseStatusText, color = if (allowDiscard) AccentRed else TextSecondary, fontSize = 12.sp)
                    }
                    if (twoColumns) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp), content = primaryActions)
                            Column(Modifier.weight(1.3f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp), content = detailActions)
                        }
                    } else {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = detailActions)
                        HorizontalDivider()
                        primaryActions()
                    }
                }
            }
        }
        pendingOverwriteSlot?.let { slot ->
            AlertDialog(
                onDismissRequest = { pendingOverwriteSlot = null },
                title = { Text(AppLanguage.text(R.string.polish_overwrite_title, slot)) },
                text = { Text(AppLanguage.text(R.string.polish_overwrite_warning)) },
                confirmButton = {
                    TextButton(onClick = { pendingOverwriteSlot = null; saveSlot(slot) }) {
                        Text(AppLanguage.text(R.string.polish_overwrite))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOverwriteSlot = null }) { Text(AppLanguage.text(R.string.ux_cancel)) }
                }
            )
        }
        if (showRestartConfirmation) {
            AlertDialog(
                onDismissRequest = { showRestartConfirmation = false },
                title = { Text(AppLanguage.text(R.string.ui_restart)) },
                text = { Text(AppLanguage.text(R.string.polish_restart_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestartConfirmation = false
                        runMenuOperation {
                            app.emulatorSession.reset()
                            showPauseMenu = false
                            app.inputManager.setMenuIntercept(false)
                            app.emulatorSession.resume()
                        }
                    }) { Text(AppLanguage.text(R.string.ui_restart)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartConfirmation = false }) { Text(AppLanguage.text(R.string.ux_cancel)) }
                }
            )
        }
        if (showDiscardConfirmation) {
            AlertDialog(
                onDismissRequest = { if (!menuBusy) showDiscardConfirmation = false },
                title = { Text(AppLanguage.text(R.string.ui_leave_without_saving)) },
                text = { Text(AppLanguage.text(R.string.ux_discard_warning)) },
                confirmButton = {
                    TextButton(enabled = !menuBusy, onClick = {
                        runMenuOperation {
                            val result = app.emulatorSession.stopGame(forceDiscard = true)
                            if (result.success) {
                                showDiscardConfirmation = false
                                app.inputManager.setMenuIntercept(false)
                                onExitGame()
                            }
                        }
                    }) { Text(AppLanguage.text(R.string.ux_confirm_leave)) }
                },
                dismissButton = {
                    TextButton(enabled = !menuBusy, onClick = { showDiscardConfirmation = false }) {
                        Text(AppLanguage.text(R.string.ux_cancel))
                    }
                }
            )
        }
        if (showResumePrompt) {
            AlertDialog(
                onDismissRequest = { if (!menuBusy) onExitGame() },
                title = { Text(AppLanguage.text(R.string.play_resume_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(AppLanguage.text(R.string.play_resume_message,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(resumeTimestamp))))
                        if (menuBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (pauseStatusText.isNotEmpty()) Text(pauseStatusText, color = AccentRed)
                    }
                },
                confirmButton = {
                    TextButton(enabled = !menuBusy, onClick = {
                        runMenuOperation {
                            app.emulatorSession.startGame(game, autoLoadResumeState = true)
                            showResumePrompt = false
                            app.inputManager.setMenuIntercept(false)
                        }
                    }) { Text(AppLanguage.text(R.string.ui_resume_game)) }
                },
                dismissButton = {
                    Column {
                        TextButton(enabled = !menuBusy, onClick = {
                            runMenuOperation {
                                app.emulatorSession.startGame(game, autoLoadResumeState = false)
                                showResumePrompt = false
                                app.inputManager.setMenuIntercept(false)
                            }
                        }) { Text(AppLanguage.text(R.string.ui_restart)) }
                        TextButton(enabled = !menuBusy, onClick = onExitGame) {
                            Text(AppLanguage.text(R.string.ui_back_to_library))
                        }
                    }
                }
            )
        }
    }
}

private suspend fun captureSavePreview(surface: SurfaceView?, directory: File): String? {
    if (surface == null || !surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) return null
    val scale = 320f / maxOf(surface.width, surface.height)
    val bitmap = Bitmap.createBitmap((surface.width * scale).toInt().coerceAtLeast(1),
        (surface.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    try {
        val result = suspendCoroutine<Int> { continuation ->
            try {
                PixelCopy.request(surface, bitmap, { continuation.resume(it) }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
            }
        }
        if (result != PixelCopy.SUCCESS) return null
        return withContext(Dispatchers.IO) {
            directory.mkdirs()
            val file = File.createTempFile("state_", ".png", directory)
            try {
                val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (written) file.absolutePath else { file.delete(); null }
            } catch (_: java.io.IOException) {
                file.delete()
                null
            }
        }
    } catch (_: java.io.IOException) {
        return null
    } finally {
        bitmap.recycle()
    }
}
