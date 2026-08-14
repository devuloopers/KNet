package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetHttpStatusBadge
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState

/**
 * Cohesive actions parameter object for [ResponseEditorPanel].
 *
 * Encapsulates all user interaction and mutation callbacks for response editing.
 *
 * @property onStatusCodeChanged Callback invoked when the user modifies the HTTP status code.
 * @property onStatusTextChanged Callback invoked when the user modifies the HTTP status message text.
 * @property onBodyStateChanged Callback invoked when the [ResponseBodyState] payload or body mode changes.
 * @property onBodyPayloadChanged Callback invoked when the raw payload text changes.
 * @property onHeadersChanged Callback invoked when response transport headers are added, edited, or deleted.
 * @property onCookiesChanged Callback invoked when response cookies are added, edited, or deleted.
 * @property onSubTabSelected Callback invoked when the user switches between response sub-tabs.
 */
data class ResponseEditorPanelActions(
    val onStatusCodeChanged: (Int) -> Unit = {},
    val onStatusTextChanged: (String) -> Unit = {},
    val onBodyStateChanged: (ResponseBodyState) -> Unit = {},
    val onBodyPayloadChanged: (String) -> Unit = {},
    val onHeadersChanged: (List<Pair<String, String>>) -> Unit = {},
    val onCookiesChanged: (List<Pair<String, String>>) -> Unit = {},
    val onSubTabSelected: (InspectorSubTab) -> Unit = {}
)

/**
 * Unified interactive HTTP response editor facade composable.
 *
 * Supports live authoring and editing of response status code, status message text,
 * transport headers, cookies, and response body payloads via [ResponseBodyEditor]. Used for
 * in-flight breakpoint modification, mock servers, and response simulation.
 *
 * @param statusCode HTTP response status code integer (e.g. 200, 404, 500).
 * @param statusText HTTP response status description (e.g. "OK", "Not Found").
 * @param bodyState Structured immutable [ResponseBodyState] holding the response payload and active mode.
 * @param headers List of response transport headers as key-value pairs.
 * @param cookies List of response cookies as key-value pairs.
 * @param activeSubTab Currently selected response sub-tab ([InspectorSubTab.BODY], [InspectorSubTab.HEADERS], [InspectorSubTab.COOKIES]).
 * @param actions Cohesive action callbacks for handling state mutations.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ResponseEditorPanel(
    statusCode: Int = 200,
    statusText: String = "OK",
    bodyState: ResponseBodyState = ResponseBodyState(),
    headers: List<Pair<String, String>> = emptyList(),
    cookies: List<Pair<String, String>> = emptyList(),
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    actions: ResponseEditorPanelActions = ResponseEditorPanelActions(),
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val headerEntries = remember(headers) {
        headers.mapIndexed { idx, (k, v) -> KeyValueEntry(id = "resp_header_$idx", key = k, value = v) }
    }

    val cookieEntries = remember(cookies) {
        cookies.mapIndexed { idx, (k, v) -> KeyValueEntry(id = "resp_cookie_$idx", key = k, value = v) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Status Bar Header (Status Code & Status Text inputs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.surface)
                .border(width = 1.dp, color = themeColors.border)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )

                // Status Code Text Field
                KNetTextField(
                    value = if (statusCode > 0) statusCode.toString() else "",
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(3)
                        val parsed = filtered.toIntOrNull() ?: 0
                        actions.onStatusCodeChanged(parsed)
                        if (parsed in DEFAULT_STATUS_TEXTS) {
                            actions.onStatusTextChanged(DEFAULT_STATUS_TEXTS.getValue(parsed))
                        }
                    },
                    modifier = Modifier.width(72.dp),
                    config = InputFieldConfig(
                        placeholder = "200",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )

                // Status Text Input Field
                KNetTextField(
                    value = statusText,
                    onValueChange = { actions.onStatusTextChanged(it) },
                    modifier = Modifier.width(180.dp),
                    config = InputFieldConfig(
                        placeholder = "OK",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )
            }

            // Status Badge Preview
            KNetHttpStatusBadge(
                statusCode = statusCode,
                statusText = statusText
            )
        }

        HorizontalDivider(color = themeColors.border)

        // 2. Sub-Tabs Header Navigation Bar
        InspectorSubTabRow(
            tabs = InspectorSubTab.ResponseTabs,
            activeTab = localActiveTab,
            onTabSelected = { newTab ->
                localActiveTab = newTab
                actions.onSubTabSelected(newTab)
            },
            headerCount = headers.size,
            cookieCount = cookies.size
        )

        HorizontalDivider(color = themeColors.border)

        // 3. Active Sub-Tab Editor Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (localActiveTab) {
                InspectorSubTab.BODY -> {
                    ResponseBodyEditor(
                        state = bodyState,
                        onStateChange = { updatedBodyState ->
                            actions.onBodyStateChanged(updatedBodyState)
                            actions.onBodyPayloadChanged(updatedBodyState.payloadText)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = headerEntries,
                        keyHeader = "HEADER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No response headers configured. Click '+ Add Header' to start.",
                        addLabel = "Add Header",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = headerEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onHeadersChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = headers + ("" to "")
                            actions.onHeadersChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = headers.toMutableList().apply { removeAt(targetIndex) }
                            actions.onHeadersChanged(updatedList)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.COOKIES -> {
                    KNetKeyValueEditor(
                        entries = cookieEntries,
                        keyHeader = "COOKIE NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No response cookies configured. Click '+ Add Cookie' to start.",
                        addLabel = "Add Cookie",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = cookieEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onCookiesChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = cookies + ("" to "")
                            actions.onCookiesChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = cookies.toMutableList().apply { removeAt(targetIndex) }
                            actions.onCookiesChanged(updatedList)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    // Fallback to Response Body Editor
                    ResponseBodyEditor(
                        state = bodyState,
                        onStateChange = { updatedBodyState ->
                            actions.onBodyStateChanged(updatedBodyState)
                            actions.onBodyPayloadChanged(updatedBodyState.payloadText)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private val DEFAULT_STATUS_TEXTS: Map<Int, String> = mapOf(
    200 to "OK",
    201 to "Created",
    202 to "Accepted",
    204 to "No Content",
    301 to "Moved Permanently",
    302 to "Found",
    304 to "Not Modified",
    400 to "Bad Request",
    401 to "Unauthorized",
    403 to "Forbidden",
    404 to "Not Found",
    405 to "Method Not Allowed",
    422 to "Unprocessable Entity",
    429 to "Too Many Requests",
    500 to "Internal Server Error",
    502 to "Bad Gateway",
    503 to "Service Unavailable",
    504 to "Gateway Timeout"
)
