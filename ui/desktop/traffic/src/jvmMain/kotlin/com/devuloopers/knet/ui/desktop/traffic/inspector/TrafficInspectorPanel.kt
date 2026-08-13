package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.httppanel.components.EndpointCard
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkOverviewSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkTimingSpec
import com.devuloopers.knet.ui.desktop.httppanel.view.KNetOverviewInspector
import com.devuloopers.knet.ui.desktop.httppanel.view.KNetRequestInspector
import com.devuloopers.knet.ui.desktop.httppanel.view.KNetResponseInspector
import com.devuloopers.knet.ui.desktop.httppanel.view.KNetTimelineInspector
import com.devuloopers.knet.ui.desktop.traffic.model.*

/**
 * 500dp Right-Docked Inspection Panel bound strictly to :ui:core design tokens and primitives.
 */
@Composable
fun TrafficInspectorPanel(
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
    onSendToApiStudio: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    KNetTheme.dimensions

    KNetSurface(
        modifier = modifier.fillMaxSize().border(width = 1.dp, color = themeColors.border), color = themeColors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-Tabs Header
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).background(themeColors.surface)
                    .border(width = 1.dp, color = themeColors.border).horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InspectorTabButton(
                    label = "Overview",
                    isSelected = activeTab == InspectorTab.OVERVIEW,
                    onClick = { onTabSelected(InspectorTab.OVERVIEW) })
                InspectorTabButton(
                    label = "Request",
                    isSelected = activeTab == InspectorTab.REQUEST,
                    onClick = { onTabSelected(InspectorTab.REQUEST) })
                InspectorTabButton(
                    label = "Response",
                    isSelected = activeTab == InspectorTab.RESPONSE,
                    onClick = { onTabSelected(InspectorTab.RESPONSE) })
                InspectorTabButton(
                    label = "Timeline",
                    isSelected = activeTab == InspectorTab.TIMELINE,
                    onClick = { onTabSelected(InspectorTab.TIMELINE) })
            }

            // Tab Content Body
            if (selectedTransaction == null) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a transaction to inspect details",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted)
                    )
                }
            } else {
                when (activeTab) {
                    InspectorTab.OVERVIEW -> {
                        val overviewSpec = remember(selectedTransaction) {
                            val targetUrl =
                                if (selectedTransaction.host.isNotBlank()) "https://${selectedTransaction.host}${selectedTransaction.path}" else selectedTransaction.path
                            val contentType =
                                selectedTransaction.responseHeaders.map { it.key to it.value }
                                    .find { it.first.equals("Content-Type", ignoreCase = true) }?.second ?: ""
                            NetworkOverviewSpec(
                                method = selectedTransaction.method,
                                url = targetUrl,
                                statusCode = selectedTransaction.status,
                                statusText = selectedTransaction.statusText,
                                protocol = selectedTransaction.protocol,
                                remoteIp = if (selectedTransaction.host.isNotBlank()) "${selectedTransaction.host}:443" else "",
                                timestamp = selectedTransaction.dateGroup.ifEmpty { selectedTransaction.timestamp.toString() },
                                durationMs = selectedTransaction.formattedTime,
                                sizeBytes = selectedTransaction.formattedSize,
                                contentType = contentType
                            )
                        }
                        KNetOverviewInspector(
                            spec = overviewSpec,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    InspectorTab.REQUEST -> {
                        val requestSpec = remember(selectedTransaction, preparedState) {
                            val reqBody =
                                preparedState.requestBody.formattedText.ifBlank { preparedState.requestBody.rawText }
                                    .ifBlank { selectedTransaction.requestBody }
                            val headerPairs = selectedTransaction.requestHeaders.map { it.key to it.value }
                            val queryParamsList = selectedTransaction.queryParams.map { it.key to it.value.toString() }
                            val parsedMethod = try {
                                HttpMethod.valueOf(selectedTransaction.method.uppercase())
                            } catch (_: Exception) {
                                HttpMethod.CUSTOM
                            }
                            val targetUrl =
                                if (selectedTransaction.host.isNotBlank()) "https://${selectedTransaction.host}${selectedTransaction.path}" else selectedTransaction.path

                            com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
                                method = parsedMethod,
                                customMethod = if (parsedMethod == HttpMethod.CUSTOM) selectedTransaction.method else null,
                                url = targetUrl,
                                headers = headerPairs,
                                queryParams = queryParamsList,
                                bodyPayload = reqBody,
                                timestamp = selectedTransaction.timestamp
                            )
                        }
                        val mappedReqSubTab = when (activeRequestSubTab) {
                            RequestSubTab.HEADERS -> InspectorSubTab.HEADERS
                            RequestSubTab.PARAMS -> InspectorSubTab.PARAMS
                            RequestSubTab.COOKIES -> InspectorSubTab.COOKIES
                            RequestSubTab.BODY -> InspectorSubTab.BODY
                        }
                        KNetRequestInspector(
                            spec = requestSpec,
                            activeSubTab = mappedReqSubTab,
                            onSubTabSelected = { newSubTab ->
                                val legacyTab = when (newSubTab) {
                                    InspectorSubTab.HEADERS -> RequestSubTab.HEADERS
                                    InspectorSubTab.COOKIES -> RequestSubTab.COOKIES
                                    InspectorSubTab.PARAMS -> RequestSubTab.PARAMS
                                    InspectorSubTab.BODY -> RequestSubTab.BODY
                                    else -> RequestSubTab.BODY
                                }
                                onRequestSubTabSelected(legacyTab)
                            },
                            onOpenInApiStudio = { onSendToApiStudio(selectedTransaction.transactionId) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.RESPONSE -> {
                        val responseSpec = remember(selectedTransaction, preparedState) {
                            val resBody =
                                preparedState.responseBody.formattedText.ifBlank { preparedState.responseBody.rawText }
                                    .ifBlank { selectedTransaction.responseBody }
                            val headerPairs = selectedTransaction.responseHeaders.map { it.key to it.value }
                            NetworkResponseSpec(
                                statusCode = selectedTransaction.status,
                                statusText = selectedTransaction.statusText,
                                durationMs = parseDurationMs(selectedTransaction.formattedTime),
                                sizeBytes = parseSizeBytes(selectedTransaction.formattedSize),
                                responseBody = resBody,
                                headers = headerPairs
                            )
                        }
                        val mappedResSubTab = when (activeResponseSubTab) {
                            ResponseSubTab.HEADERS -> InspectorSubTab.HEADERS
                            ResponseSubTab.COOKIES -> InspectorSubTab.COOKIES
                            ResponseSubTab.BODY -> InspectorSubTab.BODY
                        }
                        KNetResponseInspector(
                            spec = responseSpec,
                            preparedBody = preparedState.responseBody,
                            isPreparing = preparedState.isPreparing,
                            activeSubTab = mappedResSubTab,
                            onSubTabSelected = { newSubTab ->
                                val legacyTab = when (newSubTab) {
                                    InspectorSubTab.HEADERS -> ResponseSubTab.HEADERS
                                    InspectorSubTab.COOKIES -> ResponseSubTab.COOKIES
                                    InspectorSubTab.BODY -> ResponseSubTab.BODY
                                    else -> ResponseSubTab.BODY
                                }
                                onResponseSubTabSelected(legacyTab)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.TIMELINE -> {
                        val timingSpec = remember(selectedTransaction.timings) {
                            val t = selectedTransaction.timings
                            NetworkTimingSpec(
                                dnsMs = t.dnsMs,
                                tcpMs = t.tcpMs,
                                tlsMs = t.tlsMs,
                                ttfbMs = t.ttfbMs,
                                downloadMs = t.downloadMs,
                                totalTimeMs = t.totalTimeMs,
                                isReusedConnection = t.isReusedConnection
                            )
                        }
                        KNetTimelineInspector(
                            timing = timingSpec,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun parseDurationMs(formattedTime: String): Long {
    val numeric = formattedTime.filter { it.isDigit() }
    return numeric.toLongOrNull() ?: 0L
}

private fun parseSizeBytes(formattedSize: String): Long {
    val numeric = formattedSize.filter { it.isDigit() }
    return numeric.toLongOrNull() ?: 0L
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
    KNetTheme.shapes

    val isBodyTab = activeSubTab == RequestSubTab.BODY

    Column(
        modifier = Modifier.fillMaxSize()
            .then(if (!isBodyTab) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Endpoint Summary Card
        EndpointCard(
            method = transaction.method, endpoint = "https://${transaction.host}${transaction.path}"
        )

        // 2. Request Sub-Menu Bar
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cookieHeader =
                transaction.requestHeaders.entries.find { it.key.equals("cookie", ignoreCase = true) }?.value
            val cookiesCount = if (!cookieHeader.isNullOrBlank()) parseCookieHeader(cookieHeader).size else 0
            SubTabChip(
                label = "Headers (${transaction.requestHeaders.size})",
                isSelected = activeSubTab == RequestSubTab.HEADERS,
                onClick = { onSubTabSelected(RequestSubTab.HEADERS) })
            SubTabChip(
                label = "Params (${transaction.queryParams.size})",
                isSelected = activeSubTab == RequestSubTab.PARAMS,
                onClick = { onSubTabSelected(RequestSubTab.PARAMS) })
            SubTabChip(
                label = "Cookies ($cookiesCount)",
                isSelected = activeSubTab == RequestSubTab.COOKIES,
                onClick = { onSubTabSelected(RequestSubTab.COOKIES) })
            SubTabChip(
                label = "Body",
                isSelected = activeSubTab == RequestSubTab.BODY,
                onClick = { onSubTabSelected(RequestSubTab.BODY) })
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
                            text = "No request headers", style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        transaction.requestHeaders.forEach { (key, value) ->
                            GridRow(label = key, value = value, isMono = true)
                        }
                    }
                }
            }

            RequestSubTab.PARAMS -> {
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
                    val cookieHeader =
                        transaction.requestHeaders.entries.find { it.key.equals("cookie", ignoreCase = true) }?.value
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
                            text = "No request cookies", style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        cookies.forEach { (name, value) ->
                            GridRow(label = name, value = value, isMono = true)
                        }
                    }
                }
            }

            RequestSubTab.BODY -> {
                val reqText = preparedState.requestBody.formattedText.ifBlank { preparedState.requestBody.rawText }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "REQUEST BODY")
                        if (reqText.isNotBlank()) {
                            KNetCopyButton(
                                textToCopy = reqText,
                                contentDescription = "Copy Request Body",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (preparedState.isPreparing) {
                        Text(
                            text = "Loading request body...",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else if (reqText.isBlank()) {
                        Text(
                            text = "No request body payload",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        KNetCodeEditor(
                            document = preparedState.requestBody,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabChip(
    label: String, isSelected: Boolean, onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Box(
        modifier = Modifier.clip(shapes.small)
            .background(if (isSelected) themeColors.border else themeColors.surfaceVariant).clickable { onClick() }
            .padding(horizontal = spacing.sm, vertical = spacing.xs).handCursor(),
        contentAlignment = Alignment.Center) {
        Text(
            text = label, style = typography.labelMedium.copy(
                color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
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
        val setCookieHeaders =
            transaction.responseHeaders.entries.filter { it.key.equals("set-cookie", ignoreCase = true) }
                .map { it.value }
        setCookieHeaders.mapNotNull { parseSetCookieHeader(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubTabChip(
                label = "Headers (${transaction.responseHeaders.size})",
                isSelected = activeSubTab == ResponseSubTab.HEADERS,
                onClick = { onSubTabSelected(ResponseSubTab.HEADERS) })
            SubTabChip(
                label = "Cookies (${parsedResponseCookies.size})",
                isSelected = activeSubTab == ResponseSubTab.COOKIES,
                onClick = { onSubTabSelected(ResponseSubTab.COOKIES) })
            SubTabChip(
                label = "Body",
                isSelected = activeSubTab == ResponseSubTab.BODY,
                onClick = { onSubTabSelected(ResponseSubTab.BODY) })
        }

        when (activeSubTab) {
            ResponseSubTab.HEADERS -> {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val headersText =
                        transaction.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
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
                            text = "No response headers", style = typography.caption.copy(color = themeColors.textMuted)
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
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
                                modifier = Modifier.fillMaxWidth().border(1.dp, themeColors.border, shapes.small)
                                    .background(themeColors.surfaceVariant).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GridRow(
                                    label = "Name", value = cookie.name, valueColor = themeColors.accent, isMono = true
                                )
                                GridRow(label = "Value", value = cookie.value, isMono = true)
                                if (cookie.domain != null) GridRow(
                                    label = "Domain", value = cookie.domain, isMono = true
                                )
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
                val respText = preparedState.responseBody.formattedText.ifBlank { preparedState.responseBody.rawText }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "RESPONSE BODY")
                        if (respText.isNotBlank()) {
                            KNetCopyButton(
                                textToCopy = respText,
                                contentDescription = "Copy Response Body",
                                size = 14.dp,
                                tint = themeColors.textSecondary
                            )
                        }
                    }

                    if (preparedState.isPreparing) {
                        Text(
                            text = "Loading response body...",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else if (respText.isBlank()) {
                        Text(
                            text = "No response body payload",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    } else {
                        KNetCodeEditor(
                            document = preparedState.responseBody,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }
        }
    }
}




@Composable
private fun TimelineWaterfallRow(
    label: String, durationMs: Long, totalMs: Long, color: Color
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val fraction = (durationMs.toFloat() / totalMs.toFloat()).coerceIn(0.02f, 1.0f)

    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(color = themeColors.textSecondary),
            modifier = Modifier.width(130.dp)
        )

        Box(
            modifier = Modifier.weight(1f).height(18.dp).background(Color(0xFF1E1E2E), shape = shapes.small)
                .padding(horizontal = 2.dp), contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.fillMaxHeight(0.7f).fillMaxWidth(fraction).clip(shapes.small).background(color)
            )
        }

        Text(
            text = "$durationMs ms",
            style = typography.codeSmall.copy(color = themeColors.textPrimary),
            modifier = Modifier.width(64.dp).padding(start = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val typography = KNetTheme.typography
    val themeColors = KNetTheme.colors

    Text(
        text = title, style = typography.caption.copy(
            color = themeColors.textSecondary, fontWeight = FontWeight.SemiBold
        )
    )
}

@Composable
private fun InspectorTabButton(
    label: String, isSelected: Boolean, onClick: () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Column(
        modifier = Modifier.padding(vertical = 4.dp).clip(shapes.small)
            .background(if (isSelected) themeColors.surfaceVariant else Color.Transparent).clickable { onClick() }
            .padding(horizontal = spacing.md, vertical = 4.dp).handCursor(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label, style = typography.bodyMedium.copy(
                color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
        )

        Box(
            modifier = Modifier.padding(top = 4.dp).width(28.dp).height(3.dp).clip(shapes.pill)
                .background(if (isSelected) themeColors.accent else Color.Transparent)
        )
    }
}

@Composable
private fun GridRow(
    label: String, value: String, valueColor: Color = KNetTheme.colors.textPrimary, isMono: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
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
    return cookieHeader.split(";").mapNotNull {
        val parts = it.trim().split("=", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()
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
    val isJson = contentType.isNullOrBlank() || contentType.contains(
        "json", ignoreCase = true
    ) || trimmed.startsWith("{") || trimmed.startsWith("[")
    return if (isJson) {
        JsonBodyFormatter().prettyPrintJson(trimmed).trimEnd()
    } else {
        trimmed
    }
}


