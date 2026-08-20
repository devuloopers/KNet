package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.EndpointCard
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState

/**
 * Slide-out desktop drawer displaying active in-flight suspended HTTP transactions.
 * Supports Master-Detail layout with an animated left Queue Sidebar when multiple transactions are waiting,
 * and reuses `:ui:desktop:httpPanel` editors with [FORWARD], [DROP], and [DISABLE RULE] controls.
 *
 * @param events List of currently waiting intercepted transactions in the queue.
 * @param activeEvent The currently selected active transaction being inspected and edited.
 * @param isVisible Whether the drawer should be shown.
 * @param onSelectEvent Callback when selecting an item from the queue sidebar.
 * @param onDropItem Callback when an individual item's drop button is clicked in the queue sidebar.
 * @param onDropAll Callback when the bulk "Drop All" button is clicked.
 * @param onForwardRequest Callback when forwarding a request with modifications.
 * @param onForwardResponse Callback when forwarding a response with modifications.
 * @param onDrop Callback when dropping the current active transaction.
 * @param onDisableRule Callback when disabling the matching rule and dropping the current transaction.
 * @param onDismiss Callback when dismissing/closing the drawer.
 * @param modifier Optional layout modifier.
 */
@Composable
fun LiveInterceptDrawer(
    events: List<PendingBreakpoint>,
    activeEvent: PendingBreakpoint?,
    isVisible: Boolean,
    resolvedPayloads: Map<String, ResolvedInterceptPayload> = emptyMap(),
    onSelectEvent: (eventId: String) -> Unit,
    onDropItem: (eventId: String) -> Unit,
    onDropAll: () -> Unit,
    onForwardRequest: (modifiedRequest: BreakpointRequestEdit) -> Unit,
    onForwardResponse: (modifiedResponse: BreakpointResponseEdit) -> Unit,
    onDrop: () -> Unit,
    onDisableRule: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    // Retain the last active event and non-empty queue snapshot so that during the slide-out
    // exit animation, Compose continues to render the full drawer UI smoothly without an abrupt 0x0 collapse.
    var lastActiveEvent by remember { mutableStateOf(activeEvent) }
    if (activeEvent != null) {
        lastActiveEvent = activeEvent
    }

    var lastEvents by remember { mutableStateOf(events) }
    if (events.isNotEmpty()) {
        lastEvents = events
    }

    val currentActiveEvent = activeEvent ?: lastActiveEvent
    val currentEvents = events.ifEmpty { lastEvents }

    KNetSideDrawer(
        visible = isVisible && activeEvent != null,
        size = KNetSideDrawerSize.EXPANDED,
        modifier = modifier,
    ) {
        val eventToRender = currentActiveEvent ?: return@KNetSideDrawer
        val preResolved = resolvedPayloads[eventToRender.id]
        val candidate = eventToRender.candidate
        val request = candidate.request
        val response = candidate.response

        var editedReqHeaders by remember(eventToRender.id) {
            mutableStateOf(request.head.headers.mapIndexed { index, header ->
                KeyValueEntry("intercept-header-$index", header.name.value, header.value)
            })
        }
        var reqBodyState by remember(eventToRender.id) {
            val spec = preResolved?.requestPayloadSpec
                ?: PayloadInspectionSpec.fromBytes(
                    candidate.requestBody?.copyBytes(),
                    request.head.headers.map { it.name.value to it.value },
                )
            mutableStateOf(RequestBodyState.from(spec))
        }
        var activeReqSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        var editedStatusCode by remember(eventToRender.id) {
            mutableStateOf(response?.head?.status?.code ?: 200)
        }
        var editedStatusText by remember(eventToRender.id) {
            mutableStateOf(response?.head?.reasonPhrase ?: "OK")
        }
        var editedRespHeaders by remember(eventToRender.id) {
            mutableStateOf(response?.head?.headers?.map { it.name.value to it.value }.orEmpty())
        }
        var respBodyState by remember(eventToRender.id) {
            val spec = preResolved?.responsePayloadSpec
                ?: PayloadInspectionSpec.fromBytes(
                    candidate.responseBody?.copyBytes(),
                    response?.head?.headers?.map { it.name.value to it.value }.orEmpty(),
                )
            mutableStateOf(ResponseBodyState.from(spec))
        }
        var activeRespSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        Row(modifier = Modifier.fillMaxSize()) {
                // Left Queue Sidebar (always visible in Master-Detail layout)
                InterceptQueueSidebar(
                    events = currentEvents,
                    selectedEventId = eventToRender.id,
                    onSelectEvent = onSelectEvent,
                    onDropItem = onDropItem,
                    onDropAll = onDropAll
                )

                // Right Editor Pane
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Header Bar (2-Tier: Status Badges Row + Endpoint Card Row)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColors.surfaceVariant)
                            .border(width = 1.dp, color = themeColors.border)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tier 1: Status Badges & Close Button
                        val badgeScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(badgeScrollState)
                            ) {
                                // Status Badge
                                Box(
                                    modifier = Modifier
                                        .background(themeColors.semantic.warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(1.dp, themeColors.semantic.warning, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "PAUSED IN-FLIGHT",
                                        style = typography.codeSmall.copy(
                                            color = themeColors.semantic.warning,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Intercept Phase Badge
                                val isRequestPhase = candidate.phase == BreakpointPhase.REQUEST
                                val phaseColor = if (isRequestPhase) themeColors.semantic.info else themeColors.semantic.success
                                val phaseLabel = if (isRequestPhase) "REQUEST INTERCEPT" else "RESPONSE INTERCEPT"

                                Box(
                                    modifier = Modifier
                                        .background(phaseColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(1.dp, phaseColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = phaseLabel,
                                        style = typography.codeSmall.copy(
                                            color = phaseColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Protocol / Payload Badge
                                val contentTypeHeader = response?.head?.headers
                                    ?.firstOrNull { it.name.value.equals("Content-Type", ignoreCase = true) }?.value
                                    ?: request.head.headers
                                        .firstOrNull { it.name.value.equals("Content-Type", ignoreCase = true) }?.value

                                val (protocolHeaderLabel, protocolHeaderColor) = when {
                                    contentTypeHeader?.contains("json", ignoreCase = true) == true -> "JSON" to themeColors.semantic.info
                                    contentTypeHeader?.contains("xml", ignoreCase = true) == true -> "XML" to themeColors.semantic.warning
                                    contentTypeHeader?.contains("form", ignoreCase = true) == true -> "FORM-DATA" to Color(0xFFFAB387)
                                    else -> null to themeColors.textSecondary
                                }

                                if (protocolHeaderLabel != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(protocolHeaderColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                            .border(1.dp, protocolHeaderColor.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = protocolHeaderLabel,
                                            style = typography.codeSmall.copy(
                                                color = protocolHeaderColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Drawer",
                                    tint = themeColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Tier 2: Standardized EndpointCard with Full URL, HTTP Method, and Copy Action
                        val methodColor = when (request.head.method.token.uppercase()) {
                            "GET" -> themeColors.semantic.success
                            "POST" -> themeColors.semantic.info
                            "PUT" -> themeColors.semantic.warning
                            "DELETE" -> themeColors.semantic.error
                            else -> themeColors.semantic.info
                        }

                        EndpointCard(
                            method = request.head.method.token,
                            endpoint = request.absoluteUrl(),
                            methodColor = methodColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Control Toolbar ([FORWARD], [DROP], [DISABLE RULE])
                    val toolbarScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColors.background)
                            .border(width = 1.dp, color = themeColors.border)
                            .horizontalScroll(toolbarScrollState)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic Forward Button
                        val isRequestPhase = candidate.phase == BreakpointPhase.REQUEST
                        val forwardLabel = if (isRequestPhase) "FORWARD REQUEST" else "FORWARD RESPONSE"

                        KNetButton(
                            onClick = {
                                if (isRequestPhase) {
                                    val headersToForward = editedReqHeaders.filter {
                                        it.enabled && !it.key.equals("Content-Encoding", ignoreCase = true)
                                    }
                                    val modifiedReq = BreakpointRequestEdit(
                                        request = request.copy(
                                            head = request.head.copy(
                                                headers = headersToForward.map { entry ->
                                                    HeaderField(HeaderName(entry.key), entry.value)
                                                }
                                            )
                                        ),
                                        body = reqBodyState.payloadText.encodeToByteArray()
                                            .takeIf(ByteArray::isNotEmpty)
                                            ?.let(::BreakpointBody),
                                    )
                                    onForwardRequest(modifiedReq)
                                } else {
                                    val headersToForward = editedRespHeaders.filterNot {
                                        it.first.equals("Content-Encoding", ignoreCase = true)
                                    }
                                    val originalResponse = requireNotNull(response)
                                    val modifiedResp = BreakpointResponseEdit(
                                        response = originalResponse.copy(
                                            head = originalResponse.head.copy(
                                                status = HttpStatus(editedStatusCode),
                                                reasonPhrase = editedStatusText.takeIf(String::isNotBlank),
                                                headers = headersToForward.map { (name, value) ->
                                                    HeaderField(HeaderName(name), value)
                                                },
                                            )
                                        ),
                                        body = respBodyState.payloadText.encodeToByteArray()
                                            .takeIf(ByteArray::isNotEmpty)
                                            ?.let(::BreakpointBody),
                                    )
                                    onForwardResponse(modifiedResp)
                                }
                            },
                            variant = ButtonVariant.Primary
                        ) {
                            Text(
                                text = forwardLabel,
                                style = typography.caption.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Drop Button
                        KNetButton(
                            onClick = onDrop,
                            variant = ButtonVariant.Secondary
                        ) {
                            Text(
                                text = "DROP",
                                style = typography.caption.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Disable Rule Button
                        KNetButton(
                            onClick = onDisableRule,
                            variant = ButtonVariant.Secondary
                        ) {
                            Text(
                                text = "DISABLE RULE",
                                style = typography.caption.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Editor Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (candidate.phase == BreakpointPhase.REQUEST) {
                            RequestEditorPanel(
                                bodyState = reqBodyState,
                                headers = editedReqHeaders,
                                activeSubTab = activeReqSubTab,
                                actions = RequestEditorPanelActions(
                                    onBodyStateChanged = { reqBodyState = it },
                                    onHeadersChanged = { editedReqHeaders = it },
                                    onSubTabSelected = { activeReqSubTab = it }
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ResponseEditorPanel(
                                statusCode = editedStatusCode,
                                statusText = editedStatusText,
                                bodyState = respBodyState,
                                headers = editedRespHeaders,
                                activeSubTab = activeRespSubTab,
                                actions = ResponseEditorPanelActions(
                                    onStatusCodeChanged = { editedStatusCode = it },
                                    onStatusTextChanged = { editedStatusText = it },
                                    onBodyStateChanged = { respBodyState = it },
                                    onHeadersChanged = { pairs ->
                                        editedRespHeaders = pairs
                                    },
                                    onSubTabSelected = { activeRespSubTab = it }
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
        }
    }
}
