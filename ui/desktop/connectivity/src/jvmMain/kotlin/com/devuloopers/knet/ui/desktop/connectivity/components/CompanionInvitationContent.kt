package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.model.CompanionInvitationUiState
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent

/** Active companion invitation QR, endpoint details, expiry, and ordered setup guidance. */
@Composable
internal fun CompanionInvitationContent(
    invitation: CompanionInvitationUiState.Ready,
    onIntent: (ConnectDeviceIntent) -> Unit,
) {
    val colors = KNetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KNetSurface(
            modifier = Modifier.size(270.dp),
            color = Color.White,
            shape = KNetTheme.shapes.large,
        ) {
            KNetQrCode(value = invitation.qrPayload, modifier = Modifier.size(262.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompanionStatusLabel("Invitation ready", colors.semantic.success)
            CompanionDesktopPanel(invitation)
            Text(
                text = "Open KNet Companion and scan this QR. The invitation contains a one-time secret and expires automatically.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
            )
            Text(
                text = "Expires in ${formatRemainingTime(invitation.remainingSeconds)}",
                style = KNetTheme.typography.labelMedium.copy(
                    color = colors.semantic.warning,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                softWrap = false,
            )
            KNetButton(
                onClick = { onIntent(ConnectDeviceIntent.RefreshCompanionInvitation) },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh QR")
            }
        }
    }

    CompanionConnectionSteps(invitation.desktopDisplayName)

    Text(
        text = "Do not share or save this QR. It authorizes one companion until the invitation expires or is used.",
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.semantic.warningContainer, KNetTheme.shapes.small)
            .padding(12.dp),
        style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
    )
}

@Composable
private fun CompanionDesktopPanel(invitation: CompanionInvitationUiState.Ready) {
    val colors = KNetTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, KNetTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Router, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = invitation.desktopDisplayName,
                style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${invitation.host}:${invitation.controlPort}",
                style = KNetTheme.typography.titleSmall.copy(
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompanionConnectionSteps(desktopDisplayName: String) {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Connect from KNet Companion",
                    style = KNetTheme.typography.titleSmall.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            listOf(
                "Connect the phone and this computer to the same reachable local network.",
                "Open KNet Companion, choose Connect to KNet Desktop, and scan this QR.",
                "Confirm that the companion shows $desktopDisplayName before continuing.",
                "Install and verify the KNet certificate when prompted, then start inspection.",
            ).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        style = KNetTheme.typography.labelMedium.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionStatusLabel(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        KNetSurface(Modifier.size(7.dp), color = color, shape = KNetTheme.shapes.pill) {}
        Text(
            text = label,
            style = KNetTheme.typography.labelMedium.copy(color = color, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}

internal fun formatRemainingTime(remainingSeconds: Long): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = (safeSeconds % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
