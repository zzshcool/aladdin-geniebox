package com.aladdin.geniebox.input

import android.view.KeyEvent

object DefaultControllerProfile {
    /**
     * Android KeyEvent -> RetroPad Key Code 預設對照
     */
    val DEFAULT_KEY_MAP: Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to RetroPadKey.B,
        KeyEvent.KEYCODE_BUTTON_B to RetroPadKey.A,
        KeyEvent.KEYCODE_BUTTON_X to RetroPadKey.Y,
        KeyEvent.KEYCODE_BUTTON_Y to RetroPadKey.X,
        KeyEvent.KEYCODE_BUTTON_L1 to RetroPadKey.L,
        KeyEvent.KEYCODE_BUTTON_R1 to RetroPadKey.R,
        KeyEvent.KEYCODE_BUTTON_L2 to RetroPadKey.L2,
        KeyEvent.KEYCODE_BUTTON_R2 to RetroPadKey.R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL to RetroPadKey.L3,
        KeyEvent.KEYCODE_BUTTON_THUMBR to RetroPadKey.R3,
        KeyEvent.KEYCODE_BUTTON_START to RetroPadKey.START,
        KeyEvent.KEYCODE_BUTTON_SELECT to RetroPadKey.SELECT,
        KeyEvent.KEYCODE_DPAD_UP to RetroPadKey.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to RetroPadKey.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to RetroPadKey.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to RetroPadKey.RIGHT
    )
}
