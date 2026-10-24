package com.aladdin.geniebox.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.cos

private val LampPixels = listOf(
    "................hh..............",
    "...............hgggh............",
    "..............hhhhhhh...........",
    "..........hhhhggggggghhhh.......",
    "hh.......hggggggggggggggghhh....",
    ".hghhhhhhgggggggggggggggggh.hh..",
    "..hgggggggggggggggggggggggh..h..",
    "...hhhhgggggggggggggggggggh..h..",
    ".......hgggggggggggggggggh.hh...",
    "........hggggggggggggggghhh.....",
    ".........hhhhhhhhhhhhhh.........",
    ".............hggggh.............",
    ".............hggggh.............",
    "..........hhhhgggghhhh..........",
    ".........hggggggggggggh.........",
    ".........hhhhhhhhhhhhhh........."
)

private val BoxPixels = listOf(
    "..ssssssssssssssssss....",
    ".sccccccccccccccccccs...",
    ".scccccccccccccccccccs..",
    ".sccddddddddddddddcccs..",
    ".sccddddddddddddddcccs..",
    ".sccddddddddddddddcccs..",
    ".sccddddddmdddddddcccs..",
    ".sccddddddmdddddddcccs..",
    ".sccdddddmcmddddddcccs..",
    ".sccdddmmcccmmddddcccs..",
    ".sccddmmcccccmmdddcccs..",
    ".sccdddmmcccmmddddcccs..",
    ".sccdddddmcmddddddcccs..",
    ".sccddddddmdddddddcccs..",
    ".sccddddddmdddddddcccs..",
    ".sccddddddddddddddcccs..",
    ".scccccccccccccccccccs..",
    ".sccccdddccccccccccccs..",
    ".sccccdddccccccccccccs..",
    ".sccdddddddccccmmcccccs.",
    ".sccdddddddcccmmmmccccs.",
    ".sccccdddccccccmmcccccs.",
    ".sccccdddccccccccmmcccs.",
    ".sccccccccccccccmmmmcs..",
    ".scccccccccccccccmmcs...",
    ".scccccccddddccccccs....",
    "..scccccccccccccccs.....",
    "...sssssssssssssss......"
)

