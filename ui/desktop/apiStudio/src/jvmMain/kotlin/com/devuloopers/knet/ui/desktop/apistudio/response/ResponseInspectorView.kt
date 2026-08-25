package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
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
    val onClearResponse: () -> Unit = {},
    val onClearVisibleLiveRecords: () -> Unit = {},
    val onLiveRecordSelected: (Long) -> Unit = {},
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
    if (state.liveResponse != null) {
        LiveHttpResponseView(state = state, actions = actions, modifier = modifier)
        return
    }
    val headerPairs = remember(state.headers) { state.headers.map { it.key to it.value } }
    val cookiePairs = remember(state.cookies) { state.cookies.map { it.key to it.value } }
    val isExecuting = state.executionState == ExecutionState.EXECUTING

    val responseHead = remember(state.statusCode, state.statusText, state.protocol, headerPairs) {
        state.statusCode.takeIf { it in 100..999 }?.let { statusCode ->
            ResponseHead(
                protocol = state.protocol ?: ApplicationProtocol.fromToken("HTTP/1.1"),
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
