package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.presentation.state.CompanionHomeCertificateStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeDesktopStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeHttpsCapability
import com.devuloopers.knet.companion.presentation.state.CompanionHomeNetworkPath
import com.devuloopers.knet.companion.presentation.state.CompanionHomeTunnelStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompanionReadinessSummary(state: CompanionHomeUiState) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth),
        color = KNetTheme.colors.surface,
        shape = KNetTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = KNetTheme.spacing.xxl)) {
            HomeSummaryRow(
                KNetIcons.Desktop,
                stringResource(Res.string.home_desktop_label),
                state.desktopDisplayName,
                stringResource(desktopStatus(state.desktopStatus)),
                desktopColor(state.desktopStatus),
            )
            HomeDivider()
            HomeSummaryRow(
                KNetIcons.Shield,
                stringResource(Res.string.home_certificate_label),
                stringResource(Res.string.home_certificate_name),
                stringResource(certificateStatus(state.certificateStatus)),
                certificateColor(state.certificateStatus),
            )
            HomeDivider()
            HomeSummaryRow(
                KNetIcons.Lock,
                stringResource(Res.string.home_tunnel_label),
                stringResource(Res.string.home_capture_device_vpn),
                stringResource(tunnelStatus(state.tunnelStatus)),
                tunnelColor(state.tunnelStatus),
            )
        }
    }
}

@Composable
private fun HomeSummaryRow(icon: ImageVector, title: String, subtitle: String, value: String, color: Color) {
    val duration = homeAnimationDuration()
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(duration),
        label = "CompanionHomeSummaryStatusColor",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KNetTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeIconTile(icon, animatedColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = KNetTheme.typography.titleLarge, color = KNetTheme.colors.textPrimary)
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                label = "CompanionHomeSummarySubtitle",
            ) { currentSubtitle ->
                Text(currentSubtitle, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                label = "CompanionHomeSummaryValue",
            ) { currentValue ->
                Text(currentValue, style = KNetTheme.typography.titleMedium, color = animatedColor)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(animatedColor))
        }
    }
}

@Composable
internal fun CompanionInspectionConfiguration(state: CompanionHomeUiState) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth),
        color = KNetTheme.colors.surface,
        shape = KNetTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl)) {
            Text(
                stringResource(Res.string.home_configuration_title),
                style = KNetTheme.typography.heading,
                color = KNetTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(KNetTheme.spacing.lg))
            HomeConfigurationRow(
                stringResource(Res.string.home_capture_mode),
                stringResource(
                    if (state.inspectionMode == CompanionInspectionMode.DEVICE_VPN) {
                        Res.string.home_capture_device_vpn
                    } else {
                        Res.string.home_capture_local_proxy
                    },
                ),
            )
            HomeDivider()
            HomeConfigurationRow(
                stringResource(Res.string.home_network_path),
                stringResource(
                    when (state.networkPath) {
                        CompanionHomeNetworkPath.DIRECT_LAN -> Res.string.home_network_direct_lan
                        CompanionHomeNetworkPath.RELAY -> Res.string.home_network_relay
                        CompanionHomeNetworkPath.UNAVAILABLE -> Res.string.home_network_waiting
                    },
                ),
            )
            HomeDivider()
            HomeConfigurationRow(
                stringResource(Res.string.home_https_visibility),
                stringResource(
                    if (state.httpsCapability == CompanionHomeHttpsCapability.FULL) {
                        Res.string.home_https_full
                    } else {
                        Res.string.home_https_limited
                    },
                ),
            )
        }
    }
}

@Composable
private fun HomeConfigurationRow(label: String, value: String) {
    val duration = homeAnimationDuration()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KNetTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.42f), style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
        AnimatedContent(
            targetState = value,
            modifier = Modifier.weight(0.58f),
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            label = "CompanionHomeConfigurationValue",
        ) { currentValue ->
            Text(currentValue, style = KNetTheme.typography.titleMedium, color = KNetTheme.colors.textPrimary)
        }
    }
}

