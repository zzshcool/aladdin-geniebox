package com.aladdin.geniebox.app

import android.app.Application
import com.aladdin.geniebox.data.db.GenieDatabase
import com.aladdin.geniebox.data.repository.GameRepository
import com.aladdin.geniebox.data.repository.RomSourceRepository
import com.aladdin.geniebox.data.repository.SaveRepository
import com.aladdin.geniebox.data.repository.SettingsRepository
import com.aladdin.geniebox.data.scanner.RomScanner
import com.aladdin.geniebox.data.storage.SafBridge
import com.aladdin.geniebox.data.storage.StorageCoordinator
import com.aladdin.geniebox.data.storage.StorageManager
import com.aladdin.geniebox.emulator.CoreRegistry
import com.aladdin.geniebox.emulator.EmulatorSession
import com.aladdin.geniebox.emulator.NativeEmulator
import com.aladdin.geniebox.input.InputManager
import kotlinx.coroutines.launch

class GenieApplication : Application() {

    lateinit var database: GenieDatabase private set
    lateinit var storageManager: StorageManager private set
    lateinit var safBridge: SafBridge private set
    lateinit var gameRepository: GameRepository private set
    lateinit var romSourceRepository: RomSourceRepository private set
    lateinit var saveRepository: SaveRepository private set
    lateinit var settingsRepository: SettingsRepository private set
    lateinit var romScanner: RomScanner private set
    lateinit var storageCoordinator: StorageCoordinator private set
    lateinit var coreRegistry: CoreRegistry private set
    lateinit var nativeEmulator: NativeEmulator private set
    lateinit var inputManager: InputManager private set
    lateinit var emulatorSession: EmulatorSession private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = GenieDatabase.getInstance(this)
        storageManager = StorageManager(this)
        safBridge = SafBridge(this, storageManager)

        // 背景預先部署 PPSSPP 必要資產至 files/system/PPSSPP
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            storageManager.deployPpssppAssetsIfNeeded()
        }

        gameRepository = GameRepository(
            database.gameDao(),
            database.favoriteDao(),
            database.playHistoryDao()
        )
        romSourceRepository = RomSourceRepository(
            database.romSourceDao(),
            database.gameDao()
        )
        saveRepository = SaveRepository(
            database.saveMetadataDao(),
            storageManager
        )
        settingsRepository = SettingsRepository(this)

        romScanner = RomScanner(
            this,
            database.gameDao(),
            database.romSourceDao()
        )

        storageCoordinator = StorageCoordinator(
            this,
            romSourceRepository,
            romScanner
        )
        storageCoordinator.startListening()

        coreRegistry = CoreRegistry(this)
        nativeEmulator = NativeEmulator()
        inputManager = InputManager(this, nativeEmulator)

        emulatorSession = EmulatorSession(
            this,
            nativeEmulator,
            coreRegistry,
            storageManager,
            safBridge,
            saveRepository,
            gameRepository,
            romSourceRepository
        )
    }

    companion object {
        lateinit var instance: GenieApplication
            private set
    }
}
