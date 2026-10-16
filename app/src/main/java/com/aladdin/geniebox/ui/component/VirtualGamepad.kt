package com.aladdin.geniebox.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.input.PlayerInputSourceAdapter
import com.aladdin.geniebox.input.RetroPadKey

private val GamepadAccent = Color(0xFF99C6AB)

private val GamepadInk = Color(0xFF131615)

private val GamepadSurface = Color(0xFF29342E)

@Composable
fun VirtualGamepadOverlay(
    platform: PlatformType,
    adapter: PlayerInputSourceAdapter,
    opacity: Float = 0.65f,
    scale: Float = 1.0f,
    vibrationEnabled: Boolean = true,
    edgeInset: Float = 8f,
    bottomInset: Float = 12f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val triggerHaptic = {
        if (vibrationEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .alpha(opacity)
    ) {
        val clusterSize = minOf(128f * scale, maxHeight.value * 0.42f, (maxWidth.value - 32f) / 2f).coerceAtLeast(64f)
        val sidePadding = edgeInset.coerceIn(0f, ((maxWidth.value - clusterSize * 2f - 16f) / 2f).coerceAtLeast(0f)).dp
        val bottomPadding = bottomInset.coerceIn(0f, (maxHeight.value - clusterSize - 56f).coerceAtLeast(0f)).dp
        // 頂部肩鍵 (L, R, L2, R2)
        if (platform != PlatformType.NES && platform != PlatformType.GB) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sidePadding, vertical = 8.dp)
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
        }

        // 左下角：D-Pad 十字鍵或類比搖桿
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sidePadding, bottom = bottomPadding)
                .size(clusterSize.dp)
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
                    .align(Alignment.TopStart)
                    .padding(start = sidePadding, top = 64.dp)
                    .size(minOf(100f * scale, maxHeight.value * 0.24f).dp)
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
                .padding(end = sidePadding, bottom = bottomPadding)
                .size(clusterSize.dp)
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
    pressedOverride: Boolean? = null,
    interactive: Boolean = true,
    onStateChange: (Boolean) -> Unit
) {
    var touchPressed by remember { mutableStateOf(false) }
    val isPressed = pressedOverride ?: touchPressed
    val currentOnStateChange by rememberUpdatedState(onStateChange)

    Box(
        modifier = modifier
            .size(width.dp, height.dp)
            .background(
                color = if (isPressed) GamepadAccent else GamepadSurface.copy(alpha = 0.8f),
                shape = shape
            )
            .border(
                width = 1.5.dp,
                color = if (isPressed) Color.White else GamepadAccent.copy(alpha = 0.6f),
                shape = shape
            )
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.any {
                                it.pressed && it.position.x >= 0 && it.position.x < size.width &&
                                    it.position.y >= 0 && it.position.y < size.height
                            }
                            if (down != touchPressed) {
                                touchPressed = down
                                currentOnStateChange(down)
                            }
                        }
                    }
                } finally {
                    if (touchPressed) {
                        touchPressed = false
                        currentOnStateChange(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) GamepadInk else Color.White
        )
    }
}

@Composable
fun VirtualDpad(
    onDirectionChange: (key: Int, pressed: Boolean) -> Unit
) {
    VirtualButtonCluster(
        buttons = listOf(RetroPadKey.LEFT to "◀", RetroPadKey.RIGHT to "▶", RetroPadKey.UP to "▲", RetroPadKey.DOWN to "▼"),
        directional = true,
        onButtonChange = onDirectionChange
    )
}

@Composable
fun VirtualActionButtons(
    platform: PlatformType,
    onButtonChange: (key: Int, pressed: Boolean) -> Unit
) {
    val playStation = platform == PlatformType.PS1 || platform == PlatformType.PSP
    val buttons = buildList {
        add(RetroPadKey.A to if (playStation) "○" else "A")
        add(RetroPadKey.B to if (playStation) "×" else "B")
        if (platform == PlatformType.SFC || playStation) {
            add(RetroPadKey.X to if (playStation) "△" else "X")
            add(RetroPadKey.Y to if (playStation) "□" else "Y")
        }
    }
    VirtualButtonCluster(buttons, directional = false, onButtonChange = onButtonChange)
}

