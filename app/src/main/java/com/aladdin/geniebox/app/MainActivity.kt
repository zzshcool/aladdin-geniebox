package com.aladdin.geniebox.app

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.EmulationSessionState
import com.aladdin.geniebox.feature.library.LibraryScreen
import com.aladdin.geniebox.feature.player.PlayerScreen
import com.aladdin.geniebox.feature.settings.SettingsScreen
import com.aladdin.geniebox.feature.setup.SetupScreen
import com.aladdin.geniebox.input.InputMode
import com.aladdin.geniebox.ui.theme.AladdinGenieBoxTheme
import com.aladdin.geniebox.ui.component.BrandIntro

sealed class AppDestination {
    data object Intro : AppDestination()
    data object Setup : AppDestination()
    data object Library : AppDestination()
    data class Player(val game: Game) : AppDestination()
    data object Settings : AppDestination()
    data object Restoring : AppDestination()
}

class MainActivity : ComponentActivity() {

    private val app get() = GenieApplication.instance

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持螢幕長亮，防止電視盒進入休眠
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 設定全螢幕沉浸模式
        hideSystemUi()

        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsState(initial = 0)
            AladdinGenieBoxTheme(themeMode = themeMode) {
                val libraryStateHolder = rememberSaveableStateHolder()
                val sources by app.romSourceRepository.getAllSources().collectAsState(initial = emptyList())
                var setupCompletedOrSkipped by rememberSaveable { mutableStateOf(false) }
                var currentDestination by rememberSaveable { mutableStateOf<String>("DEFAULT") }
                var activeGameId by rememberSaveable { mutableStateOf(app.emulatorSession.activeGame?.id) }
                var activeGame by remember { mutableStateOf(app.emulatorSession.activeGame) }
                var introCompleted by rememberSaveable { mutableStateOf(app.emulatorSession.activeGame != null) }
                var introReady by remember { mutableStateOf(introCompleted) }
                var resumeDirectly by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!introCompleted && !app.settingsRepository.shouldPlayIntro()) {
                        introCompleted = true
                        setupCompletedOrSkipped = true
                        if (currentDestination == "DEFAULT") currentDestination = "LIBRARY"
                    }
                    introReady = true
                }
                LaunchedEffect(activeGameId) {
                    val id = activeGameId
                    if (id == null) {
                        activeGame = null
                    } else {
                        activeGame = app.emulatorSession.activeGame?.takeIf { it.id == id }
                            ?: app.gameRepository.getGameById(id)
                        if (activeGame == null) {
                            activeGameId = null
                            currentDestination = "LIBRARY"
                        }
                    }
                }

                // 首次若無來源且未跳過，顯示 Setup；否則顯示 Library
                val isSetupNeeded = sources.isEmpty() && !setupCompletedOrSkipped
                val targetDestination = when {
                    !introCompleted -> AppDestination.Intro
                    currentDestination == "SETUP" -> AppDestination.Setup
                    isSetupNeeded && currentDestination == "DEFAULT" -> AppDestination.Setup
                    currentDestination == "SETTINGS" -> AppDestination.Settings
                    currentDestination == "PLAYER" && activeGame != null -> AppDestination.Player(activeGame!!)
                    currentDestination == "PLAYER" && activeGameId != null -> AppDestination.Restoring
                    else -> AppDestination.Library
                }
                val entryAlpha = remember { Animatable(1f) }
                LaunchedEffect(targetDestination) {
                    if (targetDestination is AppDestination.Player || targetDestination is AppDestination.Intro) {
                        entryAlpha.snapTo(1f)
                    } else {
                        entryAlpha.snapTo(0f)
                        entryAlpha.animateTo(1f, tween(180))
                    }
                }

                // 同步輸入模式 (Finding 11)
                LaunchedEffect(targetDestination) {
                    when (targetDestination) {
                        is AppDestination.Player -> app.inputManager.setInputMode(
                            if (app.emulatorSession.sessionState.value is EmulationSessionState.Paused)
                                InputMode.PAUSE_MENU else InputMode.IN_GAME
                        )
                        else -> app.inputManager.setInputMode(InputMode.UI_NAVIGATION)
                    }
                }

                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (targetDestination is AppDestination.Player) 1f else entryAlpha.value
                }) {
                when (targetDestination) {
                    is AppDestination.Intro -> if (introReady) BrandIntro(onFinished = {
                        introCompleted = true
                        if (currentDestination == "DEFAULT" && activeGameId == null) {
                            setupCompletedOrSkipped = true
                            currentDestination = "LIBRARY"
                        }
                    })
                    is AppDestination.Setup -> {
                        SetupScreen(
                            onSetupComplete = {
                                setupCompletedOrSkipped = true
                                currentDestination = "LIBRARY"
                            }
                        )
                    }
                    is AppDestination.Library -> {
                        libraryStateHolder.SaveableStateProvider("library") {
                        LibraryScreen(
                            onGameSelect = { game ->
                                resumeDirectly = false
                                activeGame = game
                                activeGameId = game.id
                                currentDestination = "PLAYER"
                            },
                            onContinueGame = { game ->
                                resumeDirectly = true
                                activeGame = game
                                activeGameId = game.id
                                currentDestination = "PLAYER"
                            },
                            onNavigateSettings = {
                                currentDestination = "SETTINGS"
                            },
                            onNavigateWelcome = {
                                currentDestination = "SETUP"
                            }
                        )
                    }
                    }
                    is AppDestination.Player -> {
                        PlayerScreen(
                            game = targetDestination.game,
                            resumeDirectly = resumeDirectly,
                            onExitGame = {
                                activeGame = null
                                activeGameId = null
                                currentDestination = "LIBRARY"
                            }
                        )
                    }
                    is AppDestination.Settings -> {
                        SettingsScreen(
                            onNavigateBack = {
                                currentDestination = "LIBRARY"
                            }
                        )
                    }
                    is AppDestination.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.emulatorSession.enterForeground()
        hideSystemUi()
        // 回到前景時自動核對外接儲存設備狀態 (Finding 14)
        app.storageCoordinator.checkSourcesAvailability(autoScanNewSources = true)
    }

    override fun onPause() {
        super.onPause()
        // 切到背景時自動暫停模擬與音訊，釋放手把按鍵 (Finding 10)
        app.emulatorSession.enterBackground()
        app.inputManager.releaseAllInputs()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
        } else {
            app.inputManager.releaseAllInputs()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = app.inputManager.handleKeyEvent(event)
        if (handled) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = app.inputManager.handleMotionEvent(event)
        if (handled) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun hideSystemUi() {
        val isTvDevice = app.settingsRepository.isTvDevice()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                if (isTvDevice) {
                    controller.hide(WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isTvDevice) (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            ) else (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
