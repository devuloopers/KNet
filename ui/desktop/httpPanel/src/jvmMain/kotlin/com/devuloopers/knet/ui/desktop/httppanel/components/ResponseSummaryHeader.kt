package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.ui.core.components.button.KNetCopyDropdownButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyOption
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Top summary bar component for the response inspector rendering HTTP status badge, latency, response size, content-type, copy options, and clear response button.
 *
 * @param head Canonical response metadata.
 * @param timings Canonical exchange timings.
 * @param formattedSize Formatted response body byte size string (e.g. "2.5 KB", "300 B").
 * @param contentType Optional content-type header string (e.g. "application/json").
 * @param onClearResponse Optional event callback when user clears response output.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ResponseSummaryHeader(
    head: ResponseHead,
    timings: ExchangeTimings,
    formattedSize: String,
    contentType: String? = null,
    responseBody: String = "",
    cookies: List<Pair<String, String>> = emptyList(),
    onClearResponse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val summaryScrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(themeColors.surface)
            .border(width = 1.dp, color = themeColors.border)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Pinned Status Code Badge
        KNetHttpStatusBadge(
            statusCode = head.status.code,
            statusText = head.reasonPhrase.orEmpty(),
        )

        VerticalDivider(modifier = Modifier.padding(vertical = 10.dp))

        // 2. Horizontally Scrollable Metrics Center Area
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(summaryScrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Actual response protocol observed by the API Studio transport.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Protocol:",
                    style = typography.caption.copy(color = themeColors.textMuted),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = head.protocol.token,
                    style = typography.bodySmall.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Latency Metric
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Time:",
                    style = typography.caption.copy(color = themeColors.textMuted),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${timings.totalMillis ?: 0L} ms",
                    style = typography.bodySmall.copy(
                        color = themeColors.accent,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Response Size Metric
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Size:",
                    style = typography.caption.copy(color = themeColors.textMuted),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = formattedSize,
                    style = typography.bodySmall.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Content-Type Indicator
            if (!contentType.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Type:",
                        style = typography.caption.copy(color = themeColors.textMuted),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = contentType,
                        style = typography.bodySmall.copy(
                            color = themeColors.textSecondary,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 3. Pinned Trailing Action Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val copyOptions = buildList {
                if (head.headers.isNotEmpty()) {
                    val formattedHeaders = head.headers.joinToString("\n") { "${it.name.value}: ${it.value}" }
                    add(KNetCopyOption("Response Headers") { formattedHeaders })
                }
                if (cookies.isNotEmpty()) {
                    val formattedCookies = cookies.joinToString("; ") { "${it.first}=${it.second}" }
                    add(KNetCopyOption("Cookies") { formattedCookies })
                }
            }

            KNetCopyDropdownButton(
                primaryTextToCopy = { responseBody },
                options = copyOptions
            )

            if (onClearResponse != null) {
                KNetIconButton(
                    icon = KNetIcons.Delete,
                    contentDescription = "Clear Response",
                    onClick = onClearResponse
                )
            }
        }
    }
}
