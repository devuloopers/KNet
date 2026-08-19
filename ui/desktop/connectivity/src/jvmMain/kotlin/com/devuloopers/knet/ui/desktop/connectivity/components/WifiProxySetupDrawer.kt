package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceOperation
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceUiState

/** Right-side setup drawer containing the stable QR code and manual mobile proxy instructions. */
@Composable
internal fun WifiProxySetupDrawer(
    state: ConnectDeviceUiState,
    onIntent: (ConnectDeviceIntent) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KNetSideDrawer(
        visible = state.isSetupDrawerVisible,
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
            DrawerHeader(onClose = { onIntent(ConnectDeviceIntent.CloseSetup) })
            when (val sharing = state.sharingState) {
                is WifiSharingState.Active -> ActiveSetupContent(sharing, onCopy)
                WifiSharingState.Enabling -> WaitingPanel("Preparing the Wi-Fi proxy and mobile setup page.")
                WifiSharingState.Disabling -> WaitingPanel("The Wi-Fi proxy is closing with the desktop proxy.")
                is WifiSharingState.Failed -> FailurePanel(sharing.code)
                is WifiSharingState.Disabled -> InactivePanel(state, onIntent)
            }
        }
    }
}

@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    val colors = KNetTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KNetSurface(
            modifier = Modifier.size(42.dp),
            color = colors.semantic.infoContainer,
            shape = KNetTheme.shapes.large,
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = colors.accent, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                "Wi-Fi Proxy Setup",
                style = KNetTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Scan once, install the certificate, then configure the phone Wi-Fi proxy.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close setup", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun ActiveSetupContent(
    sharing: WifiSharingState.Active,
    onCopy: (String) -> Unit,
) {
    val session = sharing.session
    val colors = KNetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KNetSurface(
            modifier = Modifier.size(224.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shape = KNetTheme.shapes.large,
        ) {
            SetupQrCode(value = session.setupUrl, modifier = Modifier.size(216.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            StatusLabel("Wi-Fi proxy ready", colors.semantic.success)
            EndpointPanel(
                host = session.proxyEndpoint.host,
                port = session.proxyEndpoint.port,
            )
            Text(
                "Scan the QR code with the phone camera. The page detects neither the device nor its platform; it simply offers both safe download formats.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            SelectionContainer {
                Text(
                    session.setupUrl,
                    style = KNetTheme.typography.caption.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KNetButton(
                onClick = { onCopy(session.setupUrl) },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("Copy setup URL")
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlatformSteps(
            title = "Android",
            icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp)) },
            steps = listOf(
                "Scan the QR and download the Android certificate.",
                "Install it under Security as a CA certificate.",
                "Edit this Wi-Fi network and choose Manual proxy.",
                "Enter ${session.proxyEndpoint.host} and port ${session.proxyEndpoint.port}.",
            ),
            modifier = Modifier.weight(1f),
        )
        PlatformSteps(
            title = "iPhone and iPad",
            icon = { Icon(Icons.Default.PhoneIphone, contentDescription = null, modifier = Modifier.size(20.dp)) },
            steps = listOf(
                "Scan the QR and download the Apple profile.",
                "Install it from Settings, Profile Downloaded.",
                "Enable full trust in Certificate Trust Settings.",
                "Set Configure Proxy to Manual with the endpoint shown.",
            ),
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        "Any device that can reach this exact desktop network address may use the proxy while it is running.",
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.semantic.warningContainer, KNetTheme.shapes.small)
            .padding(10.dp),
        style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EndpointPanel(host: String, port: Int) {
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
                "Manual proxy server",
                style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "$host:$port",
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
private fun PlatformSteps(
    title: String,
    icon: @Composable () -> Unit,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = modifier,
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(
                    title,
                    style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        style = KNetTheme.typography.labelMedium.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        step,
                        style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InactivePanel(
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
            if (state.isProxyRunning) "Waiting for a reachable local network" else "Start the proxy to create the setup QR",
            style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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

@Composable
private fun WaitingPanel(message: String) {
    val colors = KNetTheme.colors
    Text(
        message,
        modifier = Modifier.fillMaxWidth().background(colors.semantic.infoContainer, KNetTheme.shapes.small).padding(14.dp),
        style = KNetTheme.typography.bodySmall.copy(color = colors.semantic.info),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FailurePanel(code: String) {
    val colors = KNetTheme.colors
    val text = when (code) {
        "wifi_address_unavailable" -> "No supported local IPv4 address is available. Connect this computer to Wi-Fi and try again."
        "certificate_unavailable" -> "The KNet root certificate is unavailable."
        "wifi_bind_failed" -> "The Wi-Fi proxy or setup-page port is already in use. KNet will retry automatically."
        else -> "Wi-Fi setup is unavailable ($code). KNet will retry automatically."
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(colors.semantic.errorContainer, KNetTheme.shapes.small).padding(14.dp),
        style = KNetTheme.typography.bodySmall.copy(color = colors.semantic.error),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusLabel(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        KNetSurface(Modifier.size(7.dp), color = color, shape = KNetTheme.shapes.pill) {}
        Text(
            label,
            style = KNetTheme.typography.labelMedium.copy(color = color, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}
