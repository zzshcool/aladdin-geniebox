package com.aladdin.geniebox.ui.component

import androidx.compose.animation.core.animateFloatAsState
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
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tvCardScale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) CardDarkFocused else CardDark
        ),
        border = if (isFocused) BorderStroke(2.5.dp, AladdinGold) else BorderStroke(1.dp, DividerDark),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
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
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "btnScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isFocused -> AladdinGold
                isPrimary -> AladdinGold.copy(alpha = 0.85f)
                else -> CardDarkFocused
            },
            contentColor = if (isFocused || isPrimary) BgDark else TextPrimary
        ),
        border = if (isFocused) BorderStroke(2.dp, AladdinGoldLight) else BorderStroke(1.dp, DividerDark)
    ) {
        Text(text = text)
    }
}
