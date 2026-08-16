package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Status pill badge indicating OS Root CA trust state.
 */
@Composable
fun StatusBadge(
    isTrusted: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isTrusted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
    val textColors = if (isTrusted) Color(0xFF10B981) else Color(0xFFF59E0B)
    val label = if (isTrusted) "• TRUSTED IN OS" else "• NOT INSTALLED"

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = KNetTheme.typography.labelSmall.copy(
                color = textColors,
                fontSize = 10.sp
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}
