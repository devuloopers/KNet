package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.badge.KNetHttpStatusBadge
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyDropdownButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyOption
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.button.KNetSegmentedButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel

/**
 * Closed set of copy format capabilities supported by Response Inspector views.
 *
 * @property label User-facing format label.
 */
enum class CopyFormatType(val label: String) {
    RAW("RAW"),
    JSON("JSON"),
    TEXT("TEXT")
}

/**
 * Closed set of response inspector sub-tabs with strongly-typed copy format capabilities.
 *
 * @property baseLabel Display label for the sub-tab.
 * @property supportedCopyFormats List of supported [CopyFormatType] options.
 */
enum class ResponseSubTab(
    val baseLabel: String,
    val supportedCopyFormats: List<CopyFormatType>
) {
    BODY(
        baseLabel = "Body",
        supportedCopyFormats = listOf(CopyFormatType.JSON)
    ),
    HEADERS(
        baseLabel = "Headers",
        supportedCopyFormats = listOf(CopyFormatType.RAW, CopyFormatType.JSON)
    ),
    COOKIES(
        baseLabel = "Cookies",
        supportedCopyFormats = listOf(CopyFormatType.RAW, CopyFormatType.JSON)
    ),
    TEST_RESULTS(
        baseLabel = "Test Results",
        supportedCopyFormats = listOf(CopyFormatType.TEXT)
    ),
    CONSOLE(
        baseLabel = "Console",
        supportedCopyFormats = listOf(CopyFormatType.TEXT)
    );

    val isMultiFormatCopy: Boolean get() = supportedCopyFormats.size > 1
}

/**
 * Cohesive event callbacks parameter object for [ResponseInspectorView].
 */
data class ResponseInspectorActions(
    val onClearResponse: () -> Unit = {}
)

/**
 * Right-pane Response Inspector component displaying response status, metrics, responsive sub-tabs,
 * payload code viewer, header & cookie key-value tables, assertion results, and script console logs.
 *
 * Uses cohesive [ResponseInspectorState] and [ResponseInspectorActions] parameter objects to maintain clean API architecture.
 */
@Composable
fun ResponseInspectorView(
    state: ResponseInspectorState,
    actions: ResponseInspectorActions = ResponseInspectorActions(),
    activeSubTab: ResponseSubTab = ResponseSubTab.BODY,
    onSubTabSelected: (ResponseSubTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val headerPairs = remember(state.headers) { state.headers.map { it.key to it.value } }
    val cookiePairs = remember(state.cookies) { state.cookies.map { it.key to it.value } }
    val isExecuting = state.executionState == ExecutionState.EXECUTING

    val responseHead = remember(state.statusCode, state.statusText, headerPairs) {
        state.statusCode.takeIf { it in 100..999 }?.let { statusCode ->
            ResponseHead(
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                status = HttpStatus(statusCode),
                reasonPhrase = state.statusText.takeIf(String::isNotBlank),
                headers = headerPairs
                    .filter { (name, _) -> name.isNotBlank() }
                    .map { (name, value) -> HeaderField(HeaderName(name), value) },
            )
        }
    }
    val payloadSpec = remember(headerPairs, state.responseBody, isExecuting) {
        PayloadInspectionSpec.fromPayload(
            headers = headerPairs,
            rawBody = state.responseBody,
            isPreparing = isExecuting,
        )
    }

    val mappedSubTab = when (activeSubTab) {
        ResponseSubTab.BODY -> InspectorSubTab.BODY
        ResponseSubTab.HEADERS -> InspectorSubTab.HEADERS
        ResponseSubTab.COOKIES -> InspectorSubTab.COOKIES
        else -> InspectorSubTab.BODY
    }

    ResponseViewPanel(
        head = responseHead,
        timings = ExchangeTimings(totalMillis = state.durationMs),
        responseSizeBytes = state.sizeBytes,
        payloadSpec = payloadSpec,
        cookies = cookiePairs,
        failureReason = state.failureReason,
        errorMessage = state.errorMessage,
        isPreparing = isExecuting,
        activeSubTab = mappedSubTab,
        onSubTabSelected = { newSubTab ->
            val responseTab = when (newSubTab) {
                InspectorSubTab.BODY -> ResponseSubTab.BODY
                InspectorSubTab.HEADERS -> ResponseSubTab.HEADERS
                InspectorSubTab.COOKIES -> ResponseSubTab.COOKIES
                else -> ResponseSubTab.BODY
            }
            onSubTabSelected(responseTab)
        },
        onClearResponse = actions.onClearResponse,
        modifier = modifier
    )
}
