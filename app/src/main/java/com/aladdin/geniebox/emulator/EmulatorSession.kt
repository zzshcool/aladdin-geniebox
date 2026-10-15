package com.aladdin.geniebox.emulator

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
    private var currentGame: Game? = null
    private var stagedFile: File? = null

    init {
        nativeEmulator.nativeInit(
            systemDir = storageManager.systemDir.absolutePath,
            saveDir = context.filesDir.absolutePath + "/saves",
            coreAssetsDir = storageManager.coreAssetsDir.absolutePath
        )
    }

    suspend fun startGame(game: Game, autoLoadResumeState: Boolean = false) = sessionMutex.withLock {
        if (isRunning) {
            stopGameInternal(forceDiscard = true)
        }

        currentGame = game
        _sessionState.value = EmulationSessionState.Preparing("正在檢查核心與設備環境…", 0.1f)

        // 1. 檢查 ABI
        if (!coreRegistry.isAbiSupported()) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.UNSUPPORTED_ABI,
                message = "設備未支援 arm64-v8a ABI，無法載入 64 位元模擬核心。"
            )
            return@withLock
        }

        // 2. 檢查核心產物
        val coreFile = coreRegistry.getCoreLibraryFile(game.platform)
        val desc = coreRegistry.cores[game.platform]
        if (coreFile == null || desc == null) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.MISSING_CORE,
                message = "缺少 ${game.platform.displayName} 核心動態庫 (${desc?.soFileName ?: "未知"})。請執行 scripts/build_cores.sh。"
            )
            return@withLock
        }

        // 3. PS1 BIOS 狀態
        if (game.platform == PlatformType.PS1 && !coreRegistry.checkPs1BiosAvailable()) {
            Log.w(TAG, "PS1 real BIOS missing, HLE will be used with limited compatibility")
        }

        // 3.1 PSP Assets 狀態與部署保證 (R7-02, R8-01)
        if (game.platform == PlatformType.PSP) {
            _sessionState.value = EmulationSessionState.Preparing("正在準備 PSP 系統資源與字型…", 0.25f)
            val success = storageManager.deployPpssppAssetsIfNeeded()
            if (!success) {
                _sessionState.value = EmulationSessionState.Failed(
                    reason = FailureReason.UNKNOWN,
                    message = "PPSSPP 系統資源部署失敗（可能內部儲存空間不足或讀寫異常）。請釋出儲存空間後重試。"
                )
                return@withLock
            }
        }

        // 4. 準備 ROM Staging 檔案 (Finding 6, 7, 17)
        _sessionState.value = EmulationSessionState.Preparing("正在準備遊戲 ROM 檔案…", 0.4f)
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
                            _sessionState.value = EmulationSessionState.Preparing("正在準備光碟軌道檔案…", p)
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
                            _sessionState.value = EmulationSessionState.Preparing("正在準備多片光碟檔案…", p)
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
                            _sessionState.value = EmulationSessionState.Preparing("正在準備遊戲檔案…", p)
                        }
                    )
                }
            }
        }

        if (staged == null || !staged.exists()) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.SOURCE_OFFLINE,
                message = "外接來源離線或無法讀取 ROM 檔案：$fileName"
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
                message = "載入 Libretro 核心動態庫失敗：${coreFile.name}"
            )
            return@withLock
        }

        if (!gameLoaded) {
            _sessionState.value = EmulationSessionState.Failed(
                reason = FailureReason.ROM_CORRUPTED,
                message = "模擬核心無法識別或載入此 ROM 格式。"
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
            if (stateData != null) {
                withContext(emulationDispatcher) {
                    nativeEmulator.nativeUnserializeState(stateData)
                }
            }
        }

        // 10. 記錄最近遊玩
        gameRepository.recordGamePlay(game.id, desc.coreId)

        // 11. 正確的啟動順序：先設定狀態標記，再啟動非同步迴圈工作
        isRunning = true
        isPaused = false
        startAudioStream()
        startEmulationLoop(game)

        _sessionState.value = EmulationSessionState.Running(game = game)
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
            while (isActive && isRunning) {
                if (isPaused) {
                    delay(20)
                    continue
                }
                val readCount = nativeEmulator.nativeReadAudioSamples(audioBuffer)
                if (readCount > 0) {
                    audioTrack?.write(audioBuffer, 0, readCount)
                } else {
                    delay(2)
                }
            }
        }
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

            while (isActive && isRunning) {
                if (isPaused) {
                    delay(50)
                    nextFrameTime = System.nanoTime()
                    continue
                }

                nativeEmulator.nativeRunFrame()
                frameCounter++

                val now = System.currentTimeMillis()
                if (now - lastFpsCalc >= 1000) {
                    currentCalculatedFps = (frameCounter * 1000.0f) / (now - lastFpsCalc)
                    frameCounter = 0
                    lastFpsCalc = now

                    val underruns = nativeEmulator.nativeGetAudioUnderruns()
                    _sessionState.value = EmulationSessionState.Running(
                        game = game,
                        fps = currentCalculatedFps,
                        audioUnderruns = underruns,
                        isPaused = false
                    )
                }

                nextFrameTime += framePeriodNs
                val sleepNs = nextFrameTime - System.nanoTime()
                if (sleepNs > 1_000_000) {
                    delay(sleepNs / 1_000_000)
                } else if (sleepNs < -framePeriodNs * 2) {
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
        if (!isRunning || !isPaused) return
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

    suspend fun saveManualState(slotIndex: Int): Boolean = sessionMutex.withLock {
        val g = currentGame ?: return@withLock false
        val stateBytes = withContext(emulationDispatcher) {
            nativeEmulator.nativeSerializeState()
        } ?: return@withLock false
        val coreDesc = coreRegistry.cores[g.platform]
        saveRepository.saveState(
            gameId = g.id,
            coreId = coreDesc?.coreId ?: "unknown",
            coreVersion = coreDesc?.version ?: "1.0",
            slotIndex = slotIndex,
            stateData = stateBytes
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
     * 安全停止遊戲。若存檔失敗且未強制放棄，維持暫停狀態，回報具體錯誤 (Finding 9, R2-06)
     */
    suspend fun stopGame(forceDiscard: Boolean = false): StopSessionResult = sessionMutex.withLock {
        stopGameInternal(forceDiscard)
    }

    private suspend fun stopGameInternal(forceDiscard: Boolean): StopSessionResult {
        var sramSaved = true
        var stateSaved = true
        var errorMsg: String? = null

        val g = currentGame
        if (g != null && isRunning) {
            // 1. 嘗試保存原生電池 SRAM (於專用 emulationDispatcher 執行 JNI)
            val sram = withContext(emulationDispatcher) {
                nativeEmulator.nativeGetSramData()
            }
            if (sram != null && sram.isNotEmpty()) {
                val ok = saveRepository.saveSram(g.platform, g.id, sram)
                if (!ok) {
                    sramSaved = false
                    errorMsg = "原生 SRAM 存檔寫入內部儲存失敗 (空間不足或權限錯誤)"
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
                    stateData = stateBytes
                )
                if (!ok) {
                    stateSaved = false
                    if (errorMsg == null) errorMsg = "自動即時存檔寫入失敗"
                }
            }

            // 如果保存失敗且未強制放棄，維持暫停狀態，不銷毀核心進度
            if ((!sramSaved || !stateSaved) && !forceDiscard) {
                isPaused = true
                _sessionState.value = EmulationSessionState.Paused(g)
                return StopSessionResult(
                    success = false,
                    sramSaved = sramSaved,
                    stateSaved = stateSaved,
                    errorMessage = errorMsg
                )
            }
        }

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
        _sessionState.value = EmulationSessionState.Idle

        return StopSessionResult(
            success = true,
            sramSaved = sramSaved,
            stateSaved = stateSaved
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
