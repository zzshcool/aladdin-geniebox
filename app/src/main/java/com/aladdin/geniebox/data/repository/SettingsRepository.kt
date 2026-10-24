package com.aladdin.geniebox.data.repository

import android.content.Context
import com.aladdin.geniebox.domain.model.PlatformType
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged

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
        private val KEY_VISIBLE_GAMEPAD_DEFAULT_INITIALIZED = booleanPreferencesKey("virtual_gamepad_visible_default_initialized")
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

    fun nesReduceSlowdown(gameId: String): Flow<Boolean> = context.dataStore.data.map {
        it[booleanPreferencesKey("nes_reduce_slowdown_$gameId")] ?: false
    }

    suspend fun setNesReduceSlowdown(gameId: String, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("nes_reduce_slowdown_$gameId")] = enabled }
    }

    private fun controllerMappingKey(descriptor: String, playerId: Int) = "${playerId}_$descriptor"

    fun controllerMapping(descriptor: String, playerId: Int): Map<Int, Int> {
        val value = context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE)
            .let { it.getString(controllerMappingKey(descriptor, playerId), null)
                ?: it.getString("device_$descriptor", null) } ?: return emptyMap()
        val json = org.json.JSONObject(value)
        return json.keys().asSequence().associate { it.toInt() to json.getInt(it) }
    }

    fun setControllerMapping(descriptor: String, playerId: Int, mapping: Map<Int, Int>) {
        val json = org.json.JSONObject()
        mapping.forEach { (key, value) -> json.put(key.toString(), value) }
        context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE).edit()
            .putString(controllerMappingKey(descriptor, playerId), json.toString())
            .putString("device_$descriptor", json.toString()).apply()
    }

    fun resetControllerMapping(descriptor: String, playerId: Int) {
        context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE).edit()
            .remove(controllerMappingKey(descriptor, playerId)).remove("device_$descriptor").apply()
    }
    val virtualGamepadMode: Flow<Int> = context.dataStore.data.map { values ->
        val mode = values[KEY_VIRTUAL_GAMEPAD_MODE] ?: 1
        if (values[KEY_VISIBLE_GAMEPAD_DEFAULT_INITIALIZED] != true && mode != 2) 1 else mode
    }
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

    suspend fun initializeVirtualGamepadDefault() {
        context.dataStore.edit { values ->
            if (values[KEY_VISIBLE_GAMEPAD_DEFAULT_INITIALIZED] != true) {
                if (values[KEY_VIRTUAL_GAMEPAD_MODE] != 2) values[KEY_VIRTUAL_GAMEPAD_MODE] = 1
                values[KEY_VISIBLE_GAMEPAD_DEFAULT_INITIALIZED] = true
            }
        }
    }

    suspend fun setDeviceUiMode(mode: Int) {
        context.dataStore.edit { it[KEY_DEVICE_UI_MODE] = mode }
    }

    private fun platformFloat(key: Preferences.Key<Float>, platform: PlatformType, fallback: Float): Flow<Float> =
        context.dataStore.data.map { values ->
            values[floatPreferencesKey("${key.name}_${platform.id}")] ?: values[key] ?: fallback
        }

    private fun touchKey(name: String, platform: PlatformType, landscape: Boolean?) =
        floatPreferencesKey("${name}_${platform.id}" + when (landscape) {
            true -> "_landscape"
            false -> "_portrait"
            null -> ""
        })

    private fun touchValue(name: String, platform: PlatformType, landscape: Boolean?, fallback: Float): Flow<Float> =
        context.dataStore.data.map {
            it[touchKey(name, platform, landscape)] ?: it[touchKey(name, platform, null)] ?:
                it[floatPreferencesKey(name)] ?: fallback
        }

    fun touchOpacity(platform: PlatformType, landscape: Boolean? = null): Flow<Float> =
        touchValue(KEY_TOUCH_OPACITY.name, platform, landscape, 0.65f)
    fun touchScale(platform: PlatformType, landscape: Boolean? = null): Flow<Float> =
        touchValue(KEY_TOUCH_SCALE.name, platform, landscape, 1f)
    fun touchEdgeInset(platform: PlatformType, landscape: Boolean? = null): Flow<Float> =
        touchValue("touch_edge", platform, landscape, 8f)
    fun touchBottomInset(platform: PlatformType, landscape: Boolean? = null): Flow<Float> =
        touchValue("touch_bottom", platform, landscape, 12f)
    fun touchVibration(platform: PlatformType): Flow<Boolean> =
        context.dataStore.data.map {
            it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] ?: it[KEY_TOUCH_VIBRATION] ?: true
        }

    suspend fun setTouchOpacity(platform: PlatformType, value: Float, landscape: Boolean? = null) {
        require(value in 0.2f..1f)
        context.dataStore.edit { it[touchKey(KEY_TOUCH_OPACITY.name, platform, landscape)] = value }
    }

    suspend fun setTouchScale(platform: PlatformType, value: Float, landscape: Boolean? = null) {
        require(value in 0.75f..1.4f)
        context.dataStore.edit { it[touchKey(KEY_TOUCH_SCALE.name, platform, landscape)] = value }
    }

    suspend fun setTouchPosition(platform: PlatformType, edge: Float, bottom: Float, landscape: Boolean? = null) {
        require(edge in 0f..64f && bottom in 0f..72f)
        context.dataStore.edit {
            it[touchKey("touch_edge", platform, landscape)] = edge
            it[touchKey("touch_bottom", platform, landscape)] = bottom
        }
    }

    suspend fun setTouchVibration(platform: PlatformType, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] = enabled }
    }

    suspend fun resetTouchControls(platform: PlatformType, landscape: Boolean? = null) {
        context.dataStore.edit {
            it[touchKey(KEY_TOUCH_OPACITY.name, platform, landscape)] = 0.65f
            it[touchKey(KEY_TOUCH_SCALE.name, platform, landscape)] = 1f
            it[booleanPreferencesKey("${KEY_TOUCH_VIBRATION.name}_${platform.id}")] = true
            it[touchKey("touch_edge", platform, landscape)] = 8f
            it[touchKey("touch_bottom", platform, landscape)] = 12f
        }
    }

    val introMode: Flow<Int> = context.dataStore.data.map { it[intPreferencesKey("intro_mode")] ?: 0 }
    suspend fun setIntroMode(mode: Int) {
        require(mode in 0..2)
        context.dataStore.edit { it[intPreferencesKey("intro_mode")] = mode }
    }

    suspend fun shouldPlayIntro(): Boolean {
        val values = context.dataStore.data.first()
        val day = java.time.LocalDate.now().toString()
        val mode = values[intPreferencesKey("intro_mode")] ?: 0
        val play = mode == 0 || mode == 1 && values[stringPreferencesKey("intro_day")] != day
        if (play) context.dataStore.edit { it[stringPreferencesKey("intro_day")] = day }
        return play
    }

    fun gameAspectRatio(gameId: String): Flow<Int> = context.dataStore.data.map {
        it[intPreferencesKey("game_aspect_$gameId")] ?: it[KEY_ASPECT_RATIO_MODE] ?: 0
    }
    fun gameFilter(gameId: String): Flow<Boolean> = context.dataStore.data.map {
        it[booleanPreferencesKey("game_filter_$gameId")] ?: it[KEY_BILINEAR_FILTER] ?: false
    }
    suspend fun setGameVideo(gameId: String, aspect: Int, filter: Boolean) {
        require(aspect in 0..2)
        context.dataStore.edit {
            it[intPreferencesKey("game_aspect_$gameId")] = aspect
            it[booleanPreferencesKey("game_filter_$gameId")] = filter
        }
    }
    fun rewindEnabled(gameId: String): Flow<Boolean> = context.dataStore.data.map {
        it[booleanPreferencesKey("rewind_$gameId")] ?: false
    }
    suspend fun setRewindEnabled(gameId: String, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("rewind_$gameId")] = enabled }
    }
    fun frameSkip(gameId: String): Flow<Boolean> = context.dataStore.data.map {
        it[booleanPreferencesKey("frame_skip_$gameId")] ?: false
    }
    suspend fun setFrameSkip(gameId: String, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("frame_skip_$gameId")] = enabled }
    }

    private fun layoutKey(platform: PlatformType, landscape: Boolean, gameId: String?) =
        stringPreferencesKey("control_layout_${gameId ?: platform.id}_${if (landscape) "landscape" else "portrait"}")

    fun controlLayout(platform: PlatformType, landscape: Boolean, gameId: String? = null): Flow<Map<String, Triple<Float, Float, Float>>> =
        context.dataStore.data.map { values ->
            val json = org.json.JSONObject(values[layoutKey(platform, landscape, gameId)]
                ?: values[layoutKey(platform, landscape, null)] ?: "{}")
            json.keys().asSequence().associateWith { key ->
                val a = json.getJSONArray(key)
                Triple(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())
            }
        }

    suspend fun setControlLayout(platform: PlatformType, landscape: Boolean, gameId: String?, layout: Map<String, Triple<Float, Float, Float>>) {
        val json = org.json.JSONObject()
        layout.forEach { (key, value) ->
            json.put(key, org.json.JSONArray(listOf(value.first.coerceIn(0f, 1f),
                value.second.coerceIn(0f, 1f), value.third.coerceIn(0.65f, 1.6f))))
        }
        context.dataStore.edit { it[layoutKey(platform, landscape, gameId)] = json.toString() }
    }
    suspend fun resetControlLayout(platform: PlatformType, landscape: Boolean, gameId: String? = null) {
        context.dataStore.edit { it.remove(layoutKey(platform, landscape, gameId)) }
    }

    val gamePresentation: Flow<Map<String, Pair<String?, String?>>> = context.dataStore.data.map { values ->
        val json = org.json.JSONObject(values[stringPreferencesKey("game_presentation")] ?: "{}")
        json.keys().asSequence().associateWith { key ->
            val item = json.getJSONObject(key)
            item.optString("title").takeIf { it.isNotBlank() } to item.optString("cover").takeIf { it.isNotBlank() }
        }
    }.distinctUntilChanged()
    suspend fun setGamePresentation(gameId: String, title: String?, cover: String?) {
        context.dataStore.edit { values ->
            val key = stringPreferencesKey("game_presentation")
            val json = org.json.JSONObject(values[key] ?: "{}")
            if (title == null && cover == null) json.remove(gameId)
            else json.put(gameId, org.json.JSONObject().put("title", title ?: "").put("cover", cover ?: ""))
            values[key] = json.toString()
        }
    }

    fun preferredPlayer(descriptor: String): Int? = context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE)
        .getInt("preferred_player_$descriptor", 0).takeIf { it in 1..4 }
    fun setPreferredPlayer(descriptor: String, playerId: Int) {
        context.getSharedPreferences("controller_mappings", Context.MODE_PRIVATE).edit()
            .putInt("preferred_player_$descriptor", playerId).apply()
    }

    fun libraryState(): org.json.JSONObject = org.json.JSONObject(
        context.getSharedPreferences("library_navigation", Context.MODE_PRIVATE).getString("state", "{}") ?: "{}")
    fun saveLibraryState(category: String, query: String, sort: Int, index: Int, offset: Int, focused: String?) {
        val json = org.json.JSONObject().put("category", category).put("query", query).put("sort", sort)
            .put("index", index).put("offset", offset).put("focused", focused ?: "")
        context.getSharedPreferences("library_navigation", Context.MODE_PRIVATE).edit().putString("state", json.toString()).apply()
    }

    fun isTvDevice(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTelevision = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return isTelevision || hasLeanback
    }
}