@Composable
internal fun CompanionHomePrivacyNote() {
    KNetSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth),
        color = KNetTheme.colors.semantic.infoContainer,
        shape = KNetTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, KNetTheme.colors.semantic.info.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KNetIcons.Info, null, tint = KNetTheme.colors.semantic.info, modifier = Modifier.size(30.dp))
            Text(
                stringResource(Res.string.home_privacy_note),
                modifier = Modifier.weight(1f),
                style = KNetTheme.typography.bodyMedium,
                color = KNetTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun HomeIconTile(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(52.dp).clip(KNetTheme.shapes.large).background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), KNetTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(27.dp))
    }
}

@Composable
private fun HomeDivider() {
    HorizontalDivider(color = KNetTheme.colors.border.copy(alpha = 0.72f))
}

@Composable
private fun desktopColor(status: CompanionHomeDesktopStatus): Color = when (status) {
    CompanionHomeDesktopStatus.AVAILABLE -> KNetTheme.colors.semantic.success
    CompanionHomeDesktopStatus.UNAVAILABLE -> KNetTheme.colors.semantic.warning
    CompanionHomeDesktopStatus.SECURITY_FAILURE -> KNetTheme.colors.semantic.error
    CompanionHomeDesktopStatus.CHECKING -> KNetTheme.colors.accent
}

@Composable
private fun certificateColor(status: CompanionHomeCertificateStatus): Color = when (status) {
    CompanionHomeCertificateStatus.VERIFIED -> KNetTheme.colors.semantic.success
    CompanionHomeCertificateStatus.NEEDS_ATTENTION -> KNetTheme.colors.semantic.error
    CompanionHomeCertificateStatus.CHECKING,
    CompanionHomeCertificateStatus.VERIFICATION_PENDING,
    -> KNetTheme.colors.accent
}

@Composable
internal fun tunnelColor(status: CompanionHomeTunnelStatus): Color = when (status) {
    CompanionHomeTunnelStatus.ACTIVE -> KNetTheme.colors.semantic.success
    CompanionHomeTunnelStatus.FAILED -> KNetTheme.colors.semantic.error
    CompanionHomeTunnelStatus.CONNECTING, CompanionHomeTunnelStatus.RECONNECTING -> KNetTheme.colors.accent
    CompanionHomeTunnelStatus.INACTIVE -> KNetTheme.colors.textMuted
}

private fun desktopStatus(value: CompanionHomeDesktopStatus): StringResource = when (value) {
    CompanionHomeDesktopStatus.CHECKING -> Res.string.home_desktop_checking
    CompanionHomeDesktopStatus.AVAILABLE -> Res.string.home_desktop_available
    CompanionHomeDesktopStatus.UNAVAILABLE -> Res.string.home_desktop_unavailable
    CompanionHomeDesktopStatus.SECURITY_FAILURE -> Res.string.home_desktop_security_failure
}

private fun certificateStatus(value: CompanionHomeCertificateStatus): StringResource = when (value) {
    CompanionHomeCertificateStatus.CHECKING -> Res.string.home_certificate_checking
    CompanionHomeCertificateStatus.VERIFICATION_PENDING -> Res.string.home_certificate_pending
    CompanionHomeCertificateStatus.VERIFIED -> Res.string.home_certificate_verified
    CompanionHomeCertificateStatus.NEEDS_ATTENTION -> Res.string.home_certificate_attention
}

private fun tunnelStatus(value: CompanionHomeTunnelStatus): StringResource = when (value) {
    CompanionHomeTunnelStatus.INACTIVE -> Res.string.home_tunnel_inactive
    CompanionHomeTunnelStatus.CONNECTING -> Res.string.home_tunnel_connecting
    CompanionHomeTunnelStatus.ACTIVE -> Res.string.home_tunnel_active
    CompanionHomeTunnelStatus.RECONNECTING -> Res.string.home_tunnel_reconnecting
    CompanionHomeTunnelStatus.FAILED -> Res.string.home_tunnel_failed
}
