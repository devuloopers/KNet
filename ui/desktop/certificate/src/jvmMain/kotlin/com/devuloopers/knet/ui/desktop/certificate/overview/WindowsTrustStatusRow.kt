package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val isInstalled = trustState == TrustInstallationState.INSTALLED

    KNetSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = themeColors.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2563EB).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Windows OS",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

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
            }

            if (isInstalled) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Installed",
                        tint = themeColors.semantic.success,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Installed & Trusted",
                        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = themeColors.semantic.success
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                KNetButton(
                    onClick = onInstallClicked,
                    variant = ButtonVariant.Ghost,
                    colors = com.devuloopers.knet.ui.core.components.button.ButtonDefaults.colors(ButtonVariant.Ghost).copy(borderColor = themeColors.border),
                    size = ButtonSize.Compact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reinstall Trust")
                }
            } else if (trustState == TrustInstallationState.CHECKING) {
                Spacer(modifier = Modifier.height(10.dp))

                KNetBadge(
                    text = "Checking...",
                    containerColor = themeColors.surfaceVariant,
                    contentColor = themeColors.textSecondary
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                KNetButton(
                    onClick = onInstallClicked,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Compact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (trustState == TrustInstallationState.INSTALLING) "Installing..." else "Install Trust")
                }
            }
        }
    }
}
