package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * System status bar footer displaying connection indicators, client sessions,
 * proxy uptime, and active network transfer rates.
 */
@Composable
fun SystemStatusBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(KNetColors.SuccessGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Connected",
                color = KNetColors.SuccessGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "1 Client",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Uptime: 00:12:34",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "↓ 1.25 KB/s",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "↑ 2.34 KB/s",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
