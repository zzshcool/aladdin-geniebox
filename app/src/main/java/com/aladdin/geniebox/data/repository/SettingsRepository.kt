package com.aladdin.geniebox.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "aladdin_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_ASPECT_RATIO_MODE = intPreferencesKey("aspect_ratio_mode") // 0: Original, 1: Integer, 2: Stretch
        val KEY_BILINEAR_FILTER = booleanPreferencesKey("bilinear_filter")
        val KEY_SHOW_FPS_HUD = booleanPreferencesKey("show_fps_hud")
        val KEY_TOUCH_OPACITY = floatPreferencesKey("touch_opacity")
        val KEY_TOUCH_SCALE = floatPreferencesKey("touch_scale")
        val KEY_TOUCH_VIBRATION = booleanPreferencesKey("touch_vibration")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_VIRTUAL_GAMEPAD_MODE = intPreferencesKey("virtual_gamepad_mode") // 0: Auto, 1: Always Show, 2: Always Hide
        val KEY_DEVICE_UI_MODE = intPreferencesKey("device_ui_mode") // 0: Auto Detect, 1: Mobile Mode, 2: TV Mode
    }

    val aspectRatioMode: Flow<Int> = context.dataStore.data.map { it[KEY_ASPECT_RATIO_MODE] ?: 0 }
    val bilinearFilter: Flow<Boolean> = context.dataStore.data.map { it[KEY_BILINEAR_FILTER] ?: false }
    val showFpsHud: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_FPS_HUD] ?: false }
    val touchOpacity: Flow<Float> = context.dataStore.data.map { it[KEY_TOUCH_OPACITY] ?: 0.65f }
    val touchScale: Flow<Float> = context.dataStore.data.map { it[KEY_TOUCH_SCALE] ?: 1.0f }
    val touchVibration: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOUCH_VIBRATION] ?: true }
    val autoResume: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RESUME] ?: true }
    val virtualGamepadMode: Flow<Int> = context.dataStore.data.map { it[KEY_VIRTUAL_GAMEPAD_MODE] ?: 0 }
    val deviceUiMode: Flow<Int> = context.dataStore.data.map { it[KEY_DEVICE_UI_MODE] ?: 0 }

    suspend fun setAspectRatioMode(mode: Int) {
        context.dataStore.edit { it[KEY_ASPECT_RATIO_MODE] = mode }
    }

    suspend fun setBilinearFilter(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BILINEAR_FILTER] = enabled }
    }

    suspend fun setShowFpsHud(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_FPS_HUD] = show }
    }

    suspend fun setTouchOpacity(opacity: Float) {
        context.dataStore.edit { it[KEY_TOUCH_OPACITY] = opacity }
    }

    suspend fun setTouchScale(scale: Float) {
        context.dataStore.edit { it[KEY_TOUCH_SCALE] = scale }
    }

    suspend fun setTouchVibration(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TOUCH_VIBRATION] = enabled }
    }

    suspend fun setVirtualGamepadMode(mode: Int) {
        context.dataStore.edit { it[KEY_VIRTUAL_GAMEPAD_MODE] = mode }
    }

    suspend fun setDeviceUiMode(mode: Int) {
        context.dataStore.edit { it[KEY_DEVICE_UI_MODE] = mode }
    }

    fun isTvDevice(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTelevision = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return isTelevision || hasLeanback
    }
}
