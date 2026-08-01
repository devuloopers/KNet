package com.devuloopers.knet.ui.desktop.traffic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Proxy connection status indicator badge.
 */
@Composable
fun ConnectionIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                if (isConnected) KNetColors.SuccessGreen.copy(alpha = 0.2f) else KNetColors.ErrorRed.copy(alpha = 0.2f),
                KNetShapes.Small
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isConnected) "CONNECTED" else "DISCONNECTED",
            color = if (isConnected) KNetColors.SuccessGreen else KNetColors.ErrorRed,
            fontSize = 10.sp
        )
    }
}
