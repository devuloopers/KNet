package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.util.HostPlatform
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonDefaults
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * Platform-adaptive status card displaying the host operating system's Root CA trust state.
 *
 * Automatically adapts title, subtitle, and icons for macOS Keychain, Windows Certificate Store,
 * and Linux system trust bundles.
 *
 * @param platform The detected host operating system platform.
 * @param trustState Current trust installation status (IDLE, CHECKING, INSTALLING, INSTALLED, FAILED).
 * @param onInstallClicked Callback invoked when the user requests trust installation or re-verification.
 */
@Composable
fun SystemTrustStatusRow(
    platform: HostPlatform = HostPlatform.current(),
    trustState: TrustInstallationState,
    onInstallClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val isInstalled = trustState == TrustInstallationState.INSTALLED

    val platformIcon: ImageVector = when (platform) {
        HostPlatform.MACOS -> Icons.Default.LaptopMac
        HostPlatform.WINDOWS -> Icons.Default.Computer
        HostPlatform.LINUX -> Icons.Default.Terminal
        HostPlatform.UNKNOWN -> Icons.Default.Computer
    }

    val platformTitle: String = when (platform) {
        HostPlatform.MACOS -> "macOS Keychain"
        HostPlatform.WINDOWS -> "Windows OS"
        HostPlatform.LINUX -> "Linux OS"
        HostPlatform.UNKNOWN -> "System Store"
    }

    val platformSubtitle: String = when (platform) {
        HostPlatform.MACOS -> "User Trust Store (login.keychain-db)"
        HostPlatform.WINDOWS -> "User Certificate Store (Root)"
        HostPlatform.LINUX -> "System CA Store (/etc/ssl/certs)"
        HostPlatform.UNKNOWN -> "System Certificate Store"
    }

    val iconBgColor: Color = when (platform) {
        HostPlatform.MACOS -> Color(0xFF6366F1).copy(alpha = 0.15f)
        HostPlatform.WINDOWS -> Color(0xFF2563EB).copy(alpha = 0.15f)
        HostPlatform.LINUX -> Color(0xFFD97706).copy(alpha = 0.15f)
        HostPlatform.UNKNOWN -> Color(0xFF6B7280).copy(alpha = 0.15f)
    }

    val iconTintColor: Color = when (platform) {
        HostPlatform.MACOS -> Color(0xFF818CF8)
        HostPlatform.WINDOWS -> Color(0xFF60A5FA)
        HostPlatform.LINUX -> Color(0xFFFBBF24)
        HostPlatform.UNKNOWN -> Color(0xFF9CA3AF)
    }

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
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = platformIcon,
                        contentDescription = platformTitle,
                        tint = iconTintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformTitle,
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textPrimary
                    )
                    Text(
                        text = platformSubtitle,
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
                    colors = ButtonDefaults.colors(ButtonVariant.Ghost).copy(borderColor = themeColors.border),
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
                    Text(
                        when {
                            trustState == TrustInstallationState.INSTALLING -> "Installing..."
                            platform == HostPlatform.LINUX -> "View Install Instructions"
                            else -> "Install Trust"
                        }
                    )
                }
            }
        }
    }
}
