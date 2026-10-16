package com.aladdin.geniebox.feature.settings

import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aladdin.geniebox.emulator.CoreRegistry
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.app.GenieApplication
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.input.RetroPadKey
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val app = GenieApplication.instance
    val context = LocalContext.current
    val currentLanguage = remember { AppLanguage.selectedTag(context) }
    val themeMode by app.settingsRepository.themeMode.collectAsState(initial = 0)
    val coroutineScope = rememberCoroutineScope()

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var touchPlatformId by rememberSaveable { mutableStateOf(PlatformType.SFC.id) }
    val touchPlatform = PlatformType.fromId(touchPlatformId) ?: PlatformType.SFC
    val touchOpacity by remember(touchPlatform) { app.settingsRepository.touchOpacity(touchPlatform) }.collectAsState(initial = 0.65f)
    val touchScale by remember(touchPlatform) { app.settingsRepository.touchScale(touchPlatform) }.collectAsState(initial = 1f)
    val touchEdge by remember(touchPlatform) { app.settingsRepository.touchEdgeInset(touchPlatform) }.collectAsState(initial = 8f)
    val touchBottom by remember(touchPlatform) { app.settingsRepository.touchBottomInset(touchPlatform) }.collectAsState(initial = 12f)
    val touchVibration by remember(touchPlatform) { app.settingsRepository.touchVibration(touchPlatform) }.collectAsState(initial = true)
    val sections = listOf(R.string.ux_section_folders, R.string.ux_section_controls, R.string.ux_section_video, R.string.settings_language_appearance, R.string.ux_section_saves, R.string.ux_section_about)

    var actionMessage by remember { mutableStateOf("") }
    var fileOperationRunning by remember { mutableStateOf(false) }
    var ps1BiosReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ps1BiosReady = withContext(Dispatchers.IO) { app.coreRegistry.checkPs1BiosAvailable() }
    }
    var isScanning by remember { mutableStateOf(false) }
    var mappingPlayer by remember { mutableStateOf<Int?>(null) }
    var mappingTarget by remember { mutableStateOf<String?>(null) }
    var mappingRevision by remember { mutableIntStateOf(0) }
    val mappingButtons = listOf(
        RetroPadKey.A to "A", RetroPadKey.B to "B", RetroPadKey.X to "X", RetroPadKey.Y to "Y",
        RetroPadKey.L to "L", RetroPadKey.R to "R", RetroPadKey.L2 to "L2", RetroPadKey.R2 to "R2",
        RetroPadKey.L3 to "L3", RetroPadKey.R3 to "R3", RetroPadKey.SELECT to "SELECT", RetroPadKey.START to "START"
    )
    DisposableEffect(Unit) {
        onDispose { app.inputManager.cancelButtonMapping() }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null && !fileOperationRunning) {
            fileOperationRunning = true
            coroutineScope.launch {
                val success = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            app.storageManager.exportBackup(os)
                        } ?: false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                } finally {
                    fileOperationRunning = false
                }
                actionMessage = if (success) AppLanguage.text(R.string.ui_saves_and_states_exported_successfully) else AppLanguage.text(R.string.ui_export_failed_check_storage_access)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !fileOperationRunning) {
            fileOperationRunning = true
            coroutineScope.launch {
                val success = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { `is` ->
                            app.storageManager.importBackup(`is`)
                        } ?: false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                } finally {
                    fileOperationRunning = false
                }
                actionMessage = if (success) AppLanguage.text(R.string.ui_backup_restored_successfully) else AppLanguage.text(R.string.ui_import_failed_the_backup_may_be_damaged_or)
            }
        }
    }

    val importBiosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !fileOperationRunning) {
            fileOperationRunning = true
            coroutineScope.launch {
                val success = try {
                    withContext(Dispatchers.IO) {
                        val name = DocumentFile.fromSingleUri(context, uri)?.name
                        val acceptedName = app.coreRegistry.cores[PlatformType.PS1]?.biosFiles
                            ?.firstOrNull { it.equals(name, ignoreCase = true) }
                        if (acceptedName == null) return@withContext false
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val data = ByteArray(CoreRegistry.PS1_BIOS_SIZE_BYTES)
                            var count = 0
                            while (count < data.size) {
                                val read = input.read(data, count, data.size - count)
                                if (read <= 0) return@use null
                                count += read
                            }
                            if (input.read() != -1) null else data
                        } ?: return@withContext false
                        app.storageManager.atomicWrite(File(app.storageManager.systemDir, acceptedName), bytes)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                } finally {
                    fileOperationRunning = false
                }
                ps1BiosReady = withContext(Dispatchers.IO) { app.coreRegistry.checkPs1BiosAvailable() }
                actionMessage = AppLanguage.text(if (success) R.string.review_bios_imported else R.string.review_bios_failed)
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
                val displayName = AppLanguage.text(R.string.ui_game_storage_value_62, sourceId)
                app.romSourceRepository.registerSource(sourceId, displayName, uri.toString())
                actionMessage = AppLanguage.text(R.string.ui_added_value_scanning_your_games, displayName)
                isScanning = true
                var scanError: String? = null
                try {
                    val count = app.romScanner.scanSource(sourceId, uri) { prog ->
                        if (prog.isFinished && prog.error != null) {
                            scanError = prog.error
                        }
                        actionMessage = prog.error ?: AppLanguage.text(R.string.ui_scanning_value_games, prog.scannedCount)
                    }
                    actionMessage = scanError?.let { AppLanguage.text(R.string.ui_scan_failed_value, it) }
                        ?: AppLanguage.text(R.string.ui_scan_complete_value_games_found, count)
                } catch (e: Exception) {
                    actionMessage = AppLanguage.text(R.string.ui_scan_failed_value, e.message)
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
    val offerResume by app.settingsRepository.autoResume.collectAsState(initial = true)
    val isTvDevice = remember { app.settingsRepository.isTvDevice() }


    BackHandler {
        onNavigateBack()
    }

    if (mappingPlayer != null) {
        val player = mappingPlayer!!
        fun closeMapping() {
            app.inputManager.cancelButtonMapping()
            mappingTarget = null
            mappingPlayer = null
        }
        AlertDialog(
            onDismissRequest = { closeMapping() },
            title = { Text(AppLanguage.text(R.string.play_mapping_title, player)) },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppLanguage.text(R.string.play_mapping_hint), color = TextSecondary)
                    mappingTarget?.let { Text(AppLanguage.text(R.string.play_mapping_press, it), color = AladdinGold) }
                    mappingButtons.forEach { (key, label) ->
                        val physical = remember(player, key, mappingRevision) { app.inputManager.mappedButtonName(player, key) }
                        FocusableButton(
                            text = "$label · $physical",
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = mappingTarget == label,
                            enabled = mappingTarget == null,
                            onClick = {
                                mappingTarget = label
                                app.inputManager.beginButtonMapping(player, key) {
                                    mappingTarget = null
                                    mappingRevision++
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { closeMapping() }) { Text(AppLanguage.text(R.string.play_mapping_done)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.inputManager.cancelButtonMapping()
                    app.inputManager.resetButtonMapping(player)
                    mappingTarget = null
                    mappingRevision++
                }) { Text(AppLanguage.text(R.string.play_mapping_reset)) }
            }
        )
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = AppLanguage.text(R.string.ui_settings_67),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AladdinGold
                )
                FocusableButton(
                    text = AppLanguage.text(R.string.ui_back_to_library),
                    onClick = onNavigateBack
                )
            }
                ScrollableTabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = BgDark,
                    edgePadding = 0.dp
                ) {
                    sections.forEachIndexed { section, label ->
                        Tab(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            text = { Text(AppLanguage.text(label)) }
                        )
                    }
                }
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
            if (selectedSection == 3) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(AppLanguage.text(R.string.settings_language_appearance),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(AppLanguage.text(R.string.settings_language), color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusableButton(
                                text = AppLanguage.text(R.string.language_chinese),
                                isPrimary = currentLanguage == AppLanguage.CHINESE,
                                onClick = { AppLanguage.selectAndRecreate(context, AppLanguage.CHINESE) }
                            )
                            FocusableButton(
                                text = AppLanguage.text(R.string.language_simplified_chinese),
                                isPrimary = currentLanguage == AppLanguage.SIMPLIFIED_CHINESE,
                                onClick = { AppLanguage.selectAndRecreate(context, AppLanguage.SIMPLIFIED_CHINESE) }
                            )
                            FocusableButton(
                                text = AppLanguage.text(R.string.language_english),
                                isPrimary = currentLanguage == AppLanguage.ENGLISH,
                                onClick = { AppLanguage.selectAndRecreate(context, AppLanguage.ENGLISH) }
                            )
                        }
                        Text(AppLanguage.text(R.string.settings_theme), color = TextSecondary)
                        listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
                            .forEachIndexed { mode, label ->
                                FocusableButton(
                                    text = AppLanguage.text(label),
                                    modifier = Modifier.fillMaxWidth(),
                                    isPrimary = themeMode == mode,
                                    onClick = {
                                        coroutineScope.launch { app.settingsRepository.setThemeMode(mode) }
                                    }
                                )
                            }
                    }
                }
            }

            if (selectedSection == 1) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(AppLanguage.text(R.string.ui_controller_assignment_p1_p4), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            AppLanguage.text(R.string.ui_connect_usb_bluetooth_or_receiver_based_controllers_press),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        FlowRow(modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = if (isTvDevice) 4 else 2,
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            playerProfiles.forEach { p ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (p.isConnected) CardDarkFocused else BgDark, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(AppLanguage.text(R.string.ui_player_value, p.playerId), fontWeight = FontWeight.Bold, color = if (p.isConnected) AccentGreen else TextTertiary)
                                        Text(
                                            text = p.deviceName.ifBlank {
                                                AppLanguage.text(if (p.isConnected) R.string.ui_game_controller else R.string.ui_unassigned)
                                            },
                                            fontSize = 12.sp, color = TextPrimary, maxLines = 1
                                        )
                                        Text(if (p.isConnected) AppLanguage.text(R.string.ui_connected) else AppLanguage.text(R.string.ui_no_controller_detected), fontSize = 11.sp, color = TextSecondary)
                                        FocusableButton(
                                            text = AppLanguage.text(R.string.play_button_mapping),
                                            enabled = p.isConnected,
                                            onClick = { mappingPlayer = p.playerId }
                                        )
                                    }
                                }
                            }
                        }

                        FocusableButton(
                            text = AppLanguage.text(R.string.ui_rescan_controllers),
                            onClick = {
                                app.inputManager.refreshConnectedDevices()
                                actionMessage = AppLanguage.text(R.string.ui_input_devices_rescanned)
                            }
                        )
                    }
                }
            }

            // 2. 遊戲 ROM 目錄來源管理 (支援手機內部儲存 / USB 隨身碟)
            if (selectedSection == 0) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(AppLanguage.text(R.string.ui_game_folders), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            AppLanguage.text(R.string.ui_choose_a_game_folder_on_your_phone_usb_76),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        FocusableButton(
                            text = AppLanguage.text(R.string.ui_add_game_folder_phone_usb_drive),
                            onClick = {
                                addSourceLauncher.launch(null)
                            }
                        )

                        if (sources.isEmpty()) {
                            Text(AppLanguage.text(R.string.ui_no_game_folders_yet_use_the_button_above), color = TextTertiary, fontSize = 13.sp)
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
                                        Text(if (src.isConnected) AppLanguage.text(R.string.ui_status_online) else AppLanguage.text(R.string.ui_status_disconnected), fontSize = 12.sp, color = if (src.isConnected) AccentGreen else AccentRed)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FocusableButton(
                                            text = if (isScanning) AppLanguage.text(R.string.ui_scanning) else AppLanguage.text(R.string.ui_rescan),
                                            onClick = {
                                                if (!isScanning) {
                                                    isScanning = true
                                                    coroutineScope.launch {
                                                        actionMessage = AppLanguage.text(R.string.ui_scanning_value, src.displayName)
                                                        var scanError: String? = null
                                                        try {
                                                            val count = app.romScanner.scanSource(src.id, Uri.parse(src.treeUri)) { prog ->
                                                                if (prog.isFinished && prog.error != null) {
                                                                    scanError = prog.error
                                                                }
                                                                actionMessage = prog.error ?: AppLanguage.text(R.string.ui_value_games_found, prog.scannedCount)
                                                            }
                                                            actionMessage = scanError?.let { AppLanguage.text(R.string.ui_scan_failed_value, it) }
                                                                ?: AppLanguage.text(R.string.ui_scan_complete_value_games_found, count)
                                                        } catch (e: Exception) {
                                                            actionMessage = AppLanguage.text(R.string.ui_scan_failed_value, e.message)
                                                        } finally {
                                                            isScanning = false
                                                        }
                                                    }
                                                }
                                            }
                                        )

                                        FocusableButton(
                                            text = AppLanguage.text(R.string.ui_remove_folder),
                                            onClick = {
                                                coroutineScope.launch {
                                                    app.romSourceRepository.removeSource(src.id)
                                                    actionMessage = AppLanguage.text(R.string.ui_removed_value, src.displayName)
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
            if (selectedSection == 0) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(AppLanguage.text(R.string.ui_bios_and_emulator_cores), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PS1 BIOS：", color = TextSecondary, fontSize = 14.sp)
                            Text(
                                text = if (ps1BiosReady) AppLanguage.text(R.string.ui_ready_original_bios) else AppLanguage.text(R.string.ui_original_bios_missing_using_hle_fallback),
                                color = if (ps1BiosReady) AccentGreen else AccentRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            AppLanguage.text(R.string.review_bios_hint),
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                        FocusableButton(
                            text = AppLanguage.text(R.string.review_import_bios),
                            enabled = !fileOperationRunning,
                            onClick = { importBiosLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }

            // 4. 影音與視訊渲染設定
            if (selectedSection == 2) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(AppLanguage.text(R.string.ui_video_and_rendering), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(AppLanguage.text(R.string.ui_aspect_ratio), color = TextSecondary, fontSize = 14.sp)
                            val modes = listOf(AppLanguage.text(R.string.ui_original_default), AppLanguage.text(R.string.ui_integer_scaling), AppLanguage.text(R.string.ui_stretch_to_screen))
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

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(AppLanguage.text(R.string.ui_smoothing), color = TextSecondary, fontSize = 14.sp)
                            FocusableButton(
                                text = if (currentBilinear) AppLanguage.text(R.string.ui_bilinear_smoothing_on) else AppLanguage.text(R.string.ui_nearest_neighbor_pixels_off),
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
                                text = if (showFpsHud) AppLanguage.text(R.string.ui_show_fps_hud) else AppLanguage.text(R.string.ui_hide_fps_hud),
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
            if (selectedSection == 1) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(AppLanguage.text(R.string.ui_touch_controls_and_device_mode), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(
                            AppLanguage.text(R.string.ui_detected_device_value, if (isTvDevice) AppLanguage.text(R.string.ui_tv_android_tv) else AppLanguage.text(R.string.ui_phone_tablet)),
                            fontSize = 13.sp,
                            color = if (isTvDevice) AladdinGold else AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(AppLanguage.text(R.string.ui_touch_controls), color = TextSecondary, fontSize = 14.sp)
                            val gamepadModes = listOf(AppLanguage.text(R.string.ui_auto_hidden_on_tv), AppLanguage.text(R.string.ui_always_show), AppLanguage.text(R.string.ui_always_hide))
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
                        Text(AppLanguage.text(R.string.ux_platform_controls), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        ScrollableTabRow(selectedTabIndex = touchPlatform.ordinal, containerColor = CardDark, edgePadding = 0.dp) {
                            PlatformType.entries.forEach { platform ->
                                Tab(
                                    selected = platform == touchPlatform,
                                    onClick = { touchPlatformId = platform.id },
                                    text = { Text(AppLanguage.platformName(platform)) }
                                )
                            }
                        }
                        TouchControlSlider(AppLanguage.text(R.string.ux_control_opacity), touchOpacity, 0.2f..1f, multiplier = 100f, unit = "%") {
                            coroutineScope.launch { app.settingsRepository.setTouchOpacity(touchPlatform, it) }
                        }
                        TouchControlSlider(AppLanguage.text(R.string.ux_control_scale), touchScale, 0.75f..1.4f, multiplier = 100f, unit = "%") {
                            coroutineScope.launch { app.settingsRepository.setTouchScale(touchPlatform, it) }
                        }
                        TouchControlSlider(AppLanguage.text(R.string.ux_control_edge), touchEdge, 0f..64f, unit = "dp") {
                            coroutineScope.launch { app.settingsRepository.setTouchPosition(touchPlatform, it, touchBottom) }
                        }
                        TouchControlSlider(AppLanguage.text(R.string.ux_control_bottom), touchBottom, 0f..72f, unit = "dp") {
                            coroutineScope.launch { app.settingsRepository.setTouchPosition(touchPlatform, touchEdge, it) }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(AppLanguage.text(R.string.ux_control_vibration), color = TextPrimary)
                            Switch(checked = touchVibration, onCheckedChange = {
                                coroutineScope.launch { app.settingsRepository.setTouchVibration(touchPlatform, it) }
                            })
                        }
                        Text(AppLanguage.text(R.string.ux_control_safe_area), fontSize = 12.sp, color = TextSecondary)
                        FocusableButton(
                            text = AppLanguage.text(R.string.ux_reset_controls),
                            onClick = { coroutineScope.launch { app.settingsRepository.resetTouchControls(touchPlatform) } }
                        )
                    }
                }
            }

            // 6. 存檔與備份管理
            if (selectedSection == 4) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(AppLanguage.text(R.string.ui_saves_and_backups), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(AppLanguage.text(R.string.play_offer_resume), modifier = Modifier.weight(1f), color = TextPrimary)
                            Switch(checked = offerResume, onCheckedChange = { enabled ->
                                coroutineScope.launch { app.settingsRepository.setAutoResume(enabled) }
                            })
                        }
                        Text(AppLanguage.text(R.string.play_auto_save_hint), color = TextSecondary, fontSize = 12.sp)
                        Text(
                            AppLanguage.text(R.string.ui_game_saves_and_save_states_stay_in_internal),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusableButton(
                                text = AppLanguage.text(R.string.ui_export_save_backup),
                                enabled = !fileOperationRunning,
                                onClick = {
                                    val defaultName = "genie_backup_${System.currentTimeMillis()}.zip"
                                    exportBackupLauncher.launch(defaultName)
                                }
                            )

                            FocusableButton(
                                text = AppLanguage.text(R.string.ui_restore_zip_backup),
                                enabled = !fileOperationRunning,
                                onClick = {
                                    importBackupLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                }
                            )

                            FocusableButton(
                                text = AppLanguage.text(R.string.ui_clear_rom_cache),
                                onClick = {
                                    val bytes = app.storageManager.clearRomStagingCache()
                                    actionMessage = AppLanguage.text(R.string.ui_freed_value_mb_of_cache, bytes / (1024 * 1024))
                                }
                            )
                        }
                    }
                }
            }

            // 6. 關於與版權宣告
            if (selectedSection == 5) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(AppLanguage.text(R.string.ui_about_aladdin_geniebox), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AladdinGold)
                        Text(AppLanguage.text(R.string.ui_project_license_gnu_general_public_license_v3_0), fontSize = 13.sp, color = TextPrimary)
                        Text(AppLanguage.text(R.string.ui_emulator_cores_fceumm_gpl_2_0_gambatte_gpl), fontSize = 12.sp, color = TextTertiary)
                        Text(AppLanguage.text(R.string.ui_works_offline_with_no_ads_or_telemetry_no), fontSize = 12.sp, color = TextTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


@Composable
private fun TouchControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    multiplier: Float = 1f,
    unit: String,
    onCommit: (Float) -> Unit
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$label: ${(draft * multiplier).toInt()}$unit", color = TextSecondary)
        Slider(
            modifier = Modifier.semantics { contentDescription = label },
            value = draft.coerceIn(range.start, range.endInclusive),
            onValueChange = { draft = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(draft) }
        )
    }
}
