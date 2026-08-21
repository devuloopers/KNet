package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.connectivity.model.WifiSharingFailure
import com.devuloopers.knet.connectivity.model.WifiSharingListenerFailureReason
import com.devuloopers.knet.connectivity.model.WifiSharingListenerKind
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
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
            val operationFailureCode = state.failureCode
            if (operationFailureCode != null) {
                OperationFailurePanel(operationFailureCode)
            } else {
                when (val sharing = state.sharingState) {
                    is WifiSharingState.Active -> ActiveSetupContent(sharing, onCopy)
                    WifiSharingState.Enabling -> WaitingPanel("Preparing the Wi-Fi proxy and mobile setup page.")
                    is WifiSharingState.Recovering -> RecoveryPanel(sharing)
                    WifiSharingState.Disabling -> WaitingPanel("The Wi-Fi proxy is closing with the desktop proxy.")
                    is WifiSharingState.Failed -> WifiFailurePanel(sharing)
                    is WifiSharingState.Disabled -> InactivePanel(state, onIntent)
                }
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
                "Connect, trust the KNet certificate, configure the proxy, then verify traffic.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        KNetIconButton(
            onClick = onClose,
            icon = Icons.Default.Close,
            contentDescription = "Close setup",
            size = 40.dp,
            iconSize = 24.dp,
            tint = colors.textSecondary,
        )
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
                "Scan the QR code with the phone camera, then choose the download for Android or Apple on the setup page.",
                style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
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
                Spacer(Modifier.width(6.dp))
                Text("Copy setup URL")
            }
        }
    }

    SetupPrerequisites()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlatformSteps(
            title = "Android",
            icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp)) },
            steps = listOf(
                "Scan the QR code and choose Download Android certificate.",
                "In Settings, search for Install CA certificate and install the downloaded KNet certificate.",
                "Open this Wi-Fi network's advanced settings and set Proxy to Manual.",
                "Enter ${session.proxyEndpoint.host} as the hostname and ${session.proxyEndpoint.port} as the port, without http://, then save.",
            ),
            modifier = Modifier.weight(1f),
        )
        PlatformSteps(
            title = "iPhone and iPad",
            icon = { Icon(Icons.Default.PhoneIphone, contentDescription = null, modifier = Modifier.size(20.dp)) },
            steps = listOf(
                "Scan the QR code and choose Download Apple profile.",
                "Open Settings, select Profile Downloaded, and install the KNet profile.",
                "In Certificate Trust Settings, enable full trust for the KNet Root CA.",
                "Open this Wi-Fi network, set Configure Proxy to Manual, enter ${session.proxyEndpoint.host} and ${session.proxyEndpoint.port}, leave authentication off, then save.",
            ),
            modifier = Modifier.weight(1f),
        )
    }

    SetupVerification()

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
private fun SetupPrerequisites() {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Before you begin",
                style = KNetTheme.typography.titleSmall.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                "• Connect the phone to the same reachable local network as this computer (usually the same Wi-Fi).\n" +
                    "• Keep the KNet proxy running while configuring and testing the phone.",
                style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun SetupVerification() {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.semantic.successContainer,
        shape = KNetTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Verify the connection",
                style = KNetTheme.typography.titleSmall.copy(
                    color = colors.semantic.success,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                "Open a website on the phone and confirm that a new request appears in KNet Traffic. If it does not, recheck the network, proxy endpoint, and certificate trust.",
                style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
            )
            Text(
                "Some apps reject user-installed certificates or use certificate pinning. Browser traffic can work even when those apps cannot be inspected.",
                style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.alignByBaseline(),
                        style = KNetTheme.typography.labelMedium.copy(color = colors.accent, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        step,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                        style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
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
private fun OperationFailurePanel(code: String) {
    val colors = KNetTheme.colors
    val text = when (code) {
        "certificate_unavailable" -> "The KNet root certificate is unavailable."
        else -> "The proxy operation failed ($code)."
    }
    MessagePanel(text, error = true)
}

@Composable
private fun RecoveryPanel(state: WifiSharingState.Recovering) {
    val text = "${state.failure.message(recovering = true)} KNet is retrying automatically (attempt ${state.attempt})."
    MessagePanel(text, error = false)
}

@Composable
private fun WifiFailurePanel(state: WifiSharingState.Failed) {
    val retryNote = if (state.recoverable) " KNet will keep retrying in the background." else ""
    MessagePanel(state.failure.message(recovering = false) + retryNote, error = true)
}

@Composable
private fun MessagePanel(text: String, error: Boolean) {
    val colors = KNetTheme.colors
    Text(
        text,
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
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun WifiSharingFailure.message(recovering: Boolean): String = when (this) {
    WifiSharingFailure.NetworkAddressUnavailable ->
        "No supported local IPv4 address is available. Connect this computer to a local network."
    WifiSharingFailure.CertificateUnavailable -> "The KNet root certificate is unavailable."
    WifiSharingFailure.Unexpected -> "Wi-Fi setup could not be activated."
    is WifiSharingFailure.ListenerUnavailable -> {
        val listenerName = when (listener) {
            WifiSharingListenerKind.LAN_PROXY_GATEWAY -> "Wi-Fi proxy"
            WifiSharingListenerKind.SETUP_PORTAL -> "Setup page"
        }
        when (reason) {
            WifiSharingListenerFailureReason.ADDRESS_IN_USE -> if (recovering) {
                "$listenerName endpoint ${endpoint.host}:${endpoint.port} is still being released."
            } else {
                "$listenerName endpoint ${endpoint.host}:${endpoint.port} is still in use."
            }
            WifiSharingListenerFailureReason.ADDRESS_UNAVAILABLE ->
                "The selected network address ${endpoint.host} is no longer available."
            WifiSharingListenerFailureReason.PERMISSION_DENIED ->
                "The operating system denied access to $listenerName endpoint ${endpoint.host}:${endpoint.port}."
            WifiSharingListenerFailureReason.UNKNOWN ->
                "$listenerName could not listen on ${endpoint.host}:${endpoint.port}."
        }
    }
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
