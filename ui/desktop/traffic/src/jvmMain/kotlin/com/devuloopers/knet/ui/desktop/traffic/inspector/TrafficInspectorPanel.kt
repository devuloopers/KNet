package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.http.components.EndpointCard
import com.devuloopers.knet.ui.desktop.traffic.model.InspectorPreparedState
import com.devuloopers.knet.ui.desktop.traffic.model.InspectorTab
import com.devuloopers.knet.ui.desktop.traffic.model.PreviewFormatMode
import com.devuloopers.knet.ui.desktop.traffic.model.RequestSubTab
import com.devuloopers.knet.ui.desktop.traffic.model.ResponseSubTab

/**
 * 500dp Right-Docked Inspection Panel bound strictly to :ui:core design tokens and primitives.
 */
@Composable
public fun TrafficInspectorPanel(
    selectedTransaction: TrafficItemUiState?,
    activeTab: InspectorTab,
    activeRequestSubTab: RequestSubTab = RequestSubTab.HEADERS,
    activeResponseSubTab: ResponseSubTab = ResponseSubTab.BODY,
    previewMode: PreviewFormatMode,
    preparedState: InspectorPreparedState = InspectorPreparedState(),
    onTabSelected: (InspectorTab) -> Unit,
    onRequestSubTabSelected: (RequestSubTab) -> Unit = {},
    onResponseSubTabSelected: (ResponseSubTab) -> Unit = {},
    onPreviewModeSelected: (PreviewFormatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val dimensions = KNetTheme.dimensions

    KNetSurface(
        modifier = modifier
            .fillMaxSize()
            .border(width = 1.dp, color = themeColors.border),
        color = themeColors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-Tabs Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(themeColors.surface)
                    .border(width = 1.dp, color = themeColors.border)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InspectorTabButton(
                    label = "Overview",
                    isSelected = activeTab == InspectorTab.OVERVIEW,
                    onClick = { onTabSelected(InspectorTab.OVERVIEW) }
                )
                InspectorTabButton(
                    label = "Request",
                    isSelected = activeTab == InspectorTab.REQUEST,
                    onClick = { onTabSelected(InspectorTab.REQUEST) }
                )
                InspectorTabButton(
                    label = "Response",
                    isSelected = activeTab == InspectorTab.RESPONSE,
                    onClick = { onTabSelected(InspectorTab.RESPONSE) }
                )
                InspectorTabButton(
                    label = "Timeline",
                    isSelected = activeTab == InspectorTab.TIMELINE,
                    onClick = { onTabSelected(InspectorTab.TIMELINE) }
                )
            }

            // Tab Content Body
            if (selectedTransaction == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a transaction to inspect details",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted)
                    )
                }
            } else {
                when (activeTab) {
                    InspectorTab.OVERVIEW -> OverviewTabContent(selectedTransaction)
                    InspectorTab.REQUEST -> RequestTabContent(
                        transaction = selectedTransaction,
                        activeSubTab = activeRequestSubTab,
                        preparedState = preparedState,
                        onSubTabSelected = onRequestSubTabSelected
                    )
                    InspectorTab.RESPONSE -> ResponseTabContent(
                        transaction = selectedTransaction,
                        activeSubTab = activeResponseSubTab,
                        previewMode = previewMode,
                        preparedState = preparedState,
                        onSubTabSelected = onResponseSubTabSelected,
                        onPreviewModeSelected = onPreviewModeSelected
                    )
                    InspectorTab.TIMELINE -> TimelineTabContent(selectedTransaction)
                }
            }
        }
    }
}

@Composable
private fun OverviewTabContent(transaction: TrafficItemUiState) {
    val themeColors = KNetTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        EndpointCard(
            method = transaction.method,
            endpoint = "https://${transaction.host}${transaction.path}"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GridRow(
                label = "Status",
                value = if (transaction.status > 0) "${transaction.status} ${transaction.statusText}" else "In Progress...",
                valueColor = if (transaction.status in 200..299) themeColors.semantic.success else themeColors.textPrimary
            )
            GridRow(label = "Protocol", value = transaction.protocol)
            GridRow(label = "Remote IP", value = "${transaction.host}:443", isMono = true)
            GridRow(label = "Time", value = transaction.dateGroup.ifEmpty { "N/A" })
            GridRow(label = "Duration", value = transaction.formattedTime)
            GridRow(label = "Size", value = transaction.formattedSize)
            GridRow(
                label = "Type",
                value = transaction.responseHeaders["Content-Type"] ?: "application/json; charset=utf-8",
                isMono = true
            )
        }
    }
}

