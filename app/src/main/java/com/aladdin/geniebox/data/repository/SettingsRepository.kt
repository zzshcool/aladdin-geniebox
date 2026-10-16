package com.aladdin.geniebox.data.repository

import android.content.Context
import com.aladdin.geniebox.domain.model.PlatformType
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "aladdin_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val GAME_SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
        val KEY_ASPECT_RATIO_MODE = intPreferencesKey("aspect_ratio_mode") // 0: Original, 1: Integer, 2: Stretch
        val KEY_BILINEAR_FILTER = booleanPreferencesKey("bilinear_filter")
        val KEY_SHOW_FPS_HUD = booleanPreferencesKey("show_fps_hud")
        val KEY_TOUCH_OPACITY = floatPreferencesKey("touch_opacity")
        val KEY_TOUCH_SCALE = floatPreferencesKey("touch_scale")
        val KEY_TOUCH_VIBRATION = booleanPreferencesKey("touch_vibration")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_VIRTUAL_GAMEPAD_MODE = intPreferencesKey("virtual_gamepad_mode") // 0: Auto, 1: Always Show, 2: Always Hide
        val KEY_DEVICE_UI_MODE = intPreferencesKey("device_ui_mode") // 0: Auto Detect, 1: Mobile Mode, 2: TV Mode
        val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
    }

    val aspectRatioMode: Flow<Int> = context.dataStore.data.map { it[KEY_ASPECT_RATIO_MODE] ?: 0 }
    val bilinearFilter: Flow<Boolean> = context.dataStore.data.map { it[KEY_BILINEAR_FILTER] ?: false }
    val showFpsHud: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_FPS_HUD] ?: false }
    val touchOpacity: Flow<Float> = context.dataStore.data.map { it[KEY_TOUCH_OPACITY] ?: 0.65f }
    val touchScale: Flow<Float> = context.dataStore.data.map { it[KEY_TOUCH_SCALE] ?: 1.0f }
    val touchVibration: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOUCH_VIBRATION] ?: true }
    val autoResume: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RESUME] ?: true }
    val muteFastForward: Flow<Boolean> = context.dataStore.data.map {
        it[booleanPreferencesKey("mute_fast_forward")] ?: true
    }

    suspend fun setMuteFastForward(enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("mute_fast_forward")] = enabled }
    }
    suspend fun setAutoResume(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RESUME] = enabled }
    }
    fun gameSpeed(gameId: String): Flow<Float> = context.dataStore.data.map {
        (it[floatPreferencesKey("game_speed_$gameId")] ?: 1f).takeIf { speed -> speed in GAME_SPEEDS } ?: 1f
    }

    suspend fun setGameSpeed(gameId: String, speed: Float) {
        require(speed in GAME_SPEEDS)
        context.dataStore.edit { it[floatPreferencesKey("game_speed_$gameId")] = speed }
    }

    private fun controllerMappingKey(descriptor: String, playerId: Int) = "${playerId}_$descriptor"

    fun controllerMapping(descriptor: String, playerId: Int): Map<Int, Int> {
        val value = context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE)
            .getString(controllerMappingKey(descriptor, playerId), null) ?: return emptyMap()
        val json = org.json.JSONObject(value)
        return json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
    }

    fun setControllerMapping(descriptor: String, playerId: Int, mapping: Map<Int, Int>) {
        val json = org.json.JSONObject()
        mapping.forEach { (key, value) -> json.put(key.toString(), value) }
        context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE).edit()
            .putString(controllerMappingKey(descriptor, playerId), json.toString()).apply()
    }

    fun resetControllerMapping(descriptor: String, playerId: Int) {
        context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE).edit()
            .remove(controllerMappingKey(descriptor, playerId)).apply()
    }
    val virtualGamepadMode: Flow<Int> = context.dataStore.data.map { it[KEY_VIRTUAL_GAMEPAD_MODE] ?: 0 }
    val deviceUiMode: Flow<Int> = context.dataStore.data.map { it[KEY_DEVICE_UI_MODE] ?: 0 }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }

    suspend fun setThemeMode(mode: Int) {
        require(mode in 0..2)
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

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

    private fun platformFloat(key: Preferences.Key<Float>, platform: PlatformType, fallback: Float): Flow<Float> =
        context.dataStore.data.map { values ->
            values[floatPreferencesKey("${key.name}_${platform.id}")] ?: values[key] ?: fallback
        }

    fun touchOpacity(platform: PlatformType): Flow<Float> = platformFloat(KEY_TOUCH_OPACITY, platform, 0.65f)
    fun touchScale(platform: PlatformType): Flow<Float> = platformFloat(KEY_TOUCH_SCALE, platform, 1f)
    fun touchEdgeInset(platform: PlatformType): Flow<Float> =
        context.dataStore.data.map { it[floatPreferencesKey("touch_edge_${platform.id}")] ?: 8f }
    fun touchBottomInset(platform: PlatformType): Flow<Float> =
        context.dataStore.data.map { it[floatPreferencesKey("touch_bottom_${platform.id}")] ?: 12f }
    fun touchVibration(platform: PlatformType): Flow<Boolean> =
        context.dataStore.data.map {
            it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] ?: it[KEY_TOUCH_VIBRATION] ?: true
        }

    suspend fun setTouchOpacity(platform: PlatformType, value: Float) {
        require(value in 0.2f..1f)
        context.dataStore.edit { it[floatPreferencesKey("${KEY_TOUCH_OPACITY.name}_${platform.id}")] = value }
    }

    suspend fun setTouchScale(platform: PlatformType, value: Float) {
        require(value in 0.75f..1.4f)
        context.dataStore.edit { it[floatPreferencesKey("${KEY_TOUCH_SCALE.name}_${platform.id}")] = value }
    }

    suspend fun setTouchPosition(platform: PlatformType, edge: Float, bottom: Float) {
        require(edge in 0f..64f && bottom in 0f..72f)
        context.dataStore.edit {
            it[floatPreferencesKey("touch_edge_${platform.id}")] = edge
            it[floatPreferencesKey("touch_bottom_${platform.id}")] = bottom
        }
    }

    suspend fun setTouchVibration(platform: PlatformType, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] = enabled }
    }

    suspend fun resetTouchControls(platform: PlatformType) {
        context.dataStore.edit {
            it[floatPreferencesKey("${KEY_TOUCH_OPACITY.name}_${platform.id}")] = 0.65f
            it[floatPreferencesKey("${KEY_TOUCH_SCALE.name}_${platform.id}")] = 1f
            it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] = true
            it[floatPreferencesKey("touch_edge_${platform.id}")] = 8f
            it[floatPreferencesKey("touch_bottom_${platform.id}")] = 12f
        }
    }

    fun isTvDevice(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTelevision = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return isTelevision || hasLeanback
    }
}
