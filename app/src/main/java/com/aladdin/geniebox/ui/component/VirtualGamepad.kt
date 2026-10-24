package com.aladdin.geniebox.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalDensity
import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
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
    layout: Map<String, Triple<Float, Float, Float>> = emptyMap(),
    editing: Boolean = false,
    onLayoutChange: (Map<String, Triple<Float, Float, Float>>) -> Unit = {},
    onEditDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val currentLayout by rememberUpdatedState(layout)
    val changeLayout by rememberUpdatedState(onLayoutChange)
    var selected by remember { mutableStateOf("DPAD") }
    val triggerHaptic = {
        if (vibrationEnabled && !editing) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val actionRegions = remember(platform, editing) { mutableMapOf<Int, Pair<Offset, Float>>() }
    val currentRegions by rememberUpdatedState(actionRegions)
    val currentAdapter by rememberUpdatedState(adapter)
    val currentHaptic by rememberUpdatedState(triggerHaptic)
    var heldActions by remember { mutableStateOf(emptySet<Int>()) }
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding().pointerInput(editing, platform) {
        if (editing) return@pointerInput
        val fingers = mutableMapOf<PointerId, Set<Int>>()
        fun update(keys: Set<Int>) {
            (heldActions - keys).forEach { currentAdapter.sendButton(1, it, false) }
            (keys - heldActions).forEach { currentHaptic(); currentAdapter.sendButton(1, it, true) }
            heldActions = keys
        }
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val keys = mutableSetOf<Int>()
                    event.changes.forEach { change ->
                        val point = change.position / density.density
                        val touched = currentRegions.filterValues { (center, radius) ->
                            (point - center).getDistance() <= radius
                        }.keys
                        if (change.pressed && !change.previousPressed && touched.isNotEmpty()) {
                            fingers[change.id] = touched.toSet()
                        }
                        if (change.id in fingers) {
                            if (change.pressed) { keys += fingers[change.id].orEmpty(); keys += touched }
                            else fingers.remove(change.id)
                            change.consume()
                        }
                    }
                    update(keys)
                }
            }
        } finally { update(emptySet()) }
    }) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = maxWidth < 420.dp
        val cluster = minOf(128f * scale, height * 0.42f, (width - 32f) / 2f,
            height - if (compact) 56f else 16f).coerceAtLeast(1f)
        val side = edgeInset.coerceIn(0f, ((width - cluster * 2f - 16f) / 2f).coerceAtLeast(0f))
        val bottom = bottomInset.coerceIn(0f, (height - cluster - 56f).coerceAtLeast(0f))
        val clusterY = height - (if (compact) maxOf(bottom, 48f) else bottom) - cluster / 2f
        val leftX = side + cluster / 2f
        val rightX = width - side - cluster / 2f
        val actionSize = cluster * 0.4f
        val ps = platform == PlatformType.PS1 || platform == PlatformType.PSP
        val controls = buildList {
            add("DPAD")
            if (ps) add("ANALOG")
            addAll(listOf("A", "B"))
            if (platform == PlatformType.SFC || ps) addAll(listOf("X", "Y"))
            addAll(listOf("SELECT", "START"))
            if (platform != PlatformType.NES && platform != PlatformType.GB) addAll(listOf("L", "R"))
            if (platform == PlatformType.PS1) addAll(listOf("L2", "R2"))
        }
        val defaults = mutableMapOf<String, Triple<Float, Float, Float>>()
        controls.forEach { id ->
            val defaultSize = when (id) { "DPAD" -> cluster; "ANALOG" -> minOf(100f * scale, height * 0.24f)
                "SELECT", "START" -> 60f; "L", "R", "L2", "R2" -> 48f; else -> actionSize }
            val center = when (id) {
                "DPAD" -> Offset(leftX, clusterY)
                "ANALOG" -> Offset(leftX, 64f + defaultSize / 2f)
                "A" -> Offset(rightX + cluster * 0.3f, clusterY)
                "B" -> Offset(rightX, clusterY + cluster * 0.3f)
                "X" -> Offset(rightX, clusterY - cluster * 0.3f)
                "Y" -> Offset(rightX - cluster * 0.3f, clusterY)
                "SELECT" -> Offset(width / 2f - 34f, height - 24f)
                "START" -> Offset(width / 2f + 34f, height - 24f)
                "L" -> Offset(side + 24f, 32f)
                "L2" -> Offset(side + 80f, 32f)
                "R2" -> Offset(width - side - 80f, 32f)
                else -> Offset(width - side - 24f, 32f)
            }
            defaults[id] = Triple(center.x / width, center.y / height, 1f)
            val stored = layout[id]
            val factor = stored?.third ?: 1f
            val itemWidth = (defaultSize * factor).coerceIn(1f, width)
            val itemHeight = ((if (id == "SELECT" || id == "START") 32f else defaultSize) * factor).coerceIn(1f, height)
            val x = ((stored?.first ?: (center.x / width)) * width).coerceIn(itemWidth / 2f, width - itemWidth / 2f)
            val y = ((stored?.second ?: (center.y / height)) * height).coerceIn(itemHeight / 2f, height - itemHeight / 2f)
            val editModifier = if (editing) Modifier.pointerInput(id, width, height) {
                var start = Offset.Zero
                var distance = Offset.Zero
                detectDragGestures(onDragStart = {
                    selected = id; distance = Offset.Zero
                    val saved = currentLayout[id]
                    start = if (saved == null) center else Offset(saved.first * width, saved.second * height)
                }) { change, delta ->
                    change.consume()
                    distance += delta / density.density
                    val position = start + distance
                    changeLayout(currentLayout + (id to Triple((position.x / width).coerceIn(0f, 1f),
                        (position.y / height).coerceIn(0f, 1f), currentLayout[id]?.third ?: 1f)))
                }
            }.clickable { selected = id } else Modifier
            Box(Modifier.offset { IntOffset(with(density) { (x - itemWidth / 2f).dp.roundToPx() },
                with(density) { (y - itemHeight / 2f).dp.roundToPx() }) }
                .size(itemWidth.dp, itemHeight.dp).then(editModifier)
                .then(if (editing && selected == id) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                .alpha(if (editing) 1f else opacity)) {
                if (editing) {
                    Box(Modifier.fillMaxSize().background(GamepadSurface, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text(id, color = Color.White, fontSize = 12.sp)
                    }
                } else when (id) {
                    "DPAD" -> VirtualDpad { key, pressed -> if (pressed) triggerHaptic(); adapter.sendButton(1, key, pressed) }
                    "ANALOG" -> VirtualAnalogStick { ax, ay ->
                        adapter.sendAnalog(1, RetroPadKey.ANALOG_LEFT_X, ax)
                        adapter.sendAnalog(1, RetroPadKey.ANALOG_LEFT_Y, ay)
                    }
                    else -> {
                        val key = when (id) {
                            "A" -> RetroPadKey.A; "B" -> RetroPadKey.B; "X" -> RetroPadKey.X; "Y" -> RetroPadKey.Y
                            "L" -> RetroPadKey.L; "R" -> RetroPadKey.R; "L2" -> RetroPadKey.L2; "R2" -> RetroPadKey.R2
                            "SELECT" -> RetroPadKey.SELECT; else -> RetroPadKey.START
                        }
                        val label = if (ps) when (id) { "A" -> "○"; "B" -> "×"; "X" -> "△"; "Y" -> "□"; else -> id } else id
                        val action = id in listOf("A", "B", "X", "Y")
                        if (action) actionRegions[key] = Offset(x, y) to (minOf(itemWidth, itemHeight) * 0.62f)
                        GamepadButton(label, modifier = Modifier.fillMaxSize(), width = itemWidth.roundToInt(), height = itemHeight.roundToInt(),
                            interactive = !action, pressedOverride = if (action) key in heldActions else null,
                            shape = if (id == "SELECT" || id == "START") RoundedCornerShape(18.dp) else CircleShape) { pressed ->
                            if (pressed) triggerHaptic()
                            adapter.sendButton(1, key, pressed)
                        }
                    }
                }
            }
        }
        if (editing) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 300.dp).background(Color(0xEE0D1420))
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(AppLanguage.text(R.string.opt_drag_controls), color = Color.White, fontSize = 12.sp)
                Text(selected + " · " + ((layout[selected]?.third ?: 1f) * 100).roundToInt() + "%", color = Color.White)
                Slider(value = layout[selected]?.third ?: 1f, valueRange = 0.65f..1.6f, onValueChange = { value ->
                    val old = layout[selected] ?: defaults[selected]
                    if (old != null) onLayoutChange(layout + (selected to old.copy(third = value)))
                }, onValueChangeFinished = {})
                Row {
                    TextButton(onClick = { onLayoutChange(emptyMap()) }) { Text(AppLanguage.text(R.string.opt_reset_layout)) }
                    TextButton(onClick = onEditDone) { Text(AppLanguage.text(R.string.opt_done)) }
                }
            }
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
                val owned = mutableSetOf<PointerId>()
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.filter { it.pressed && !it.previousPressed &&
                                it.position.x >= 0 && it.position.x < size.width && it.position.y >= 0 && it.position.y < size.height
                            }.forEach { owned += it.id }
                            event.changes.filter { !it.pressed }.forEach { owned -= it.id }
                            val down = event.changes.any { it.pressed && it.id in owned }
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