@Composable
private fun RequestTabContent(
    transaction: TrafficItemUiState,
    activeSubTab: RequestSubTab,
    preparedState: InspectorPreparedState,
    onSubTabSelected: (RequestSubTab) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val isBodyTab = activeSubTab == RequestSubTab.BODY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isBodyTab) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Endpoint Summary Card
        EndpointCard(
            method = transaction.method,
            endpoint = "https://${transaction.host}${transaction.path}"
        )

        // 2. Request Sub-Menu Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cookieHeader = transaction.requestHeaders.entries.find { it.key.equals("cookie", ignoreCase = true) }?.value
            val cookiesCount = if (!cookieHeader.isNullOrBlank()) parseCookieHeader(cookieHeader).size else 0
            SubTabChip(
                label = "Headers (${transaction.requestHeaders.size})",
                isSelected = activeSubTab == RequestSubTab.HEADERS,
                onClick = { onSubTabSelected(RequestSubTab.HEADERS) }
            )
            SubTabChip(
                label = "Query (${transaction.queryParams.size})",
                isSelected = activeSubTab == RequestSubTab.QUERY,
                onClick = { onSubTabSelected(RequestSubTab.QUERY) }
            )
            SubTabChip(
                label = "Cookies ($cookiesCount)",
                isSelected = activeSubTab == RequestSubTab.COOKIES,
                onClick = { onSubTabSelected(RequestSubTab.COOKIES) }
            )
            SubTabChip(
                label = "Body",
                isSelected = activeSubTab == RequestSubTab.BODY,
                onClick = { onSubTabSelected(RequestSubTab.BODY) }
            )
        }

        HorizontalDivider(color = themeColors.border)

        // 3. Dynamic Sub-Tab Content
        when (activeSubTab) {
            RequestSubTab.HEADERS -> {
                // Request Headers Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val headersText = transaction.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "REQUEST HEADERS (${transaction.requestHeaders.size})")
                        if (transaction.requestHeaders.isNotEmpty()) {
                            KNetCopyButton(
                                textToCopy = headersText,
                                contentDescription = "Copy Request Headers",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (transaction.requestHeaders.isEmpty()) {
                        Text(
                            text = "No request headers",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        transaction.requestHeaders.forEach { (key, value) ->
                            GridRow(label = key, value = value, isMono = true)
                        }
                    }
                }
            }
            RequestSubTab.QUERY -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val queryText = transaction.queryParams.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "QUERY PARAMETERS (${transaction.queryParams.size})")
                        if (transaction.queryParams.isNotEmpty()) {
                            KNetCopyButton(
                                textToCopy = queryText,
                                contentDescription = "Copy Query Parameters",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (transaction.queryParams.isEmpty()) {
                        Text(
                            text = "No URL query parameters",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        transaction.queryParams.forEach { (key, value) ->
                            GridRow(label = key, value = value.toString(), isMono = true)
                        }
                    }
                }
            }
            RequestSubTab.COOKIES -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cookieHeader = transaction.requestHeaders.entries.find { it.key.equals("cookie", ignoreCase = true) }?.value
                    val cookies = if (!cookieHeader.isNullOrBlank()) parseCookieHeader(cookieHeader) else emptyMap()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "REQUEST COOKIES (${cookies.size})")
                        if (cookies.isNotEmpty() && !cookieHeader.isNullOrBlank()) {
                            KNetCopyButton(
                                textToCopy = cookieHeader,
                                contentDescription = "Copy Cookie Header",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (cookies.isEmpty()) {
                        Text(
                            text = "No request cookies",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        cookies.forEach { (name, value) ->
                            GridRow(label = name, value = value, isMono = true)
                        }
                    }
                }
            }
            RequestSubTab.BODY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "REQUEST BODY")
                        if (transaction.requestBody.isNotBlank()) {
                            KNetCopyButton(
                                textToCopy = transaction.requestBody,
                                contentDescription = "Copy Request Body",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (transaction.requestBody.isBlank()) {
                        Text(
                            text = "No request body payload",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        KNetCodeEditor(
                            document = preparedState.requestBody,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Box(
        modifier = Modifier
            .clip(shapes.small)
            .background(if (isSelected) themeColors.border else themeColors.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = spacing.sm, vertical = spacing.xs)
            .handCursor(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.labelMedium.copy(
                color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun ResponseTabContent(
    transaction: TrafficItemUiState,
    activeSubTab: ResponseSubTab,
    previewMode: PreviewFormatMode,
    preparedState: InspectorPreparedState,
    onSubTabSelected: (ResponseSubTab) -> Unit,
    onPreviewModeSelected: (PreviewFormatMode) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val parsedResponseCookies = remember(transaction.responseHeaders) {
        val setCookieHeaders = transaction.responseHeaders.entries.filter { it.key.equals("set-cookie", ignoreCase = true) }.map { it.value }
        setCookieHeaders.mapNotNull { parseSetCookieHeader(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubTabChip(
                label = "Headers (${transaction.responseHeaders.size})",
                isSelected = activeSubTab == ResponseSubTab.HEADERS,
                onClick = { onSubTabSelected(ResponseSubTab.HEADERS) }
            )
            SubTabChip(
                label = "Cookies (${parsedResponseCookies.size})",
                isSelected = activeSubTab == ResponseSubTab.COOKIES,
                onClick = { onSubTabSelected(ResponseSubTab.COOKIES) }
            )
            SubTabChip(
                label = "Body",
                isSelected = activeSubTab == ResponseSubTab.BODY,
                onClick = { onSubTabSelected(ResponseSubTab.BODY) }
            )
        }

        when (activeSubTab) {
            ResponseSubTab.HEADERS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val headersText = transaction.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "RESPONSE HEADERS (${transaction.responseHeaders.size})")
                        if (transaction.responseHeaders.isNotEmpty()) {
                            KNetCopyButton(
                                textToCopy = headersText,
                                contentDescription = "Copy Response Headers",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (transaction.responseHeaders.isEmpty()) {
                        Text(
                            text = "No response headers",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        transaction.responseHeaders.forEach { (key, value) ->
                            GridRow(label = key, value = value, isMono = true)
                        }
                    }
                }
            }
            ResponseSubTab.COOKIES -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "RESPONSE COOKIES (${parsedResponseCookies.size})")
                    }

                    if (parsedResponseCookies.isEmpty()) {
                        Text(
                            text = "No response cookies set",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        parsedResponseCookies.forEach { cookie ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, themeColors.border, shapes.small)
                                    .background(themeColors.surfaceVariant)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GridRow(label = "Name", value = cookie.name, valueColor = themeColors.accent, isMono = true)
                                GridRow(label = "Value", value = cookie.value, isMono = true)
                                if (cookie.domain != null) GridRow(label = "Domain", value = cookie.domain, isMono = true)
                                if (cookie.path != null) GridRow(label = "Path", value = cookie.path, isMono = true)
                                if (cookie.expires != null) GridRow(label = "Expires", value = cookie.expires)
                                if (cookie.maxAge != null) GridRow(label = "Max-Age", value = "${cookie.maxAge}s")
                                if (cookie.secure || cookie.httpOnly) {
                                    val attributes = buildList {
                                        if (cookie.secure) add("Secure")
                                        if (cookie.httpOnly) add("HttpOnly")
                                    }.joinToString(", ")
                                    GridRow(label = "Attributes", value = attributes)
                                }
                            }
                        }
                    }
                }
            }
            ResponseSubTab.BODY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "RESPONSE BODY")
                        if (transaction.responseBody.isNotBlank()) {
                            KNetCopyButton(
                                textToCopy = transaction.responseBody,
                                contentDescription = "Copy Response Body",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (transaction.responseBody.isBlank()) {
                        Text(
                            text = "No response body payload",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        KNetCodeEditor(
                            document = preparedState.responseBody,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TimelineTabContent(transaction: TrafficItemUiState) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val timings = transaction.timings
    val totalMs = timings.totalTimeMs.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "NETWORK TIMING BREAKDOWN")
            if (timings.isReusedConnection || (timings.dnsMs == 0L && timings.tcpMs == 0L && timings.tlsMs == 0L)) {
                Box(
                    modifier = Modifier
                        .clip(shapes.pill)
                        .background(Color(0xFF313244))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Reused Connection",
                        style = typography.caption.copy(color = Color(0xFFA6ADC8), fontSize = 10.sp)
                    )
                }
            }
        }

        TimelineWaterfallRow(
            label = "DNS Resolution",
            durationMs = timings.dnsMs,
            totalMs = totalMs,
            color = Color(0xFF89B4FA)
        )
        TimelineWaterfallRow(
            label = "TCP Connect",
            durationMs = timings.tcpMs,
            totalMs = totalMs,
            color = Color(0xFF89DCEB)
        )
        TimelineWaterfallRow(
            label = "TLS Handshake",
            durationMs = timings.tlsMs,
            totalMs = totalMs,
            color = Color(0xFFA6E3A1)
        )
        TimelineWaterfallRow(
            label = "TTFB (Wait)",
            durationMs = timings.ttfbMs,
            totalMs = totalMs,
            color = Color(0xFFF9E2AF)
        )
        TimelineWaterfallRow(
            label = "Content Download",
            durationMs = timings.downloadMs,
            totalMs = totalMs,
            color = Color(0xFF74C7EC)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Latency",
                style = typography.bodySmall.copy(color = themeColors.textSecondary, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${totalMs} ms",
                style = typography.bodySmall.copy(color = themeColors.accent, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun TimelineWaterfallRow(
    label: String,
    durationMs: Long,
    totalMs: Long,
    color: Color
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val fraction = (durationMs.toFloat() / totalMs.toFloat()).coerceIn(0.02f, 1.0f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(color = themeColors.textSecondary),
            modifier = Modifier.width(130.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(Color(0xFF1E1E2E), shape = shapes.small)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .fillMaxWidth(fraction)
                    .clip(shapes.small)
                    .background(color)
            )
        }

        Text(
            text = "${durationMs} ms",
            style = typography.codeSmall.copy(color = themeColors.textPrimary),
            modifier = Modifier
                .width(64.dp)
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val typography = KNetTheme.typography
    val themeColors = KNetTheme.colors

    Text(
        text = title,
        style = typography.caption.copy(
            color = themeColors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
    )
}

@Composable
private fun InspectorTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(shapes.small)
            .background(if (isSelected) themeColors.surfaceVariant else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = spacing.md, vertical = 4.dp)
            .handCursor(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = typography.bodyMedium.copy(
                color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )

        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(shapes.pill)
                .background(if (isSelected) themeColors.accent else Color.Transparent)
        )
    }
}

@Composable
private fun GridRow(
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
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = if (isMono) typography.codeSmall.copy(color = valueColor) else typography.bodySmall.copy(color = valueColor)
        )
    }
}

internal fun parseCookieHeader(cookieHeader: String): Map<String, String> {
    return cookieHeader.split(";")
        .mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .toMap()
}

internal data class ParsedResponseCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: String? = null,
    val maxAge: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false
)

internal fun parseSetCookieHeader(setCookieHeader: String): ParsedResponseCookie? {
    if (setCookieHeader.isBlank()) return null
    val parts = setCookieHeader.split(";")
    val firstPart = parts.firstOrNull()?.trim() ?: return null
    val firstEq = firstPart.indexOf('=')
    if (firstEq == -1) return null
    val name = firstPart.substring(0, firstEq).trim()
    val value = firstPart.substring(firstEq + 1).trim()

    var domain: String? = null
    var path: String? = null
    var expires: String? = null
    var maxAge: Long? = null
    var secure = false
    var httpOnly = false

    for (i in 1 until parts.size) {
        val attribute = parts[i].trim()
        val eqIndex = attribute.indexOf('=')
        if (eqIndex == -1) {
            val key = attribute.lowercase()
            if (key == "secure") secure = true
            if (key == "httponly") httpOnly = true
        } else {
            val key = attribute.substring(0, eqIndex).trim().lowercase()
            val valStr = attribute.substring(eqIndex + 1).trim()
            when (key) {
                "domain" -> domain = valStr
                "path" -> path = valStr
                "expires" -> expires = valStr
                "max-age" -> maxAge = valStr.toLongOrNull()
            }
        }
    }

    return ParsedResponseCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        expires = expires,
        maxAge = maxAge,
        secure = secure,
        httpOnly = httpOnly
    )
}


/**
 * Detects syntax highlighter language token from Content-Type MIME string.
 */
private fun detectLanguageHint(contentType: String?): String {
    if (contentType.isNullOrBlank()) return "json"
    val lower = contentType.lowercase()
    return when {
        lower.contains("json") -> "json"
        lower.contains("html") -> "html"
        lower.contains("xml") -> "xml"
        lower.contains("javascript") || lower.contains("js") -> "js"
        lower.contains("css") -> "css"
        else -> "json"
    }
}

/**
 * Auto-pretty-prints JSON request/response body text and trims trailing newlines.
 */
internal fun formatBodyPayload(contentType: String?, rawBody: String): String {
    if (rawBody.isBlank()) return ""
    val trimmed = rawBody.trimEnd()
    val isJson = contentType.isNullOrBlank() || contentType.contains("json", ignoreCase = true) || trimmed.startsWith("{") || trimmed.startsWith("[")
    return if (isJson) {
        JsonBodyFormatter().prettyPrintJson(trimmed).trimEnd()
    } else {
        trimmed
    }
}


