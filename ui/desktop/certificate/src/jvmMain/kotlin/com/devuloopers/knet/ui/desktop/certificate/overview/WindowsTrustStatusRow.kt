package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

@Composable
fun WindowsTrustStatusRow(
    trustState: TrustInstallationState,
    onInstallClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val (statusText, statusBg, statusColor) = when (trustState) {
        TrustInstallationState.INSTALLED -> Triple("Installed", themeColors.semantic.success.copy(alpha = 0.15f), themeColors.semantic.success)
        TrustInstallationState.FAILED -> Triple("Failed", themeColors.semantic.error.copy(alpha = 0.15f), themeColors.semantic.error)
        TrustInstallationState.INSTALLING -> Triple("Installing...", themeColors.surfaceVariant, themeColors.textSecondary)
        TrustInstallationState.IDLE -> Triple("Not Installed", themeColors.semantic.warning.copy(alpha = 0.15f), themeColors.semantic.warning)
    }

    KNetSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = themeColors.surfaceVariant,
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = "Windows",
                tint = themeColors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Windows OS",
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.textPrimary
                )
                Text(
                    text = "System Certificate Store",
                    style = typography.labelSmall,
                    color = themeColors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (trustState == TrustInstallationState.IDLE || trustState == TrustInstallationState.FAILED) {
                KNetButton(
                    onClick = onInstallClicked,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact
                ) {
                    Text("Install Trust")
                }
            } else {
                KNetBadge(
                    text = statusText,
                    containerColor = statusBg,
                    contentColor = statusColor
                )
            }
        }
    }
}