@Composable
fun BrandIntro(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val finish by rememberUpdatedState(onFinished)
    var finished by remember { mutableStateOf(false) }
    val skip: () -> Unit = { if (!finished) { finished = true; finish() } }
    DisposableEffect(Unit) {
        val input = com.aladdin.geniebox.app.GenieApplication.instance.inputManager
        input.introSkipHandler = skip
        onDispose { input.introSkipHandler = null }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(Unit) {
        // 這是有固定段落的品牌片頭，以畫面時鐘播放，不使用系統動畫倍率。
        val durationNanos = 7_000_000_000L
        var elapsed = 0L
        var previousFrame = withFrameNanos { it }
        while (elapsed < durationNanos) {
            val frame = withFrameNanos { it }
            elapsed += (frame - previousFrame).coerceIn(0L, 100_000_000L)
            previousFrame = frame
            progress = (elapsed.toFloat() / durationNanos).coerceAtMost(1f)
        }
        if (!finished) { finished = true; finish() }
    }
    val textAlpha = ((progress - 0.75f) / 0.09f).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF05080F))
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.action == android.view.KeyEvent.ACTION_UP &&
                (android.view.KeyEvent.isGamepadButton(key.keyCode) || key.keyCode in listOf(
                    android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_BACK))) { skip(); true } else false
        }.focusRequester(focus).focusable().clickable(onClick = skip).safeDrawingPadding(),
        contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val artSize = if (landscape) minOf(maxWidth * 0.45f, maxHeight * 0.85f, 360.dp)
            else minOf(maxWidth * 0.9f, maxHeight * 0.58f, 360.dp)
        val art: @Composable () -> Unit = {
            Canvas(Modifier.size(artSize)) {
                drawBrandFrame(floor(progress * 84f) / 84f)
            }
        }
        val title: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(textAlpha)) {
                Text("ALADDIN", color = Color(0xFF8E9BAD), fontSize = 12.sp,
                    letterSpacing = 5.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Text("GenieBox", color = Color(0xFFE3E8EE), fontSize = 30.sp,
                    letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(16.dp))
                Text(AppLanguage.text(R.string.brand_tagline), color = Color(0xFF9AA9BD),
                    fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        if (landscape) {
            Row(Modifier.padding(24.dp).widthIn(max = 800.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                art()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)) {
                art()
                title()
            }
        }
        Text(AppLanguage.text(R.string.opt_skip_intro), color = Color(0xFF8E9BAD), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private fun DrawScope.drawBrandFrame(t: Float) {
    val pixel = floor(minOf(size.width, size.height) / 96f).coerceAtLeast(1f)
    val origin = Offset(floor((size.width - 96f * pixel) / 2f), floor((size.height - 96f * pixel) / 2f))
    fun cell(x: Float, y: Float, color: Color, width: Int = 1) {
        drawRect(color, origin + Offset(floor(x) * pixel, floor(y) * pixel), Size(pixel * width, pixel * width))
    }
    val smoke = Color(0xFF8FAFC5)
    val lampAlpha = (1f - (t - 0.56f) / 0.16f).coerceIn(0f, 1f)
    val shake = if (t < 0.24f) floor(sin(t * 75f) * 1.5f) else 0f
    LampPixels.forEachIndexed { y, row -> row.forEachIndexed { x, value ->
        val color = when (value) {
            'g' -> Color(0xFFCFB47C)
            'h' -> Color(0xFF796641)
            else -> null
        }
        if (color != null) cell(24f + x * 1.5f, 70f + y * 1.5f + shake,
            color.copy(alpha = lampAlpha), 2)
    } }
    // 燈嘴先吐出分段煙流，再匯入上方的厚煙團。
    for (i in 0 until 38) {
        val rise = (t - 0.12f) * 2.5f - i * 0.015f
        if (rise in 0f..1f) {
            val x = 24f + rise * 24f + sin(rise * 10f - t * 20f) * (2f + rise * 8f)
            val y = 76f - rise * 38f
            val alpha = ((t - 0.12f) / 0.07f).coerceIn(0f, 1f) *
                (1f - (t - 0.49f) / 0.15f).coerceIn(0f, 1f)
            cell(x, y, smoke.copy(alpha = alpha * 0.7f), 3 + i % 3)
        }
    }
    // 同一批煙霧像素收攏成圖示，避免煙霧消失後直接換一張圖。
    BoxPixels.forEachIndexed { y, row -> row.forEachIndexed { x, value ->
        val color = when (value) {
            's' -> Color(0xFF526C64)
            'c' -> Color(0xFFE4EAE1)
            'd' -> Color(0xFF1E352B)
            'm' -> Color(0xFF99C6AB)
            else -> null
        }
        if (color != null) {
            val seed = ((x * 19 + y * 31) % 101) / 100f
            val angle = seed * 6.283185f
            val cloudTime = minOf(t, 0.5f)
            val cloudGrowth = ((t - 0.23f - seed * 0.06f) / 0.18f).coerceIn(0f, 1f)
            val cloudX = 48f + sin(angle + cloudTime * 8f) * (12f + (x % 7) * 2f)
            val cloudY = 39f + cos(angle + cloudTime * 6f) * (11f + (y % 6) * 2f)
            val fromX = 24f + (cloudX - 24f) * cloudGrowth
            val fromY = 76f + (cloudY - 76f) * cloudGrowth
            val stagger = ((x * 13 + y * 7) % 19) / 19f * 0.08f
            val formed = ((t - 0.50f - stagger) / 0.18f).coerceIn(0f, 1f)
            if (cloudGrowth > 0f) {
                cell(fromX + (24f + x * 2f - fromX) * formed,
                    fromY + (10f + y * 2f - fromY) * formed,
                    lerp(smoke, color, formed).copy(alpha = cloudGrowth * (0.55f + formed * 0.45f)),
                    if (formed < 0.75f) 3 else 2)
            }
        }
    } }
    if (t in 0.72f..0.85f) {
        val alpha = (1f - (t - 0.72f) / 0.13f).coerceIn(0f, 1f)
        listOf(Offset(16f, 18f), Offset(78f, 30f), Offset(28f, 75f)).forEach {
            cell(it.x, it.y, smoke.copy(alpha = alpha), 2)
        }
    }
}
