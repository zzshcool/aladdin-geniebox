package com.aladdin.geniebox.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.input.PlayerInputSourceAdapter
import com.aladdin.geniebox.input.RetroPadKey
import com.aladdin.geniebox.ui.theme.AladdinGold
import com.aladdin.geniebox.ui.theme.BgDark
import com.aladdin.geniebox.ui.theme.CardDarkFocused
import kotlin.math.sqrt

@Composable
fun VirtualGamepadOverlay(
    platform: PlatformType,
    adapter: PlayerInputSourceAdapter,
    opacity: Float = 0.65f,
    scale: Float = 1.0f,
    vibrationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val triggerHaptic = {
        if (vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity)
    ) {
        // 頂部肩鍵 (L, R, L2, R2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadButton(label = "L", onStateChange = { p ->
                    if (p) triggerHaptic()
                    adapter.sendButton(1, RetroPadKey.L, p)
                })
                if (platform == PlatformType.PS1) {
                    GamepadButton(label = "L2", onStateChange = { p ->
                        if (p) triggerHaptic()
                        adapter.sendButton(1, RetroPadKey.L2, p)
                    })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (platform == PlatformType.PS1) {
                    GamepadButton(label = "R2", onStateChange = { p ->
                        if (p) triggerHaptic()
                        adapter.sendButton(1, RetroPadKey.R2, p)
                    })
                }
                GamepadButton(label = "R", onStateChange = { p ->
                    if (p) triggerHaptic()
                    adapter.sendButton(1, RetroPadKey.R, p)
                })
            }
        }

        // 左下角：D-Pad 十字鍵或類比搖桿
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 12.dp)
                .size((128 * scale).dp)
        ) {
            VirtualDpad(
                onDirectionChange = { dir, pressed ->
                    if (pressed) triggerHaptic()
                    adapter.sendButton(1, dir, pressed)
                }
            )
        }

        // PSP 專用：類比搖桿滑桿
        if (platform == PlatformType.PSP || platform == PlatformType.PS1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp, bottom = 48.dp)
                    .size((100 * scale).dp)
            ) {
                VirtualAnalogStick(
                    onAnalogChange = { x, y ->
                        adapter.sendAnalog(1, RetroPadKey.ANALOG_LEFT_X, x)
                        adapter.sendAnalog(1, RetroPadKey.ANALOG_LEFT_Y, y)
                    }
                )
            }
        }

        // 中下方：SELECT 與 START
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamepadButton(
                label = "SELECT",
                width = 60,
                height = 32,
                shape = RoundedCornerShape(18.dp),
                onStateChange = { p ->
                    if (p) triggerHaptic()
                    adapter.sendButton(1, RetroPadKey.SELECT, p)
                }
            )
            GamepadButton(
                label = "START",
                width = 60,
                height = 32,
                shape = RoundedCornerShape(18.dp),
                onStateChange = { p ->
                    if (p) triggerHaptic()
                    adapter.sendButton(1, RetroPadKey.START, p)
                }
            )
        }

        // 右下角：動作按鈕群 (ABXY)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 12.dp)
                .size((128 * scale).dp)
        ) {
            VirtualActionButtons(
                platform = platform,
                onButtonChange = { key, pressed ->
                    if (pressed) triggerHaptic()
                    adapter.sendButton(1, key, pressed)
                }
            )
        }
    }
}

@Composable
fun GamepadButton(
    label: String,
    modifier: Modifier = Modifier,
    width: Int = 48,
    height: Int = 48,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onStateChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(width.dp, height.dp)
            .background(
                color = if (isPressed) AladdinGold else CardDarkFocused.copy(alpha = 0.8f),
                shape = shape
            )
            .border(
                width = 1.5.dp,
                color = if (isPressed) Color.White else AladdinGold.copy(alpha = 0.6f),
                shape = shape
            )
            .pointerInput(Unit) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.any {
                                it.pressed && it.position.x >= 0 && it.position.x < size.width &&
                                    it.position.y >= 0 && it.position.y < size.height
                            }
                            if (down != isPressed) {
                                isPressed = down
                                onStateChange(down)
                            }
                        }
                    }
                } finally {
                    if (isPressed) {
                        isPressed = false
                        onStateChange(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) BgDark else Color.White
        )
    }
}

@Composable
fun VirtualDpad(
    onDirectionChange: (key: Int, pressed: Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // UP
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GamepadButton(label = "▲", onStateChange = { onDirectionChange(RetroPadKey.UP, it) })
        }
        // DOWN
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadButton(label = "▼", onStateChange = { onDirectionChange(RetroPadKey.DOWN, it) })
        }
        // LEFT
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            GamepadButton(label = "◀", onStateChange = { onDirectionChange(RetroPadKey.LEFT, it) })
        }
        // RIGHT
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadButton(label = "▶", onStateChange = { onDirectionChange(RetroPadKey.RIGHT, it) })
        }
    }
}

@Composable
fun VirtualActionButtons(
    platform: PlatformType,
    onButtonChange: (key: Int, pressed: Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // A / 東
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadButton(label = "A", onStateChange = { onButtonChange(RetroPadKey.A, it) })
        }
        // B / 南
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadButton(label = "B", onStateChange = { onButtonChange(RetroPadKey.B, it) })
        }

        // NES 與 GB 只有 A、B 兩鍵，其餘平台具備 X、Y 四鍵
        if (platform != PlatformType.NES && platform != PlatformType.GB) {
            // X / 北
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GamepadButton(label = if (platform == PlatformType.PS1) "△" else "X", onStateChange = { onButtonChange(RetroPadKey.X, it) })
            }
            // Y / 西
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                GamepadButton(label = if (platform == PlatformType.PS1) "□" else "Y", onStateChange = { onButtonChange(RetroPadKey.Y, it) })
            }
        }
    }
}

@Composable
fun VirtualAnalogStick(
    onAnalogChange: (x: Float, y: Float) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 40f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardDarkFocused.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, AladdinGold.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onAnalogChange(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onAnalogChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        thumbOffset = if (dist > maxRadius) {
                            Offset(newOffset.x / dist * maxRadius, newOffset.y / dist * maxRadius)
                        } else {
                            newOffset
                        }
                        onAnalogChange(thumbOffset.x / maxRadius, thumbOffset.y / maxRadius)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(thumbOffset.x.dp, thumbOffset.y.dp)
                .size(36.dp)
                .background(AladdinGold, CircleShape)
        )
    }
}
