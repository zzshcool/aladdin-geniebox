package com.aladdin.geniebox.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aladdin.geniebox.ui.theme.*

@Composable
fun TvCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tvCardScale"
    )
    val surface by animateColorAsState(if (isFocused) CardDarkFocused else CardDark,
        animationSpec = tween(150), label = "cardSurface")

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = surface
        ),
        border = if (isFocused) BorderStroke(2.dp, AladdinGold) else BorderStroke(1.dp, DividerDark.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content(isFocused)
        }
    }
}

@Composable
fun FocusableButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.015f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "btnScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).scale(scale),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isFocused -> AladdinGold
                isPrimary -> CardDarkFocused
                else -> CardDark
            },
            contentColor = if (isFocused) BgDark else TextPrimary
        ),
        border = if (isFocused) BorderStroke(2.dp, AladdinGoldLight)
            else BorderStroke(1.dp, if (isPrimary) AladdinGold else DividerDark)
    ) {
        Text(text = text)
    }
}
