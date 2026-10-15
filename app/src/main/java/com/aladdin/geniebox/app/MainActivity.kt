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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.feature.library.LibraryScreen
import com.aladdin.geniebox.feature.player.PlayerScreen
import com.aladdin.geniebox.feature.settings.SettingsScreen
import com.aladdin.geniebox.feature.setup.SetupScreen
import com.aladdin.geniebox.input.InputMode
import com.aladdin.geniebox.ui.theme.AladdinGenieBoxTheme

sealed class AppDestination {
    data object Setup : AppDestination()
    data object Library : AppDestination()
    data class Player(val game: Game) : AppDestination()
    data object Settings : AppDestination()
}

class MainActivity : ComponentActivity() {

    private val app get() = GenieApplication.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持螢幕長亮，防止電視盒進入休眠
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 設定全螢幕沉浸模式
        hideSystemUi()

        setContent {
            AladdinGenieBoxTheme {
                val sources by app.romSourceRepository.getAllSources().collectAsState(initial = emptyList())
                var setupCompletedOrSkipped by rememberSaveable { mutableStateOf(false) }
                var currentDestination by rememberSaveable { mutableStateOf<String>("DEFAULT") }
                var activeGame by remember { mutableStateOf<Game?>(null) }

                // 首次若無來源且未跳過，顯示 Setup；否則顯示 Library
                val isSetupNeeded = sources.isEmpty() && !setupCompletedOrSkipped
                val targetDestination = when {
                    isSetupNeeded && currentDestination == "DEFAULT" -> AppDestination.Setup
                    currentDestination == "SETTINGS" -> AppDestination.Settings
                    currentDestination == "PLAYER" && activeGame != null -> AppDestination.Player(activeGame!!)
                    else -> AppDestination.Library
                }

                // 同步輸入模式 (Finding 11)
                LaunchedEffect(targetDestination) {
                    when (targetDestination) {
                        is AppDestination.Player -> app.inputManager.setInputMode(InputMode.IN_GAME)
                        else -> app.inputManager.setInputMode(InputMode.UI_NAVIGATION)
                    }
                }

                when (targetDestination) {
                    is AppDestination.Setup -> {
                        SetupScreen(
                            onSetupComplete = {
                                setupCompletedOrSkipped = true
                                currentDestination = "LIBRARY"
                            }
                        )
                    }
                    is AppDestination.Library -> {
                        LibraryScreen(
                            onGameSelect = { game ->
                                activeGame = game
                                currentDestination = "PLAYER"
                            },
                            onNavigateSettings = {
                                currentDestination = "SETTINGS"
                            }
                        )
                    }
                    is AppDestination.Player -> {
                        PlayerScreen(
                            game = targetDestination.game,
                            onExitGame = {
                                activeGame = null
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
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        // 回到前景時自動核對外接儲存設備狀態 (Finding 14)
        app.storageCoordinator.checkSourcesAvailability(autoScanNewSources = false)
    }

    override fun onPause() {
        super.onPause()
        // 切到背景時自動暫停模擬與音訊，釋放手把按鍵 (Finding 10)
        app.emulatorSession.pause()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
