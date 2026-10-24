package com.aladdin.geniebox.input

import android.content.Context
import android.hardware.input.InputManager as AndroidInputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.aladdin.geniebox.domain.model.ControllerProfile
import com.aladdin.geniebox.emulator.NativeEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.aladdin.geniebox.data.repository.SettingsRepository
import kotlin.math.abs

enum class InputMode {
    UI_NAVIGATION, // 在首頁、設定等頁面，按鍵完全交給 Compose 導航，並觸發 TV 快捷鍵
    IN_GAME,        // 遊戲運行中，按鍵傳送至原生模擬核心
    PAUSE_MENU     // 暫停選單中，僅 P1 操作 UI，其餘玩家事件阻斷
}

interface UiShortcutListener {
    fun onToggleFavorite() {}
    fun onFocusSearch() {}
    fun onPreviousCategory() {}
    fun onNextCategory() {}
}

/**
 * 抽象玩家事件轉發介面 (符合規格第 10.2 節：預留本機 InputDevice 與 LAN 輸入 adapter 共用介面)
 */
interface PlayerInputSourceAdapter {
    fun sendButton(playerId: Int, retroKey: Int, pressed: Boolean)
    fun sendAnalog(playerId: Int, axis: Int, value: Float)
}

class InputManager(
    private val context: Context,
    private val nativeEmulator: NativeEmulator
) : AndroidInputManager.InputDeviceListener, PlayerInputSourceAdapter {

    data class PlayerSlot(
        val playerId: Int, // 1, 2, 3, 4
        var deviceId: Int = -1,
        var descriptor: String = "",
        var deviceName: String = "",
        var isConnected: Boolean = false,
        var digitalButtons: Int = 0,
        var analogLeftX: Short = 0,
        var analogLeftY: Short = 0,
        var analogRightX: Short = 0,
        var analogRightY: Short = 0
    )

    private val androidInputManager = context.getSystemService(Context.INPUT_SERVICE) as AndroidInputManager
    private val slots = Array(4) { i -> PlayerSlot(playerId = i + 1) }

    private val _playerProfiles = MutableStateFlow<List<ControllerProfile>>(emptyList())
    val playerProfiles: StateFlow<List<ControllerProfile>> = _playerProfiles.asStateFlow()
    private val settings = SettingsRepository(context)
    private val _disconnections = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val disconnections = _disconnections.asSharedFlow()
    private var mappingPlayerId: Int? = null
    private var mappingRetroKey: Int? = null
    private var mappingCompleted: (() -> Unit)? = null
    private val controllerMaps = mutableMapOf<Pair<String, Int>, Map<Int, Int>>()
    var introSkipHandler: (() -> Unit)? = null
    var gameShortcutHandler: ((Int) -> Unit)? = null
    private val shortcutKeys = mutableSetOf<Pair<Int, Int>>()

    fun beginButtonMapping(playerId: Int, retroKey: Int, onComplete: () -> Unit) {
        releaseAllInputs()
        mappingPlayerId = playerId
        mappingRetroKey = retroKey
        mappingCompleted = onComplete
    }

    fun cancelButtonMapping() {
        mappingPlayerId = null
        mappingRetroKey = null
        mappingCompleted = null
    }

    fun resetButtonMapping(playerId: Int) {
        val slot = slots.getOrNull(playerId - 1) ?: return
        settings.resetControllerMapping(slot.descriptor, playerId)
        controllerMaps.remove(slot.descriptor to playerId)
        releaseAllInputs()
    }

    private fun keyMap(slot: PlayerSlot): Map<Int, Int> =
        controllerMaps.getOrPut(slot.descriptor to slot.playerId) {
            settings.controllerMapping(slot.descriptor, slot.playerId).ifEmpty { DefaultControllerProfile.DEFAULT_KEY_MAP }
        }

    fun mappedButtonName(playerId: Int, retroKey: Int): String {
        val slot = slots.getOrNull(playerId - 1) ?: return "—"
        val key = keyMap(slot).entries.firstOrNull { it.value == retroKey }?.key ?: return "—"
        return KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")
    }

    var inputMode: InputMode = InputMode.UI_NAVIGATION
        private set

    var uiShortcutListener: UiShortcutListener? = null
    private var deadzone = 0.20f

    init {
        androidInputManager.registerInputDeviceListener(this, null)
        refreshConnectedDevices()
    }

    fun setInputMode(mode: InputMode) {
        inputMode = mode
        if (mode != InputMode.IN_GAME) {
            // 切換至非遊戲模式時，釋放遊戲中所有按鍵，防卡鍵
            releaseAllInputs()
        }
    }

    fun setMenuIntercept(active: Boolean) {
        setInputMode(if (active) InputMode.PAUSE_MENU else InputMode.IN_GAME)
    }

    fun refreshConnectedDevices() {
        val deviceIds = androidInputManager.inputDeviceIds
        val foundDevices = mutableListOf<InputDevice>()

        for (id in deviceIds) {
            val dev = androidInputManager.getInputDevice(id) ?: continue
            val sources = dev.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                foundDevices.add(dev)
            }
        }

        // 自動分配尚未指派的手把到可用槽位
        for (dev in foundDevices) {
            val assigned = slots.firstOrNull { it.deviceId == dev.id }
            if (assigned != null) {
                assigned.descriptor = dev.descriptor
                assigned.deviceName = dev.name ?: ""
                assigned.isConnected = true
            } else {
                val preferred = settings.preferredPlayer(dev.descriptor)?.let { slots[it - 1] }
                val emptySlot = preferred?.takeIf { !it.isConnected || it.deviceId == -1 }
                    ?: slots.firstOrNull { !it.isConnected || it.deviceId == -1 }
                if (emptySlot != null) {
                    emptySlot.deviceId = dev.id
                    emptySlot.descriptor = dev.descriptor
                    emptySlot.deviceName = dev.name ?: ""
                    emptySlot.isConnected = true
                }
            }
        }

        updateProfilesState()
    }

    /**
     * 手動指派或重新排序玩家手把
     */
    fun assignDeviceToPlayer(playerId: Int, deviceId: Int) {
        if (playerId !in 1..4) return
        val slot = slots[playerId - 1]
        val dev = androidInputManager.getInputDevice(deviceId)
        if (dev != null) {
            releaseAllInputs()
            slots.filter { it !== slot && it.deviceId == deviceId }.forEach {
                it.deviceId = -1; it.isConnected = false; it.descriptor = ""; it.deviceName = ""
            }
            slot.deviceId = dev.id
            slot.descriptor = dev.descriptor
            slot.deviceName = dev.name ?: ""
            slot.isConnected = true
            settings.setPreferredPlayer(dev.descriptor, playerId)
        } else {
            slot.deviceId = -1
            slot.descriptor = ""
            slot.deviceName = ""
            slot.isConnected = false
        }
        updateProfilesState()
    }

    /**
     * 按鍵加入機制 (Press Button to Join): 解決同型號 descriptor 相同無法區分之情境
     */
    fun handlePressToJoin(deviceId: Int): Int? {
        val existingSlot = slots.firstOrNull { it.deviceId == deviceId }
        if (existingSlot != null) return existingSlot.playerId

        val dev = androidInputManager.getInputDevice(deviceId) ?: return null
        val nextFree = slots.firstOrNull { !it.isConnected } ?: return null
        nextFree.deviceId = dev.id
        nextFree.descriptor = dev.descriptor
        nextFree.deviceName = dev.name ?: ""
        nextFree.isConnected = true
        updateProfilesState()
        return nextFree.playerId
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        introSkipHandler?.let { skip ->
            if (KeyEvent.isGamepadButton(event.keyCode) || event.keyCode in listOf(
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK)) {
                if (event.action == KeyEvent.ACTION_UP) skip()
                return true
            }
        }
        val deviceId = event.deviceId
        val slot = slots.firstOrNull { it.deviceId == deviceId } ?: slots[0]
        val playerIdx = slot.playerId - 1
        val eventKey = deviceId to event.keyCode
        if (eventKey in shortcutKeys) {
            if (event.action == KeyEvent.ACTION_UP) shortcutKeys.remove(eventKey)
            return true
        }
        if (inputMode == InputMode.IN_GAME && slot.playerId == 1 && gameShortcutHandler != null) {
            val selectHeld = slot.digitalButtons and (1 shl RetroPadKey.SELECT) != 0
            val shortcut = when {
                event.keyCode == KeyEvent.KEYCODE_F5 -> 0
                event.keyCode == KeyEvent.KEYCODE_F8 -> 1
                event.keyCode == KeyEvent.KEYCODE_F6 -> 2
                selectHeld && keyMap(slot)[event.keyCode] == RetroPadKey.R -> 0
                selectHeld && keyMap(slot)[event.keyCode] == RetroPadKey.L -> 1
                selectHeld && keyMap(slot)[event.keyCode] == RetroPadKey.L2 -> 2
                else -> null
            }
            if (shortcut != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                shortcutKeys += eventKey
                releaseAllInputs()
                gameShortcutHandler?.invoke(shortcut)
                return true
            }
        }

        if (mappingPlayerId != null && KeyEvent.isGamepadButton(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                slot.playerId == mappingPlayerId && slot.deviceId == deviceId && slot.isConnected) {
                val target = mappingRetroKey ?: return true
                val mapping = keyMap(slot).toMutableMap()
                val previousKey = mapping.entries.firstOrNull { it.value == target }?.key
                val previousTarget = mapping[event.keyCode]
                mapping.entries.removeAll { it.value == target }
                mapping[event.keyCode] = target
                if (previousKey != null && previousKey != event.keyCode && previousTarget != null) {
                    mapping[previousKey] = previousTarget
                }
                settings.setControllerMapping(slot.descriptor, slot.playerId, mapping)
                controllerMaps[slot.descriptor to slot.playerId] = mapping
                val complete = mappingCompleted
                cancelButtonMapping()
                complete?.invoke()
            }
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // 返回鍵交由 Activity 或 BackHandler 統一處理
            return false
        }

        when (inputMode) {
            InputMode.UI_NAVIGATION -> {
                // TV 首頁/設定導航模式：處理 UI 快捷鍵 (Y 收藏, X 搜尋, L1/R1 切換分頁)
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            uiShortcutListener?.onToggleFavorite()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            uiShortcutListener?.onFocusSearch()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            uiShortcutListener?.onPreviousCategory()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            uiShortcutListener?.onNextCategory()
                            return true
                        }
                    }
                }
                // D-Pad, A (確認), B (取消) 等一律回傳 false，讓 Compose 焦點系統正常導航！
                return false
            }
            InputMode.PAUSE_MENU -> {
                // 暫停選單模式：僅允許 P1 控制 UI (回傳 false)，其餘玩家事件全面阻斷 (回傳 true)
                if (slot.playerId == 1) {
                    return false
                }
                return true
            }
            InputMode.IN_GAME -> {
                // 遊戲運行中模式：將事件映射為 RetroPad 狀態傳送給原生模擬核心
                val retroKey = keyMap(slot)[event.keyCode] ?: return false
                val isDown = (event.action == KeyEvent.ACTION_DOWN)

                if (isDown) {
                    slot.digitalButtons = slot.digitalButtons or (1 shl retroKey)
                } else if (event.action == KeyEvent.ACTION_UP) {
                    slot.digitalButtons = slot.digitalButtons and (1 shl retroKey).inv()
                }

                flushPlayerInput(playerIdx)
                return true
            }
        }
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (inputMode != InputMode.IN_GAME) {
            // 非遊戲中模式，讓類比軸向事件交由系統轉換為焦點滾動
            return false
        }

        val deviceId = event.deviceId
        val slot = slots.firstOrNull { it.deviceId == deviceId } ?: slots[0]
        val playerIdx = slot.playerId - 1

        val lx = filterDeadzone(event.getAxisValue(MotionEvent.AXIS_X))
        val ly = filterDeadzone(event.getAxisValue(MotionEvent.AXIS_Y))
        val rx = filterDeadzone(event.getAxisValue(MotionEvent.AXIS_Z))
        val ry = filterDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ))

        slot.analogLeftX = (lx * 32767).toInt().toShort()
        slot.analogLeftY = (ly * 32767).toInt().toShort()
        slot.analogRightX = (rx * 32767).toInt().toShort()
        slot.analogRightY = (ry * 32767).toInt().toShort()

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        var hatButtons = 0
        if (hatX < -0.5f) hatButtons = hatButtons or (1 shl RetroPadKey.LEFT)
        if (hatX > 0.5f) hatButtons = hatButtons or (1 shl RetroPadKey.RIGHT)
        if (hatY < -0.5f) hatButtons = hatButtons or (1 shl RetroPadKey.UP)
        if (hatY > 0.5f) hatButtons = hatButtons or (1 shl RetroPadKey.DOWN)

        val hatMask = (1 shl RetroPadKey.LEFT) or (1 shl RetroPadKey.RIGHT) or
                      (1 shl RetroPadKey.UP) or (1 shl RetroPadKey.DOWN)
        slot.digitalButtons = (slot.digitalButtons and hatMask.inv()) or hatButtons

        flushPlayerInput(playerIdx)
        return true
    }

    private fun filterDeadzone(value: Float): Float {
        return if (abs(value) < deadzone) 0.0f else value
    }

    private fun flushPlayerInput(port: Int) {
        if (port in 0..3) {
            val s = slots[port]
            nativeEmulator.nativeUpdateInput(
                port = port,
                buttons = s.digitalButtons,
                lx = s.analogLeftX,
                ly = s.analogLeftY,
                rx = s.analogRightX,
                ry = s.analogRightY,
                connected = s.isConnected
            )
        }
    }

    fun releaseAllInputs() {
        for (s in slots) {
            s.digitalButtons = 0
            s.analogLeftX = 0
            s.analogLeftY = 0
            s.analogRightX = 0
            s.analogRightY = 0
        }
        nativeEmulator.nativeReleaseAllInputs()
    }

    // AndroidInputManager.InputDeviceListener 回呼
    override fun onInputDeviceAdded(deviceId: Int) {
        refreshConnectedDevices()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val slot = slots.firstOrNull { it.deviceId == deviceId }
        if (slot != null) {
            val wasPlaying = inputMode == InputMode.IN_GAME
            slot.isConnected = false
            slot.digitalButtons = 0
            slot.analogLeftX = 0
            slot.analogLeftY = 0
            slot.analogRightX = 0
            slot.analogRightY = 0
            flushPlayerInput(slot.playerId - 1)
            if (wasPlaying) _disconnections.tryEmit(slot.playerId)
        }
        refreshConnectedDevices()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshConnectedDevices()
    }

    override fun sendButton(playerId: Int, retroKey: Int, pressed: Boolean) {
        if (playerId !in 1..4) return
        val slot = slots[playerId - 1]
        slot.isConnected = true
        if (pressed) {
            slot.digitalButtons = slot.digitalButtons or (1 shl retroKey)
        } else {
            slot.digitalButtons = slot.digitalButtons and (1 shl retroKey).inv()
        }
        flushPlayerInput(playerId - 1)
    }

    override fun sendAnalog(playerId: Int, axis: Int, value: Float) {
        if (playerId !in 1..4) return
        val slot = slots[playerId - 1]
        slot.isConnected = true
        val clamped = (value.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        when (axis) {
            RetroPadKey.ANALOG_LEFT_X -> slot.analogLeftX = clamped
            RetroPadKey.ANALOG_LEFT_Y -> slot.analogLeftY = clamped
            RetroPadKey.ANALOG_RIGHT_X -> slot.analogRightX = clamped
            RetroPadKey.ANALOG_RIGHT_Y -> slot.analogRightY = clamped
        }
        flushPlayerInput(playerId - 1)
    }

    private fun updateProfilesState() {
        _playerProfiles.value = slots.map {
            ControllerProfile(
                playerId = it.playerId,
                deviceDescriptor = it.descriptor,
                deviceName = it.deviceName,
                inputDeviceId = it.deviceId,
                isConnected = it.isConnected,
                deadzone = deadzone
            )
        }
    }
}
