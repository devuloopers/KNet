package com.devuloopers.knet.ui.desktop.codeeditor.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.ui.core.components.badge.KNetHttpStatusBadge
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyDropdownButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyOption
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.placeholder.KNetBodyLoadingPlaceholder
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider

private data class ErrorDetails(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val diagnosticText: String,
    val isWarning: Boolean
)

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

@Composable
private fun NetworkExecutionErrorCard(
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

/**
 * Closed set of sub-tabs supported by the unified [KNetResponseInspector].
 */
public enum class InspectorResponseSubTab(val label: String) {
    BODY("Body"),
    HEADERS("Headers"),
    COOKIES("Cookies")
}

/**
 * Unified high-density response inspector composable shared across Live Traffic Feed,
 * API Studio execution output, and live interception response viewers.
 *
 * Implements Option B architecture (stateless composable powered directly by domain [NetworkResponseSpec]).
 *
 * @param spec Strongly-typed domain response specification.
 * @param preparedBody Optional pre-processed document model produced asynchronously off-thread.
 * @param isPreparing True if background body preparation is currently running.
 * @param activeSubTab Currently selected response sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onClearResponse Optional event callback when user clears response output.
 * @param emptyMessage Description text displayed when no response is present.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun KNetResponseInspector(
    spec: NetworkResponseSpec,
    preparedBody: PreparedDocument? = null,
    isPreparing: Boolean = false,
    activeSubTab: InspectorResponseSubTab = InspectorResponseSubTab.BODY,
    onSubTabSelected: (InspectorResponseSubTab) -> Unit = {},
    onClearResponse: (() -> Unit)? = null,
    emptyMessage: String = "Select a transaction or execute a request to view response details",
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val formattedSize = remember(spec.sizeBytes) {
        val kb = spec.sizeBytes / 1024.0
        if (kb > 0) "${(kb * 100).toInt() / 100.0} KB" else "${spec.sizeBytes} B"
    }

    if (isPreparing) {
        KNetBodyLoadingPlaceholder(
            modifier = modifier
        )
    } else if (spec.isError) {
        NetworkExecutionErrorCard(
            failureReason = spec.failureReason,
            errorMessage = spec.errorMessage,
            onClearResponse = onClearResponse,
            modifier = modifier
        )
    } else if (!spec.hasResponse && spec.statusCode == 0 && spec.responseBody.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(themeColors.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = KNetIcons.Send,
                    contentDescription = "No Response",
                    modifier = Modifier.size(36.dp),
                    tint = themeColors.textMuted
                )
                Text(
                    text = "No Response Output",
                    style = typography.titleMedium.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = emptyMessage,
                    style = typography.bodySmall.copy(color = themeColors.textMuted)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(themeColors.surface)
        ) {
            // 1. Response Summary Bar
            val summaryScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(themeColors.surface)
                    .border(width = 1.dp, color = themeColors.border)
                    .horizontalScroll(summaryScrollState)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (spec.statusCode > 0) {
                    KNetHttpStatusBadge(statusCode = spec.statusCode, statusText = spec.statusText)
                    VerticalDivider(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Time: ${spec.durationMs} ms",
                    style = typography.caption.copy(color = themeColors.textSecondary)
                )

                VerticalDivider(modifier = Modifier.height(16.dp))

                Text(
                    text = "Size: $formattedSize",
                    style = typography.caption.copy(color = themeColors.textSecondary)
                )

                Box(modifier = Modifier.weight(1f))

                // Copy Action Dropdown
                when (localActiveTab) {
                    InspectorResponseSubTab.BODY -> {
                        KNetCopyButton(
                            textToCopy = spec.responseBody
                        )
                    }
                    InspectorResponseSubTab.HEADERS -> {
                        val rawTextLambda = { spec.headers.joinToString("\n") { "${it.first}: ${it.second}" } }
                        KNetCopyDropdownButton(
                            primaryTextToCopy = rawTextLambda,
                            options = listOf(
                                KNetCopyOption("RAW", rawTextLambda),
                                KNetCopyOption("JSON") {
                                    "{\n" + spec.headers.joinToString(",\n") { "  \"${it.first}\": \"${it.second}\"" } + "\n}"
                                }
                            )
                        )
                    }
                    InspectorResponseSubTab.COOKIES -> {
                        KNetCopyButton(
                            textToCopy = spec.cookies.joinToString("\n") { "${it.first}=${it.second}" }
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

            // 2. Response Sub-Tabs Row
            val tabsList = remember(spec.headers, spec.cookies) {
                listOf(
                    InspectorResponseSubTab.BODY.label,
                    "Headers (${spec.headers.size})",
                    "Cookies (${spec.cookies.size})"
                )
            }

            ScrollableTabRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(themeColors.surface)
            ) {
                InspectorResponseSubTab.entries.forEachIndexed { index, subTab ->
                    val titleText = tabsList[index]
                    KNetTab(
                        title = titleText,
                        selected = localActiveTab == subTab,
                        onClick = {
                            localActiveTab = subTab
                            onSubTabSelected(subTab)
                        }
                    )
                }
            }

            // 3. Sub-Tab Body Content Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (localActiveTab) {
                    InspectorResponseSubTab.BODY -> {
                        if (isPreparing) {
                            KNetBodyLoadingPlaceholder(
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (preparedBody != null) {
                            val activeText = preparedBody.formattedText.ifBlank { preparedBody.rawText }
                            if (activeText.isBlank() && preparedBody.previewText.isBlank()) {
                                KNetEmptyStatePlaceholder(
                                    title = "No Response Body",
                                    subtitle = "This response returned no body payload (e.g. HTTP 204 No Content or HTTP 304 Not Modified)"
                                )
                            } else {
                                KNetCodeEditor(
                                    document = preparedBody,
                                    mode = EditorMode.ReadOnly,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (spec.responseBody.isBlank()) {
                            KNetEmptyStatePlaceholder(
                                title = "No Response Body",
                                subtitle = "This response returned no body payload (e.g. HTTP 204 No Content or HTTP 304 Not Modified)"
                            )
                        } else {
                            val langHint = when {
                                spec.responseBody.trimStart().startsWith("{") || spec.responseBody.trimStart().startsWith("[") -> "json"
                                spec.responseBody.trimStart().startsWith("<") -> "html"
                                else -> "plain"
                            }

                            KNetCodeEditor(
                                code = spec.responseBody,
                                mode = EditorMode.ReadOnly,
                                languageHint = langHint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    InspectorResponseSubTab.HEADERS -> {
                        val entries = remember(spec.headers) {
                            spec.headers.mapIndexed { idx, (k, v) -> KeyValueEntry("h_$idx", k, v) }
                        }
                        KNetReadOnlyKeyValueViewer(
                            entries = entries,
                            keyHeader = "HEADER NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "This response contained no HTTP header key-value pairs.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    InspectorResponseSubTab.COOKIES -> {
                        val entries = remember(spec.cookies) {
                            spec.cookies.mapIndexed { idx, (k, v) -> KeyValueEntry("c_$idx", k, v) }
                        }
                        KNetReadOnlyKeyValueViewer(
                            entries = entries,
                            keyHeader = "COOKIE NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "This response included no Set-Cookie headers.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
