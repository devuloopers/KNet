package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
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

/** Response navigation; clipboard commands are separately owned by the shared typed copy catalogue. */
enum class ResponseSubTab(
    /** User-facing title before optional record counts. */ val baseLabel: String,
) {
    BODY("Body"), HEADERS("Headers"), COOKIES("Cookies"), TEST_RESULTS("Test Results"), CONSOLE("Console");
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
    modifier: Modifier = Modifier,
    exportAction: (@Composable () -> Unit)? = null,
    execution: com.devuloopers.knet.application.contract.export.StudioExportSession? = null,
    executionCopyEnabled: Boolean = false,
) {
    if (state.liveResponse != null) {
        LiveHttpResponseView(state = state, actions = actions, modifier = modifier, exportAction = exportAction, copyAction = if (executionCopyEnabled) ({
            com.devuloopers.knet.ui.desktop.httppanel.copy.StudioExecutionCopyAction(execution, "http",
                primary = com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.SSE_EVENTS, label = "Copy events", eventStream = true)
        }) else null)
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

    Column(modifier = modifier) {
    if (exportAction != null) ResponseSummaryHeader(
        head = responseHead, timings = ExchangeTimings(totalMillis = state.durationMs),
        formattedSize = "${state.sizeBytes} B", responseBody = state.responseBody,
        cookies = cookiePairs, onClearResponse = actions.onClearResponse, exportAction = exportAction,
        copyAction = if (executionCopyEnabled) ({ com.devuloopers.knet.ui.desktop.httppanel.copy.StudioExecutionCopyAction(execution, "http",
            primary = when (activeSubTab) {
                ResponseSubTab.HEADERS -> com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.RESPONSE_HEADERS
                ResponseSubTab.COOKIES -> com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.RESPONSE_COOKIES
                else -> com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.RESPONSE_BODY
            }, label = when (activeSubTab) { ResponseSubTab.HEADERS -> "Copy headers"; ResponseSubTab.COOKIES -> "Copy cookies"; else -> "Copy body" }) }) else null,
    )
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
        modifier = Modifier.weight(1f),
        showSummaryHeader = exportAction == null,
        showInlineActions = exportAction == null,
    )
    }
}
