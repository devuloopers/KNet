package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

private data class ErrorDetails(
    val icon: ImageVector,
    val title: String,
    val diagnosticText: String,
    val isWarning: Boolean
)

@Composable
fun NetworkExecutionErrorCard(
    failureReason: NetworkFailureReason?,
    errorMessage: String?,
    onClearResponse: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val details = when (failureReason) {
        is NetworkFailureReason.HostNotFound -> ErrorDetails(
            icon = KNetIcons.Search,
            title = "Could Not Resolve Host",
            diagnosticText = "The domain '${failureReason.host}' could not be resolved.\n\nTroubleshooting:\n• Check for typos in the request URL hostname.\n• Verify your computer has an active internet connection.\n• Check your local DNS configuration or proxy settings.",
            isWarning = false
        )
        is NetworkFailureReason.Timeout -> ErrorDetails(
            icon = KNetIcons.Refresh,
            title = "Request Execution Timed Out",
            diagnosticText = "The server did not respond within ${failureReason.timeoutMs.takeIf { it > 0 } ?: "the configured "}ms timeout limit.\n\nTroubleshooting:\n• Verify the target API server is running and reachable.\n• Increase request timeout in application settings if the endpoint is slow.",
            isWarning = true
        )
        is NetworkFailureReason.OfflineOrUnreachable -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "Server Unreachable / Connection Refused",
            diagnosticText = "Could not establish connection to the target server.\n\nTroubleshooting:\n• Verify the target server is running and listening on the port.\n• Check firewall rules and proxy settings.",
            isWarning = false
        )
        is NetworkFailureReason.InvalidUrl -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "Invalid Request URL",
            diagnosticText = "The URL '${failureReason.url}' is malformed or contains an unsupported scheme.",
            isWarning = true
        )
        is NetworkFailureReason.ProxyFailure -> ErrorDetails(
            icon = KNetIcons.Settings,
            title = "Proxy Connection Failure",
            diagnosticText = "Local proxy engine failed to forward the request.\n\nTroubleshooting:\n• Verify the KNet proxy server status in Traffic dashboard.\n• Check if proxy port is bound by another application.",
            isWarning = false
        )
        is NetworkFailureReason.SslHandshakeFailed -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "SSL / TLS Handshake Failed",
            diagnosticText = "Failed to establish a secure SSL/TLS connection.\n\nTroubleshooting:\n• Check server SSL certificate validity.\n• If using self-signed certs, verify CA certificate installation in settings.",
            isWarning = false
        )
        is NetworkFailureReason.TooManyRedirects -> ErrorDetails(
            icon = KNetIcons.Refresh,
            title = "Too Many HTTP Redirects",
            diagnosticText = "Request aborted due to an infinite redirect loop or max redirects limit.",
            isWarning = true
        )
        is NetworkFailureReason.Cancelled -> ErrorDetails(
            icon = KNetIcons.Close,
            title = "Request Execution Cancelled",
            diagnosticText = "The request execution was explicitly cancelled.",
            isWarning = true
        )
        is NetworkFailureReason.Generic -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "Network Execution Error",
            diagnosticText = formatCleanErrorMessage(failureReason.message),
            isWarning = false
        )
        null -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "Could Not Send Request",
            diagnosticText = formatCleanErrorMessage(errorMessage ?: "An unexpected execution error occurred while dispatching the request."),
            isWarning = false
        )
    }

    val accentColor = if (details.isWarning) Color(0xFFFAB387) else themeColors.semantic.error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
            .padding(spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(themeColors.surfaceVariant)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = details.icon,
                            contentDescription = details.title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = details.title,
                            style = typography.titleSmall.copy(
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Error • 0 ms",
                            style = typography.caption.copy(color = accentColor)
                        )
                    }
                }

                if (onClearResponse != null) {
                    KNetIconButton(
                        icon = KNetIcons.Delete,
                        contentDescription = "Clear Response",
                        onClick = onClearResponse
                    )
                }
            }

            HorizontalDivider(color = themeColors.border)

            // Diagnostics Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F17))
                    .padding(14.dp)
            ) {
                Text(
                    text = details.diagnosticText,
                    style = typography.bodySmall.copy(
                        color = themeColors.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

private fun formatCleanErrorMessage(rawMsg: String): String {
    return when {
        rawMsg.contains("UnresolvedAddressException") -> {
            "Unable to resolve the target web address. Please check that the URL hostname is spelled correctly and your computer has an active internet connection."
        }
        rawMsg.contains("ConnectException") || rawMsg.contains("Connection refused") -> {
            "Connection refused by target server. The server may be offline or not accepting connections."
        }
        rawMsg.contains("SocketTimeoutException") || rawMsg.contains("Timeout") -> {
            "The request execution timed out before receiving a response from the server."
        }
        rawMsg.contains("SSLException") || rawMsg.contains("Certificate") -> {
            "SSL security check failed. The server's certificate could not be verified."
        }
        else -> {
            rawMsg
                .replace(Regex("([a-z0-9]+\\.)+([A-Z][a-zA-Z0-9]+Exception)"), "$2")
                .replace(Regex("([a-z0-9]+\\.)+([A-Z][a-zA-Z0-9]+Error)"), "$2")
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }
    }
}