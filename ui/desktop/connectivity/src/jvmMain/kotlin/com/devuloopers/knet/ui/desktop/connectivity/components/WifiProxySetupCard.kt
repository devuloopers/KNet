package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState

/** Single entry card for the complete Wi-Fi proxy setup flow. */
@Composable
internal fun WifiProxySetupCard(
    state: ConnectDeviceUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = modifier
            .size(300.dp)
            .clip(KNetTheme.shapes.large)
            .clickable(onClick = onClick)
            .handCursor(),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(KNetTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                KNetSurface(
                    modifier = Modifier.matchParentSize(),
                    color = colors.semantic.infoContainer,
                    shape = KNetTheme.shapes.large,
                ) {}
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Wi-Fi Proxy Setup",
                style = KNetTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connect Android, iPhone, or iPad",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            WifiSetupStatus(state)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Text(
                    text = "  Open setup  ",
                    style = KNetTheme.typography.labelMedium.copy(color = colors.textSecondary),
                    maxLines = 1,
                    softWrap = false,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun WifiSetupStatus(state: ConnectDeviceUiState) {
    val colors = KNetTheme.colors
    val (label, color) = if (state.failureCode != null) {
        "Setup needs attention" to colors.semantic.error
    } else when (val sharing = state.sharingState) {
        is WifiSharingState.Active ->
            "Ready at ${sharing.session.proxyEndpoint.host}:${sharing.session.proxyEndpoint.port}" to colors.semantic.success
        WifiSharingState.Enabling -> "Preparing Wi-Fi access" to colors.semantic.info
        is WifiSharingState.Recovering -> "Reconnecting Wi-Fi access" to colors.semantic.info
        WifiSharingState.Disabling -> "Closing Wi-Fi access" to colors.textMuted
        is WifiSharingState.Failed -> "Setup needs attention" to colors.semantic.warning
        is WifiSharingState.Disabled -> if (state.isProxyRunning) {
            "Waiting for a local network" to colors.semantic.warning
        } else {
            "Proxy is stopped" to colors.textMuted
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        KNetSurface(Modifier.size(7.dp), color = color, shape = KNetTheme.shapes.pill) {}
        Text(
            text = label,
            style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
