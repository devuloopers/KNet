package com.devuloopers.knet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * Renders a stylized colored badge for HTTP response status codes (e.g. 200, 304, 400).
 *
 * Fully documented according to the repository's KDoc standards.
 *
 * @param statusCode The HTTP status code integer (e.g. 200, 404).
 * @param modifier Layout parameters passed from parents.
 */
@Composable
fun StatusBadge(
    statusCode: Int,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (statusCode) {
        in 200..299 -> KNetColors.SuccessGreen.copy(alpha = 0.15f) to KNetColors.SuccessGreen
        in 300..399 -> KNetColors.WarningOrange.copy(alpha = 0.15f) to KNetColors.WarningOrange
        in 400..499 -> KNetColors.ErrorRed.copy(alpha = 0.15f) to KNetColors.ErrorRed
        in 500..599 -> Color.Magenta.copy(alpha = 0.15f) to Color.Magenta
        101 -> KNetColors.ActiveBlue.copy(alpha = 0.15f) to KNetColors.ActiveBlue
        else -> KNetColors.TextSecondary.copy(alpha = 0.15f) to KNetColors.TextSecondary
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusCode.toString(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
