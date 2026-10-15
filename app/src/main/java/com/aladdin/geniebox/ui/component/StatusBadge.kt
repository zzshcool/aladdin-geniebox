package com.aladdin.geniebox.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.ui.theme.*

@Composable
fun PlatformBadge(platform: PlatformType, modifier: Modifier = Modifier) {
    val (bg, label) = when (platform) {
        PlatformType.NES -> Pair(AccentRed.copy(alpha = 0.25f), "NES")
        PlatformType.GB -> Pair(AccentGreen.copy(alpha = 0.25f), "GB/GBC")
        PlatformType.GBA -> Pair(AccentBlue.copy(alpha = 0.25f), "GBA")
        PlatformType.SFC -> Pair(AladdinGold.copy(alpha = 0.25f), "SFC")
        PlatformType.PS1 -> Pair(Color(0xFF8B5CF6).copy(alpha = 0.25f), "PS1")
        PlatformType.PSP -> Pair(Color(0xFFEC4899).copy(alpha = 0.25f), "PSP")
    }

    Box(
        modifier = modifier
            .background(color = bg, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color = AccentRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "來源離線",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentRed
        )
    }
}
