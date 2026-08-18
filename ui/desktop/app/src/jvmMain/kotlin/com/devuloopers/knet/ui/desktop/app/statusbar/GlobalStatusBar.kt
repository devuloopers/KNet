package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.statusbar.FlexibleSpacer
import com.devuloopers.knet.ui.core.components.statusbar.KNetStatusBar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density 32dp Desktop Global Status Bar composable matching the v3.0 Specification.
 *
 * Left: Glowing Ready Status, System Proxy indicator, Change action link.
 * Right: Request counter, Byte transfer size, and Duration timer.
 */
@Composable
fun KNetGlobalStatusBar(
    modifier: Modifier = Modifier,
    requestCount: Int = 245,
    bytesTransferred: String = "1.2 MB",
    duration: String = "00:01:24",
    isProxyRunning: Boolean = true,
    onChangeProxy: () -> Unit = {}
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetStatusBar(modifier = modifier) {
        // Left Group: Glowing Ready dot + System Proxy State
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
            Text(
                text = "Ready",
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 6.dp, end = 16.dp)
            )

            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = themeColors.textSecondary,
                modifier = Modifier.size(13.dp)
            )

            Text(
                text = "System Proxy: ",
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp)
            )

            Text(
                text = if (isProxyRunning) "Running" else "Stopped",
                style = typography.caption.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            )

            Text(
                text = "Change",
                style = typography.caption.copy(
                    color = themeColors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable(onClick = onChangeProxy)
                    .handCursor()
            )
        }

        FlexibleSpacer()

        // Right Group: Requests, Transfer Size, Duration
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = themeColors.textMuted,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$requestCount Requests",
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, end = 16.dp)
            )

            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = themeColors.textMuted,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = bytesTransferred,
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 4.dp, end = 16.dp)
            )

            Text(
                text = "Duration: $duration",
                style = typography.caption.copy(
                    color = themeColors.textSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}
