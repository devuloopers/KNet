package com.devuloopers.knet.ui.core.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Renders a stylized colored badge for HTTP request methods (e.g. GET, POST, WS).
 *
 * @param method The HTTP method text (e.g. "GET", "POST", "PUT", "DELETE", "PATCH", "WS").
 * @param modifier Layout parameters passed from parent composables.
 */
@Composable
fun MethodBadge(
    method: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (method.uppercase()) {
        "GET" -> KNetColors.SuccessGreen.copy(alpha = 0.15f) to KNetColors.SuccessGreen
        "POST" -> KNetColors.ErrorRed.copy(alpha = 0.15f) to KNetColors.ErrorRed
        "PUT", "PATCH" -> KNetColors.WarningOrange.copy(alpha = 0.15f) to KNetColors.WarningOrange
        "DELETE" -> Color.Red.copy(alpha = 0.15f) to Color.Red
        "WS", "WEBSOCKET" -> KNetColors.PurpleWS.copy(alpha = 0.15f) to KNetColors.PurpleWS
        else -> KNetColors.TextSecondary.copy(alpha = 0.15f) to KNetColors.TextSecondary
    }

    Box(
        modifier = modifier
            .background(backgroundColor, KNetShapes.Small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = method.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
