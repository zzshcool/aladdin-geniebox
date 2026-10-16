package com.aladdin.geniebox.emulator

import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.documentfile.provider.DocumentFile
import com.aladdin.geniebox.data.repository.GameRepository
import com.aladdin.geniebox.data.repository.RomSourceRepository
import com.aladdin.geniebox.data.repository.SaveRepository
import com.aladdin.geniebox.data.repository.SettingsRepository
import com.aladdin.geniebox.data.storage.SafBridge
import com.aladdin.geniebox.data.storage.StorageManager
import com.aladdin.geniebox.domain.model.EmulationSessionState
import com.aladdin.geniebox.domain.model.EntryType
import com.aladdin.geniebox.domain.model.FailureReason
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.PlatformType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors

data class StopSessionResult(
    val success: Boolean,
    val sramSaved: Boolean,
    val stateSaved: Boolean,
    val errorMessage: String? = null
)

class EmulatorSession(
    private val context: Context,
    private val nativeEmulator: NativeEmulator,
    private val coreRegistry: CoreRegistry,
    private val storageManager: StorageManager,
    private val safBridge: SafBridge,
    private val saveRepository: SaveRepository,
    private val gameRepository: GameRepository,
    private val romSourceRepository: RomSourceRepository
) {
    companion object {
        private const val TAG = "EmulatorSession"
        private const val MAX_IN_MEMORY_ROM_BYTES = 64 * 1024 * 1024L // 64 MB 以下 ROM 載入記憶體 Buffer
    }

    private val _sessionState = MutableStateFlow<EmulationSessionState>(EmulationSessionState.Idle)
    val sessionState: StateFlow<EmulationSessionState> = _sessionState.asStateFlow()
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    private val _muteFastForward = MutableStateFlow(true)
    val muteFastForward: StateFlow<Boolean> = _muteFastForward.asStateFlow()
    private val _checkpointError = MutableStateFlow<String?>(null)
    val checkpointError: StateFlow<String?> = _checkpointError.asStateFlow()
    private val _checkpointSaving = MutableStateFlow(false)
    val checkpointSaving: StateFlow<Boolean> = _checkpointSaving.asStateFlow()

    fun setMuteFastForward(enabled: Boolean) {
        _muteFastForward.value = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        require(speed in SettingsRepository.GAME_SPEEDS)
        _playbackSpeed.value = speed
    }

    private val sessionMutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 專用單一執行緒，確保 EGL Context 永遠在同一個執行緒中創建、綁定與渲染 (Finding 4)
    private val emulationExecutor = Executors.newSingleThreadExecutor()
    private val emulationDispatcher = emulationExecutor.asCoroutineDispatcher()

    private var emulationJob: Job? = null
    private var audioJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile private var isRunning = false
    @Volatile private var isPaused = false
    @Volatile private var currentGame: Game? = null
    @Volatile private var isForeground = true
    val activeGame: Game? get() = currentGame

    suspend fun pauseActiveGame(gameId: String): Boolean = sessionMutex.withLock {
        if (!isRunning || currentGame?.id != gameId) return@withLock false
        pause()
        true
    }

    fun enterForeground() {
        isForeground = true
    }

    fun enterBackground() {
        isForeground = false
        pause()
        val gameId = currentGame?.id ?: return
        if (_checkpointSaving.value) return
        _checkpointSaving.value = true
        coroutineScope.launch {
            try {
                sessionMutex.withLock {
                    val game = currentGame ?: return@withLock
                    if (!isRunning || game.id != gameId) return@withLock
                    pause()
                    val result = saveProgressInternal(game)
                    _checkpointError.value = result.errorMessage
                }
            } finally {
                _checkpointSaving.value = false
            }
        }
    }
    private var stagedFile: File? = null

    init {
        nativeEmulator.nativeInit(
            systemDir = storageManager.systemDir.absolutePath,
            saveDir = context.filesDir.absolutePath + "/saves",
            coreAssetsDir = storageManager.coreAssetsDir.absolutePath
        )
    }

    suspend fun startGame(game: Game, autoLoadResumeState: Boolean = false) =
        coroutineScope.async { startGameInternal(game, autoLoadResumeState) }.await()

    private suspend fun startGameInternal(game: Game, autoLoadResumeState: Boolean) = sessionMutex.withLock {
        if (isRunning) {
            if (currentGame?.id == game.id) return@withLock
            if (!stopGameInternal(forceDiscard = false).success) return@withLock
        }

        _checkpointError.value = null
        currentGame = game
        _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_checking_the_core_and_device), 0.1f)

        // 1. 檢查 ABI
        if (!coreRegistry.isAbiSupported()) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.UNSUPPORTED_ABI,
                message = AppLanguage.text(R.string.ui_this_device_does_not_support_arm64_v8a_a)
            )
            return@withLock
        }

        // 2. 檢查核心產物
        val coreFile = coreRegistry.getCoreLibraryFile(game.platform)
        val desc = coreRegistry.cores[game.platform]
        if (coreFile == null || desc == null) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.MISSING_CORE,
                message = AppLanguage.text(R.string.ui_missing_value_core_value_build_it_with_scripts, AppLanguage.platformName(game.platform), desc?.soFileName ?: AppLanguage.text(R.string.ui_unknown))
            )
            return@withLock
        }

        // 3. PS1 BIOS 狀態
        if (game.platform == PlatformType.PS1 && !coreRegistry.checkPs1BiosAvailable()) {
            Log.w(TAG, "PS1 real BIOS missing, HLE will be used with limited compatibility")
        }

        // 3.1 PSP Assets 狀態與部署保證 (R7-02, R8-01)
        if (game.platform == PlatformType.PSP) {
            _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_preparing_psp_system_resources_and_fonts), 0.25f)
            val success = storageManager.deployPpssppAssetsIfNeeded()
            if (!success) {
                _sessionState.value = EmulationSessionState.Failed(
                    reason = FailureReason.UNKNOWN,
                    message = AppLanguage.text(R.string.ui_unable_to_deploy_psp_resources_check_available_storage)
                )
                return@withLock
            }
        }

        // 4. 準備 ROM Staging 檔案 (Finding 6, 7, 17)
        _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_preparing_the_game_rom), 0.4f)
        val uri = Uri.parse(game.documentUri)
        val fileName = File(game.relativePath).name

        val staged = withContext(Dispatchers.IO) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            when {
                // ZIP 格式 ROM：驗證並解壓單一 ROM
                ext == "zip" || game.entryType == EntryType.ZIP -> {
                    val allowedExts = game.platform.supportedExtensions.filter { it != "zip" }
                    safBridge.extractSingleRomFromZip(game.id, uri, allowedExts)
                }
                // PS1 CUE 格式：從來源樹解析父目錄，一併複製同目錄參照的所有 BIN 軌道 (R2-04, R2-12)
                ext == "cue" || game.entryType == EntryType.CUE_BIN -> {
                    val source = romSourceRepository.getSource(game.sourceId)
                    val parentDoc = if (source != null) {
                        safBridge.resolveParentDirDoc(Uri.parse(source.treeUri), game.relativePath)
                    } else null

                    safBridge.stageCueWithBinFiles(
                        gameId = game.id,
                        cueUri = uri,
                        cueFileName = fileName,
                        parentDirDoc = parentDoc,
                        sourceLastModified = game.lastModified,
                        onProgress = { cur, total ->
                            val p = if (total > 0) (cur.toFloat() / total.toFloat()) * 0.4f + 0.4f else 0.5f
                            _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_preparing_disc_tracks), p)
                        }
                    )
                }
                // PS1 M3U 多片格式：一併複製 M3U 及所引用的所有光碟 (R2-05, R2-12)
                ext == "m3u" || game.entryType == EntryType.M3U_DISC_SET -> {
                    val source = romSourceRepository.getSource(game.sourceId)
                    val parentDoc = if (source != null) {
                        safBridge.resolveParentDirDoc(Uri.parse(source.treeUri), game.relativePath)
                    } else null

                    safBridge.stageM3uWithDiscs(
                        gameId = game.id,
                        m3uUri = uri,
                        m3uFileName = fileName,
                        parentDirDoc = parentDoc,
                        sourceLastModified = game.lastModified,
                        onProgress = { cur, total ->
                            val p = if (total > 0) (cur.toFloat() / total.toFloat()) * 0.4f + 0.4f else 0.5f
                            _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_preparing_the_multi_disc_game), p)
                        }
                    )
                }
                // 一般單檔 ROM (含 ISO, SFC, NES, GBA 等)
                else -> {
                    safBridge.stageFile(
                        gameId = game.id,
                        uri = uri,
                        destFileName = fileName,
                        expectedSizeBytes = game.sizeBytes,
                        sourceLastModified = game.lastModified,
                        onProgress = { cur, total ->
                            val p = if (total > 0) (cur.toFloat() / total.toFloat()) * 0.4f + 0.4f else 0.5f
                            _sessionState.value = EmulationSessionState.Preparing(AppLanguage.text(R.string.ui_preparing_game_files), p)
                        }
                    )
                }
            }
        }

        if (staged == null || !staged.exists()) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.SOURCE_OFFLINE,
                message = AppLanguage.text(R.string.ui_game_source_is_offline_or_unreadable_value, fileName)
            )
            return@withLock
        }
        stagedFile = staged

        // 5. 載入動態庫與遊戲 (生命週期收斂於專用 emulationDispatcher，解決 R2-06)
        _sessionState.value = EmulationSessionState.Loading(game.title)
        val (coreLoaded, gameLoaded) = withContext(emulationDispatcher) {
            val cLoaded = nativeEmulator.nativeLoadCore(coreFile.absolutePath)
            if (!cLoaded) return@withContext Pair(false, false)

            // 若檔案小於 64 MB 且非光碟映像，讀取 byte array 提供給 need_fullpath=false 的核心
            val isDiscImage = (game.platform == PlatformType.PS1 || game.platform == PlatformType.PSP)
            val romBuffer = if (!isDiscImage && staged.length() <= MAX_IN_MEMORY_ROM_BYTES) {
                try {
                    staged.readBytes()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val gLoaded = nativeEmulator.nativeLoadGame(staged.absolutePath, romBuffer)
            if (!gLoaded) {
                nativeEmulator.nativeUnloadCore()
                return@withContext Pair(true, false)
            }

            // 7. 配置四人擴充模式 (SFC 啟用真實 Multitap=257，解決 R2-07)
            configureMultitapPorts(game.platform)

            Pair(true, true)
        }

        if (!coreLoaded) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.MISSING_CORE,
                message = AppLanguage.text(R.string.ui_unable_to_load_the_libretro_core_value, coreFile.name)
            )
            return@withLock
        }

        if (!gameLoaded) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.ROM_CORRUPTED,
                message = AppLanguage.text(R.string.ui_the_emulator_core_cannot_recognize_or_load_this)
            )
            return@withLock
        }

        // 8. 載入原生 SRAM 存檔 (若存在)
        val sram = saveRepository.loadSram(game.platform, game.id)
        if (sram != null && sram.isNotEmpty()) {
            withContext(emulationDispatcher) {
                nativeEmulator.nativeSetSramData(sram)
            }
        }

        // 9. 自動續玩 (核對 coreVersion 與 formatVersion) (Finding 15)
        if (autoLoadResumeState) {
            val stateData = saveRepository.loadStateData(
                gameId = game.id,
                coreId = desc.coreId,
                expectedCoreVersion = desc.version,
                slotIndex = 0
            )
            val restored = stateData != null && withContext(emulationDispatcher) {
                nativeEmulator.nativeUnserializeState(stateData)
            }
            if (!restored) {
                withContext(emulationDispatcher) {
                    nativeEmulator.nativeUnloadGame()
                    nativeEmulator.nativeUnloadCore()
                }
                _sessionState.value = EmulationSessionState.Failed(
                    reason = FailureReason.UNKNOWN,
                    message = AppLanguage.text(R.string.play_resume_failed)
                )
                return@withLock
            }
        }

        // 10. 記錄最近遊玩
        gameRepository.recordGamePlay(game.id, desc.coreId)

        // 11. 正確的啟動順序：先設定狀態標記，再啟動非同步迴圈工作
        isRunning = true
        isPaused = !isForeground
        startAudioStream()
        startEmulationLoop(game)

        _sessionState.value = if (isPaused) EmulationSessionState.Paused(game)
            else EmulationSessionState.Running(game = game)
    }

    private fun configureMultitapPorts(platform: PlatformType) {
        when (platform) {
            PlatformType.NES -> {
                // 啟用 4 個 Joypad 埠 (FCEUmm Four Score)
                for (p in 0..3) {
                    nativeEmulator.nativeSetControllerPortDevice(p, 1) // RETRO_DEVICE_JOYPAD
                }
            }
            PlatformType.SFC -> {
                // Snes9x Multitap (Port 0: Joypad=1, Port 1: Multitap=257，解決 R2-07)
                nativeEmulator.nativeSetControllerPortDevice(0, 1)
                nativeEmulator.nativeSetControllerPortDevice(1, 257)
            }
            PlatformType.PS1 -> {
                // PCSX ReARMed Multitap
                nativeEmulator.nativeSetControllerPortDevice(0, 1)
                nativeEmulator.nativeSetControllerPortDevice(1, 1)
            }
            else -> {
                // 單人平台
                nativeEmulator.nativeSetControllerPortDevice(0, 1)
            }
        }
    }

    private fun startAudioStream() {
        val sampleRate = nativeEmulator.nativeGetSampleRate().toInt().coerceAtLeast(11025)
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        audioJob = coroutineScope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(2048)
            val outputBuffer = ShortArray(audioBuffer.size)
            val history = DoubleArray(128)
            var historyFrame = 0
            var previousLeft = 0.0
            var previousRight = 0.0
            var outputSuppressed = false
            var sourceFramePosition = 0.0
            var previousAudioSpeed = _playbackSpeed.value
            var filter = fastForwardFilter(previousAudioSpeed)
            while (isActive && isRunning) {
                val speed = _playbackSpeed.value
                val suppressOutput = isPaused || (_muteFastForward.value && speed > 1f)
                if (speed != previousAudioSpeed || suppressOutput != outputSuppressed) {
                    sourceFramePosition = 0.0
                    filter = fastForwardFilter(speed)
                    history.fill(0.0)
                    historyFrame = 0
                    previousLeft = 0.0
                    previousRight = 0.0
                }
                previousAudioSpeed = speed
                if (suppressOutput != outputSuppressed) {
                    if (suppressOutput) {
                        audioTrack?.pause()
                        audioTrack?.flush()
                    } else {
                        audioTrack?.play()
                    }
                    outputSuppressed = suppressOutput
                }
                if (isPaused) {
                    delay(20)
                    continue
                }
                val readCount = nativeEmulator.nativeReadAudioSamples(audioBuffer)
                if (readCount > 0 && !suppressOutput) {
                    if (speed == 1f) {
                        audioTrack?.write(audioBuffer, 0, readCount)
                        continue
                    }
                    // Low-pass before reducing the sample rate. Keep filter history and
                    // fractional stereo interpolation continuous across native buffers.
                    val frameCount = readCount / 2
                    var outputSamples = 0
                    for (frame in 0 until frameCount) {
                        history[historyFrame * 2] = audioBuffer[frame * 2].toDouble()
                        history[historyFrame * 2 + 1] = audioBuffer[frame * 2 + 1].toDouble()
                        var left = 0.0
                        var right = 0.0
                        for (tap in filter.indices) {
                            val index = ((historyFrame - tap) and 63) * 2
                            left += filter[tap] * history[index]
                            right += filter[tap] * history[index + 1]
                        }
                        if (sourceFramePosition <= frame) {
                            val fraction = sourceFramePosition - (frame - 1)
                            outputBuffer[outputSamples++] = (previousLeft + (left - previousLeft) * fraction)
                                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            outputBuffer[outputSamples++] = (previousRight + (right - previousRight) * fraction)
                                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            sourceFramePosition += speed
                        }
                        previousLeft = left
                        previousRight = right
                        historyFrame = (historyFrame + 1) and 63
                    }
                    sourceFramePosition -= frameCount
                    audioTrack?.write(outputBuffer, 0, outputSamples)
                } else {
                    delay(2)
                }
            }
        }
    }

    private fun fastForwardFilter(speed: Float): DoubleArray {
        val taps = 63
        val cutoff = 0.45 / speed
        val coefficients = DoubleArray(taps) { tap ->
            val offset = tap - (taps - 1) / 2
            val sinc = if (offset == 0) 2 * cutoff
                else kotlin.math.sin(2 * Math.PI * cutoff * offset) / (Math.PI * offset)
            val phase = 2 * Math.PI * tap / (taps - 1)
            sinc * (0.42 - 0.5 * kotlin.math.cos(phase) + 0.08 * kotlin.math.cos(2 * phase))
        }
        val gain = coefficients.sum()
        for (index in coefficients.indices) coefficients[index] /= gain
        return coefficients
    }

    private fun startEmulationLoop(game: Game) {
        val targetFps = nativeEmulator.nativeGetTargetFps().coerceIn(30.0f, 120.0f)
        val framePeriodNs = (1_000_000_000.0 / targetFps).toLong()

        // 綁定在固定單一執行緒運行 (Finding 4)
        emulationJob = coroutineScope.launch(emulationDispatcher) {
            var nextFrameTime = System.nanoTime()
            var frameCounter = 0
            var lastFpsCalc = System.currentTimeMillis()
            var currentCalculatedFps = targetFps
            var previousSpeed = _playbackSpeed.value
            var renderCredit = 0.0

            while (isActive && isRunning) {
                if (isPaused) {
                    delay(50)
                    nextFrameTime = System.nanoTime()
                    continue
                }

                val speed = _playbackSpeed.value
                if (speed != previousSpeed) {
                    nextFrameTime = System.nanoTime()
                    renderCredit = 0.0
                    previousSpeed = speed
                }
                renderCredit += 1.0 / speed
                val presentFrame = renderCredit >= 1.0 - 0.000001
                if (presentFrame) renderCredit -= 1.0
                nativeEmulator.nativeRunFrame(presentFrame)
                frameCounter++

                val now = System.currentTimeMillis()
                if (now - lastFpsCalc >= 1000) {
                    currentCalculatedFps = (frameCounter * 1000.0f) / (now - lastFpsCalc)
                    frameCounter = 0
                    lastFpsCalc = now

                    val underruns = nativeEmulator.nativeGetAudioUnderruns()
                    _sessionState.update { state ->
                        if (isPaused) state else EmulationSessionState.Running(
                            game = game,
                            fps = currentCalculatedFps,
                            audioUnderruns = underruns,
                            isPaused = false
                        )
                    }
                }

                val acceleratedPeriodNs = (framePeriodNs / speed).toLong()
                nextFrameTime += acceleratedPeriodNs
                val sleepNs = nextFrameTime - System.nanoTime()
                if (sleepNs > 1_000_000) {
                    delay(sleepNs / 1_000_000)
                } else if (sleepNs < -acceleratedPeriodNs * 2) {
                    nextFrameTime = System.nanoTime()
                }
            }
        }
    }

    fun pause() {
        if (!isRunning || isPaused) return
        isPaused = true
        val g = currentGame ?: return
        _sessionState.value = EmulationSessionState.Paused(g)
    }

    fun resume() {
        if (!isRunning || !isPaused || _checkpointSaving.value || !isForeground) return
        isPaused = false
        val g = currentGame ?: return
        _sessionState.value = EmulationSessionState.Running(g)
    }

    fun reset() {
        if (isRunning) {
            coroutineScope.launch(emulationDispatcher) {
                nativeEmulator.nativeReset()
            }
        }
    }

    suspend fun awaitPausedFrame() = withContext(emulationDispatcher) { }

    suspend fun saveManualState(slotIndex: Int, thumbnailPath: String? = null): Boolean = sessionMutex.withLock {
        val g = currentGame ?: return@withLock false
        val stateBytes = withContext(emulationDispatcher) {
            nativeEmulator.nativeSerializeState()
        } ?: return@withLock false
        if (stateBytes.isEmpty()) return@withLock false
        val coreDesc = coreRegistry.cores[g.platform]
        saveRepository.saveState(
            gameId = g.id,
            coreId = coreDesc?.coreId ?: "unknown",
            coreVersion = coreDesc?.version ?: "1.0",
            slotIndex = slotIndex,
            stateData = stateBytes,
            thumbnailPath = thumbnailPath
        )
    }

    suspend fun loadManualState(slotIndex: Int): Boolean = sessionMutex.withLock {
        val g = currentGame ?: return@withLock false
        val coreDesc = coreRegistry.cores[g.platform] ?: return@withLock false
        val stateBytes = saveRepository.loadStateData(
            gameId = g.id,
            coreId = coreDesc.coreId,
            expectedCoreVersion = coreDesc.version,
            slotIndex = slotIndex
        ) ?: return@withLock false
        withContext(emulationDispatcher) {
            nativeEmulator.nativeUnserializeState(stateBytes)
        }
    }

    suspend fun setDiskIndex(index: Int): Boolean = withContext(emulationDispatcher) {
        nativeEmulator.nativeSetDiskIndex(index)
    }

    suspend fun getDiskCount(): Int = withContext(emulationDispatcher) {
        nativeEmulator.nativeGetDiskCount()
    }

    /**
     * 安全停止遊戲。forceDiscard=true 完全跳過保存；否則保存失敗時保持暫停並回報錯誤。
     */
    suspend fun stopGame(forceDiscard: Boolean = false, thumbnailPath: String? = null): StopSessionResult =
        withContext(NonCancellable) {
            sessionMutex.withLock { stopGameInternal(forceDiscard, thumbnailPath) }
        }

    private suspend fun stopGameInternal(forceDiscard: Boolean, thumbnailPath: String? = null): StopSessionResult {
        pause()
        val g = currentGame
        val saved = if (!forceDiscard && g != null && isRunning) {
            saveProgressInternal(g, thumbnailPath)
        } else StopSessionResult(success = true, sramSaved = false, stateSaved = false)
        if (!saved.success) return saved

        // 停止背景協程
        isRunning = false
        isPaused = false

        emulationJob?.cancelAndJoin()
        emulationJob = null

        audioJob?.cancelAndJoin()
        audioJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack release error", e)
        }
        audioTrack = null

        withContext(emulationDispatcher) {
            nativeEmulator.nativeUnloadGame()
            nativeEmulator.nativeUnloadCore()
        }

        currentGame = null
        _checkpointError.value = null
        _sessionState.value = EmulationSessionState.Idle
        return saved
    }

    private suspend fun saveProgressInternal(g: Game, thumbnailPath: String? = null): StopSessionResult {
        var sramSaved = true
        var stateSaved = false
        var errorMsg: String? = null

        try {
            // 1. 嘗試保存原生電池 SRAM (於專用 emulationDispatcher 執行 JNI)
            val sram = withContext(emulationDispatcher) {
                nativeEmulator.nativeGetSramData()
            }
            if (sram != null && sram.isNotEmpty()) {
                val ok = saveRepository.saveSram(g.platform, g.id, sram)
                if (!ok) {
                    sramSaved = false
                    errorMsg = AppLanguage.text(R.string.ui_unable_to_write_the_game_save_storage_full)
                }
            }

            // 2. 嘗試保存自動續玩 (Slot 0)
            val stateBytes = withContext(emulationDispatcher) {
                nativeEmulator.nativeSerializeState()
            }
            if (stateBytes != null && stateBytes.isNotEmpty()) {
                val coreDesc = coreRegistry.cores[g.platform]
                val ok = saveRepository.saveState(
                    gameId = g.id,
                    coreId = coreDesc?.coreId ?: "unknown",
                    coreVersion = coreDesc?.version ?: "1.0",
                    slotIndex = 0,
                    stateData = stateBytes,
                    thumbnailPath = thumbnailPath
                )
                if (!ok) {
                    if (errorMsg == null) errorMsg = AppLanguage.text(R.string.ui_unable_to_write_the_automatic_save_state)
                }
                stateSaved = ok
            } else {
                errorMsg = AppLanguage.text(R.string.review_state_unavailable)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save progress", e)
            return StopSessionResult(false, sramSaved, stateSaved,
                AppLanguage.text(R.string.ux_action_failed, e.localizedMessage ?: ""))
        }
        return StopSessionResult(
            success = sramSaved && stateSaved,
            sramSaved = sramSaved,
            stateSaved = stateSaved,
            errorMessage = errorMsg
        )
    }

    fun setSurface(surface: Surface?) {
        nativeEmulator.nativeSetSurface(surface)
    }

    fun surfaceDestroyed() {
        nativeEmulator.nativeSurfaceDestroyed()
    }

    fun setFilterMode(bilinear: Boolean) {
        nativeEmulator.nativeSetFilterMode(bilinear)
    }

    fun setAspectRatioMode(mode: Int) {
        nativeEmulator.nativeSetAspectRatioMode(mode)
    }
}
