package com.devuloopers.knet.ui.core.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Protocol badge for network transport protocols (e.g. HTTP/1.1, HTTP/2, HTTP/3, WebSocket, gRPC).
 *
 * @param protocol Protocol string.
 * @param modifier Layout parameters.
 */
@Composable
fun ProtocolBadge(
    protocol: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (protocol.uppercase()) {
        "HTTP/2", "H2" -> KNetColors.ActiveBlue.copy(alpha = 0.15f) to KNetColors.ActiveBlue
        "HTTP/3", "H3", "QUIC" -> KNetColors.PurpleWS.copy(alpha = 0.15f) to KNetColors.PurpleWS
        "WEBSOCKET", "WS", "WSS" -> KNetColors.WarningOrange.copy(alpha = 0.15f) to KNetColors.WarningOrange
        "GRPC" -> KNetColors.SuccessGreen.copy(alpha = 0.15f) to KNetColors.SuccessGreen
        else -> KNetColors.TextSecondary.copy(alpha = 0.15f) to KNetColors.TextSecondary
    }

    Box(
        modifier = modifier
            .background(backgroundColor, KNetShapes.Small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = protocol.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
