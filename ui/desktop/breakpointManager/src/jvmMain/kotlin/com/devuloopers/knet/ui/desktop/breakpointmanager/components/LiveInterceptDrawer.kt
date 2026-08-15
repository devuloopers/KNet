package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
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
    events: List<InterceptedTransaction>,
    activeEvent: InterceptedTransaction?,
    isVisible: Boolean,
    resolvedPayloads: Map<String, ResolvedInterceptPayload> = emptyMap(),
    onSelectEvent: (eventId: String) -> Unit,
    onDropItem: (eventId: String) -> Unit,
    onDropAll: () -> Unit,
    onForwardRequest: (modifiedRequest: HttpRequest) -> Unit,
    onForwardResponse: (modifiedResponse: HttpResponse) -> Unit,
    onDrop: () -> Unit,
    onDisableRule: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val drawerWidth = 880.dp

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

    AnimatedVisibility(
        visible = isVisible && activeEvent != null,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        val eventToRender = currentActiveEvent ?: return@AnimatedVisibility
        val preResolved = resolvedPayloads[eventToRender.id]

        var editedReqHeaders by remember(eventToRender.id) {
            mutableStateOf(eventToRender.request.headers)
        }
        var reqBodyState by remember(eventToRender.id) {
            val spec = preResolved?.requestPayloadSpec
                ?: PayloadInspectionSpec.fromBytes(eventToRender.request.body, eventToRender.request.headers)
            mutableStateOf(RequestBodyState.fromResolved(spec))
        }
        var activeReqSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        var editedStatusCode by remember(eventToRender.id) {
            mutableStateOf(eventToRender.response?.statusCode ?: 200)
        }
        var editedStatusText by remember(eventToRender.id) {
            mutableStateOf(eventToRender.response?.statusText ?: "OK")
        }
        var editedRespHeaders by remember(eventToRender.id) {
            mutableStateOf(eventToRender.response?.headers ?: emptyList())
        }
        var respBodyState by remember(eventToRender.id) {
            val spec = preResolved?.responsePayloadSpec
                ?: PayloadInspectionSpec.fromBytes(eventToRender.response?.body, eventToRender.response?.headers ?: emptyList())
            mutableStateOf(ResponseBodyState.fromResolved(spec))
        }
        var activeRespSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .background(themeColors.surface)
                .border(width = 1.dp, color = themeColors.border)
        ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
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
                                        )
                                    )
                                }

                                // Intercept Phase Badge
                                val isRequestPhase = eventToRender.phase == BreakpointPhase.REQUEST
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
                                        )
                                    )
                                }

                                // Protocol / Payload Badge
                                val contentTypeHeader = eventToRender.response?.headers?.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
                                    ?: eventToRender.request.headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second

                                val (protocolHeaderLabel, protocolHeaderColor) = when (val meta = eventToRender.metadata) {
                                    is InterceptionMetadata.GraphQL -> {
                                        val op = if (!meta.operationName.isNullOrBlank()) "${meta.operationName} (${meta.operationType})" else meta.operationType
                                        "GRAPHQL: $op" to Color(0xFFCBA6F7)
                                    }
                                    is InterceptionMetadata.Grpc -> "gRPC: ${meta.serviceName}/${meta.methodName}" to Color(0xFF89DCEB)
                                    is InterceptionMetadata.Protobuf -> "PROTOBUF" to Color(0xFF89DCEB)
                                    else -> when {
                                        contentTypeHeader?.contains("json", ignoreCase = true) == true -> "JSON" to themeColors.semantic.info
                                        contentTypeHeader?.contains("xml", ignoreCase = true) == true -> "XML" to themeColors.semantic.warning
                                        contentTypeHeader?.contains("form", ignoreCase = true) == true -> "FORM-DATA" to Color(0xFFFAB387)
                                        else -> null to themeColors.textSecondary
                                    }
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
                                            )
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
                        val methodColor = when (eventToRender.method.uppercase()) {
                            "GET" -> themeColors.semantic.success
                            "POST" -> themeColors.semantic.info
                            "PUT" -> themeColors.semantic.warning
                            "DELETE" -> themeColors.semantic.error
                            else -> themeColors.semantic.info
                        }

                        EndpointCard(
                            method = eventToRender.method,
                            endpoint = eventToRender.url,
                            methodColor = methodColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Control Toolbar ([FORWARD], [DROP], [DISABLE RULE])
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColors.background)
                            .border(width = 1.dp, color = themeColors.border)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic Forward Button
                        val isRequestPhase = eventToRender.phase == BreakpointPhase.REQUEST
                        val forwardLabel = if (isRequestPhase) "FORWARD REQUEST" else "FORWARD RESPONSE"

                        KNetButton(
                            onClick = {
                                if (isRequestPhase) {
                                    val headersToForward = editedReqHeaders.filterNot {
                                        it.first.equals("Content-Encoding", ignoreCase = true)
                                    }
                                    val modifiedReq = HttpRequest(
                                        id = eventToRender.request.id,
                                        method = eventToRender.request.method,
                                        url = eventToRender.request.url,
                                        protocol = eventToRender.request.protocol,
                                        headers = headersToForward,
                                        body = reqBodyState.payloadText.encodeToByteArray(),
                                        timestamp = eventToRender.request.timestamp,
                                        isIntercepted = true,
                                        matchedRuleId = eventToRender.request.matchedRuleId
                                    )
                                    onForwardRequest(modifiedReq)
                                } else {
                                    val headersToForward = editedRespHeaders.filterNot {
                                        it.first.equals("Content-Encoding", ignoreCase = true)
                                    }
                                    val modifiedResp = HttpResponse(
                                        statusCode = editedStatusCode,
                                        statusText = editedStatusText,
                                        headers = headersToForward,
                                        body = respBodyState.payloadText.encodeToByteArray(),
                                        timestamp = eventToRender.response?.timestamp ?: System.currentTimeMillis()
                                    )
                                    onForwardResponse(modifiedResp)
                                }
                            },
                            variant = ButtonVariant.Primary
                        ) {
                            Text(text = forwardLabel, style = typography.caption.copy(fontWeight = FontWeight.Bold))
                        }

                        // Drop Button
                        KNetButton(
                            onClick = onDrop,
                            variant = ButtonVariant.Secondary
                        ) {
                            Text(text = "DROP", style = typography.caption.copy(fontWeight = FontWeight.Bold))
                        }

                        // Disable Rule Button
                        KNetButton(
                            onClick = onDisableRule,
                            variant = ButtonVariant.Secondary
                        ) {
                            Text(text = "DISABLE RULE", style = typography.caption.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    // Editor Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (eventToRender.phase == BreakpointPhase.REQUEST || eventToRender.phase == BreakpointPhase.BOTH) {
                            RequestEditorPanel(
                                bodyState = reqBodyState,
                                headers = editedReqHeaders,
                                activeSubTab = activeReqSubTab,
                                actions = RequestEditorPanelActions(
                                    onBodyStateChanged = { reqBodyState = it },
                                    onHeadersChanged = { pairs ->
                                        editedReqHeaders = pairs
                                    },
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
}