@Composable
private fun VirtualButtonCluster(
    buttons: List<Pair<Int, String>>,
    directional: Boolean,
    onButtonChange: (Int, Boolean) -> Unit
) {
    var pressedKeys by remember { mutableStateOf(emptySet<Int>()) }
    val currentOnButtonChange by rememberUpdatedState(onButtonChange)
    val alignments = if (directional) {
        listOf(Alignment.CenterStart, Alignment.CenterEnd, Alignment.TopCenter, Alignment.BottomCenter)
    } else {
        listOf(Alignment.CenterEnd, Alignment.BottomCenter, Alignment.TopCenter, Alignment.CenterStart)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().pointerInput(buttons, directional) {
            val ownedPointers = mutableSetOf<PointerId>()
            val heldActionKeys = mutableMapOf<PointerId, Set<Int>>()
            fun update(keys: Set<Int>) {
                (pressedKeys - keys).forEach { currentOnButtonChange(it, false) }
                (keys - pressedKeys).forEach { currentOnButtonChange(it, true) }
                pressedKeys = keys
            }
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.filter {
                            it.pressed && !it.previousPressed &&
                                it.position.x >= 0 && it.position.x < size.width &&
                                it.position.y >= 0 && it.position.y < size.height
                        }.forEach { ownedPointers += it.id }
                        val changes = event.changes.filter { it.id in ownedPointers }
                        val points = changes.filter { it.pressed }.map { it.position }
                        val keys = mutableSetOf<Int>()
                        if (directional) {
                            points.firstOrNull()?.let { point ->
                                val x = (point.x - size.width / 2f) / (size.width / 2f)
                                val y = (point.y - size.height / 2f) / (size.height / 2f)
                                if (x * x + y * y > 0.08f) {
                                    if (abs(x) >= abs(y) * 0.4142f) keys += if (x < 0) RetroPadKey.LEFT else RetroPadKey.RIGHT
                                    if (abs(y) >= abs(x) * 0.4142f) keys += if (y < 0) RetroPadKey.UP else RetroPadKey.DOWN
                                }
                            }
                        } else {
                            val diameter = minOf(size.width, size.height) * 0.4f
                            val radius = diameter * 0.62f
                            val centers = listOf(
                                Offset(size.width - diameter / 2f, size.height / 2f),
                                Offset(size.width / 2f, size.height - diameter / 2f),
                                Offset(size.width / 2f, diameter / 2f),
                                Offset(diameter / 2f, size.height / 2f)
                            )
                            changes.filter { it.pressed }.forEach { change ->
                                val touchedKeys = buttons.mapIndexedNotNull { index, button ->
                                    val delta = change.position - centers[index]
                                    button.first.takeIf { delta.getDistance() <= radius }
                                }.toSet()
                                if (!change.previousPressed) {
                                    heldActionKeys[change.id] = touchedKeys
                                }
                                // Keep the original press until this finger lifts; rolling onto
                                // another button must not release a held charge button.
                                keys += heldActionKeys[change.id].orEmpty()
                                keys += touchedKeys
                            }
                        }
                        update(keys)
                        changes.forEach { it.consume() }
                        changes.filter { !it.pressed }.forEach {
                            ownedPointers -= it.id
                            heldActionKeys.remove(it.id)
                        }
                    }
                }
            } finally {
                update(emptySet())
            }
        }
    ) {
        val diameter = (minOf(maxWidth.value, maxHeight.value) * 0.4f).roundToInt()
        buttons.forEachIndexed { index, (key, label) ->
            Box(modifier = Modifier.align(alignments[index])) {
                GamepadButton(
                    label = label,
                    width = diameter,
                    height = diameter,
                    pressedOverride = key in pressedKeys,
                    interactive = false,
                    onStateChange = {}
                )
            }
        }
    }
}

@Composable
fun VirtualAnalogStick(
    onAnalogChange: (x: Float, y: Float) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val currentOnAnalogChange by rememberUpdatedState(onAnalogChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadSurface.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, GamepadAccent.copy(alpha = 0.4f), CircleShape)
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    val radius = (minOf(size.width, size.height) / 2f - 18.dp.toPx()).coerceAtLeast(1f)
                    val delta = position - Offset(size.width / 2f, size.height / 2f)
                    val distance = delta.getDistance()
                    thumbOffset = if (distance > radius) delta * (radius / distance) else delta
                    currentOnAnalogChange(thumbOffset.x / radius, thumbOffset.y / radius)
                }
                try {
                    awaitPointerEventScope {
                        var pointerId: PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (pointerId == null) {
                                pointerId = event.changes.firstOrNull {
                                    it.pressed && !it.previousPressed &&
                                        it.position.x >= 0 && it.position.x < size.width &&
                                        it.position.y >= 0 && it.position.y < size.height
                                }?.id
                            }
                            val pointer = event.changes.firstOrNull { it.id == pointerId }
                            if (pointer != null) {
                                pointer.consume()
                                if (pointer.pressed) {
                                    update(pointer.position)
                                } else {
                                    pointerId = null
                                    thumbOffset = Offset.Zero
                                    currentOnAnalogChange(0f, 0f)
                                }
                            }
                        }
                    }
                } finally {
                    thumbOffset = Offset.Zero
                    currentOnAnalogChange(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(36.dp)
                .background(GamepadAccent, CircleShape)
        )
    }
}
