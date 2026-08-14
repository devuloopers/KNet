package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

private data class ErrorDetails(
    val icon: ImageVector,
    val title: String,
    val diagnosticText: String,
    val isWarning: Boolean
)

/**
 * Diagnostic error display card showing structured network failure reasons,
 * error messages, and actionable troubleshooting guidance.
 */
@Composable
fun NetworkErrorCard(
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
            diagnosticText = "The domain '${failureReason.host}' could not be resolved.\n\nTroubleshooting:\n- Check for typos in the request URL hostname.\n- Verify your computer has an active internet connection.\n- Check your local DNS configuration or proxy settings.",
            isWarning = false
        )
        is NetworkFailureReason.Timeout -> ErrorDetails(
            icon = KNetIcons.Refresh,
            title = "Request Execution Timed Out",
            diagnosticText = "The server did not respond within ${failureReason.timeoutMs.takeIf { it > 0 } ?: "the configured "}ms timeout limit.\n\nTroubleshooting:\n- Verify the target API server is running and reachable.\n- Increase request timeout in application settings if the endpoint is slow.",
            isWarning = true
        )
        is NetworkFailureReason.OfflineOrUnreachable -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "Server Unreachable / Connection Refused",
            diagnosticText = "Could not establish connection to the target server.\n\nTroubleshooting:\n- Verify the target server is running and listening on the port.\n- Check firewall rules and proxy settings.",
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
            diagnosticText = "Local proxy engine failed to forward the request.\n\nTroubleshooting:\n- Verify the KNet proxy server status in Traffic dashboard.\n- Check if proxy port is bound by another application.",
            isWarning = false
        )
        is NetworkFailureReason.SslHandshakeFailed -> ErrorDetails(
            icon = KNetIcons.Warning,
            title = "SSL / TLS Handshake Failed",
            diagnosticText = "Failed to establish a secure SSL/TLS connection.\n\nTroubleshooting:\n- Check server SSL certificate validity.\n- If using self-signed certs, verify CA certificate installation in settings.",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = details.icon,
                    contentDescription = "Error icon",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = details.title,
                style = typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeColors.textPrimary
                ),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = themeColors.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(spacing.md)
            ) {
                Text(
                    text = details.diagnosticText,
                    style = typography.bodySmall.copy(
                        color = themeColors.textSecondary,
                        lineHeight = 18.sp
                    )
                )
            }

            if (onClearResponse != null) {
                KNetButton(
                    onClick = onClearResponse,
                    variant = ButtonVariant.Secondary
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun formatCleanErrorMessage(rawMessage: String): String {
    val clean = rawMessage.trim()
    return if (clean.startsWith("Exception:") || clean.startsWith("java.lang.")) {
        clean.substringAfter(":").trim()
    } else {
        clean
    }
}
