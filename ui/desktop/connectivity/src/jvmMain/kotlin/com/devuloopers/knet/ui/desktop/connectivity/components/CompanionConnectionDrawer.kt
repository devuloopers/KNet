package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.model.CompanionInvitationUiState
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState

/** Right-side companion onboarding drawer with an ephemeral pairing QR and connection guidance. */
@Composable
internal fun CompanionConnectionDrawer(
    state: ConnectDeviceUiState,
    onIntent: (ConnectDeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    KNetSideDrawer(
        visible = state.isCompanionDrawerVisible,
        size = KNetSideDrawerSize.STANDARD,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CompanionDrawerHeader(onClose = { onIntent(ConnectDeviceIntent.CloseDrawer) })
            when (val invitation = state.companionInvitation) {
                CompanionInvitationUiState.Idle -> CompanionInactivePanel(state, onIntent)
                CompanionInvitationUiState.Creating -> CompanionMessagePanel(
                    message = "Creating a short-lived companion invitation.",
                    error = false,
                )
                is CompanionInvitationUiState.Ready -> CompanionInvitationContent(invitation, onIntent)
                CompanionInvitationUiState.Expired -> CompanionExpiredPanel(onIntent)
                is CompanionInvitationUiState.Failed -> CompanionFailurePanel(invitation.reason, state, onIntent)
            }
        }
    }
}

@Composable
private fun CompanionDrawerHeader(onClose: () -> Unit) {
    val colors = KNetTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KNetSurface(
            modifier = Modifier.size(42.dp),
            color = colors.semantic.infoContainer,
            shape = KNetTheme.shapes.large,
        ) {
            Icon(
                Icons.Default.Devices,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(23.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = "Connect Companion App",
                style = KNetTheme.typography.titleMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Scan once to authorize this phone and connect it to the correct KNet desktop.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
            )
        }
        KNetIconButton(
            onClick = onClose,
            icon = Icons.Default.Close,
            contentDescription = "Close companion connection",
            size = 40.dp,
            iconSize = 24.dp,
            tint = colors.textSecondary,
        )
    }
}
