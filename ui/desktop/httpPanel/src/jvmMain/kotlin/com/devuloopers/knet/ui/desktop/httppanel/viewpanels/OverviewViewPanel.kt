package com.devuloopers.knet.ui.desktop.httppanel.viewpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.EndpointCard
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkOverviewSpec

/**
 * Reusable HTTP transaction overview inspection facade composable rendering endpoint summary card
 * and structured key-value grid metadata (Status, Protocol, Remote IP, Time, Duration, Size, Content-Type).
 *
 * @param spec Strongly-typed domain overview specification.
 * @param modifier Composable layout modifier.
 */
@Composable
fun OverviewViewPanel(
    spec: NetworkOverviewSpec,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Target Endpoint Card
        EndpointCard(
            method = spec.method,
            endpoint = spec.url
        )

        // 2. Status & Network Metadata Grid
        val isCompleted = spec.isTerminal || (spec.durationMs.isNotBlank() && spec.durationMs != "-")
        val isError = isCompleted && (spec.statusCode == 0 || spec.statusCode in 400..599)

        val statusValue = when {
            spec.statusCode > 0 -> "${spec.statusCode} ${spec.statusText}"
            isCompleted -> "ERR (${spec.statusText.ifEmpty { "Connection Error" }})"
            else -> "In Progress..."
        }

        val statusColor = when {
            spec.statusCode in 200..299 -> themeColors.semantic.success
            spec.statusCode in 300..399 -> themeColors.semantic.warning
            isError -> themeColors.semantic.error
            else -> themeColors.textPrimary
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewGridRow(
                label = "Status",
                value = statusValue,
                valueColor = statusColor
            )
            if (isError && spec.statusText.isNotBlank()) {
                OverviewGridRow(
                    label = "Error Detail",
                    value = spec.statusText,
                    valueColor = themeColors.semantic.error
                )
            }
            OverviewGridRow(
                label = "Client Protocol",
                value = spec.clientProtocol.ifEmpty { "Unknown" },
            )
            OverviewGridRow(
                label = "Upstream Protocol",
                value = spec.upstreamProtocol?.takeIf(String::isNotBlank) ?: "Pending",
            )
            OverviewGridRow(label = "Source", value = spec.origin.ifEmpty { "Proxy client" })
            spec.connectionId?.takeIf(String::isNotBlank)?.let { connectionId ->
                OverviewGridRow(label = "Connection ID", value = connectionId, isMono = true)
            }
            spec.streamId?.let { streamId ->
                OverviewGridRow(label = "Stream ID", value = streamId.toString(), isMono = true)
            }
            if (spec.remoteIp.isNotBlank()) {
                OverviewGridRow(label = "Remote IP", value = spec.remoteIp, isMono = true)
            }
            if (spec.timestamp.isNotBlank()) {
                OverviewGridRow(label = "Time", value = spec.timestamp)
            }
            OverviewGridRow(label = "Duration", value = spec.durationMs.ifEmpty { "N/A" })
            OverviewGridRow(label = "Size", value = spec.sizeBytes.ifEmpty { "N/A" })
            if (spec.contentType.isNotBlank()) {
                OverviewGridRow(
                    label = "Type",
                    value = spec.contentType,
                    isMono = true
                )
            }
        }
    }
}

@Composable
private fun OverviewGridRow(
    label: String,
    value: String,
    valueColor: Color = KNetTheme.colors.textPrimary,
    isMono: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(color = themeColors.textSecondary),
            modifier = Modifier.width(130.dp),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            style = if (isMono) typography.codeSmall.copy(color = valueColor)
            else typography.bodySmall.copy(color = valueColor),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
