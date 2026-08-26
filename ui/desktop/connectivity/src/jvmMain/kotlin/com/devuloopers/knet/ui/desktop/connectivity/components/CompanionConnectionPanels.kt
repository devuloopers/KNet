package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.model.CompanionInvitationFailure
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceOperation
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState

/** Renders companion onboarding prerequisites while no invitation is available. */
@Composable
internal fun CompanionInactivePanel(
    state: ConnectDeviceUiState,
    onIntent: (ConnectDeviceIntent) -> Unit,
) {
    val colors = KNetTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, KNetTheme.shapes.large)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.QrCode2, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(56.dp))
        Text(
            text = if (state.isProxyRunning) {
                "Waiting for a reachable local network"
            } else {
                "Start the proxy to create a companion invitation"
            },
            style = KNetTheme.typography.titleSmall.copy(
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        if (!state.isProxyRunning) {
            KNetButton(
                onClick = { onIntent(ConnectDeviceIntent.StartProxy) },
                loading = state.operation == ConnectDeviceOperation.STARTING_PROXY,
                enabled = !state.isBusy,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Start proxy")
            }
        }
    }
}

/** Renders the expired invitation recovery action after the secret-bearing QR is removed. */
@Composable
internal fun CompanionExpiredPanel(onIntent: (ConnectDeviceIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompanionMessagePanel("This invitation expired and its QR has been removed.", error = false)
        KNetButton(onClick = { onIntent(ConnectDeviceIntent.RefreshCompanionInvitation) }) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Create new QR")
        }
    }
}

/** Renders a typed invitation failure without exposing an exception or secret. */
@Composable
internal fun CompanionFailurePanel(
    reason: CompanionInvitationFailure,
    state: ConnectDeviceUiState,
    onIntent: (ConnectDeviceIntent) -> Unit,
) {
    if (reason == CompanionInvitationFailure.CONNECTIVITY_UNAVAILABLE) {
        CompanionInactivePanel(state, onIntent)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompanionMessagePanel("KNet could not create a companion invitation.", error = true)
        KNetButton(onClick = { onIntent(ConnectDeviceIntent.RefreshCompanionInvitation) }) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Try again")
        }
    }
}

/** Shared inline status panel for companion invitation progress and failures. */
@Composable
internal fun CompanionMessagePanel(message: String, error: Boolean) {
    val colors = KNetTheme.colors
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (error) colors.semantic.errorContainer else colors.semantic.infoContainer,
                KNetTheme.shapes.small,
            )
            .padding(14.dp),
        style = KNetTheme.typography.bodySmall.copy(
            color = if (error) colors.semantic.error else colors.semantic.info,
        ),
    )
}
