package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLParameterEditorState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase

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
import com.devuloopers.knet.traffic.model.absoluteUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.BreakpointBodyEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.traffic.model.displayUrl
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
 * Immutable presentation input for the desktop live-interception drawer.
 *
 * @property events Currently pending interceptions in transport order.
 * @property activeEvent Event selected for inspection, or `null` when the drawer is closed.
 * @property isVisible Whether the shared drawer shell should be visible.
 * @property resolvedPayloads Bounded prepared payload lookup for the selected event.
 * @property requestDescriptors Protocol-aware queue presentation keyed by pending event ID.
 */
data class LiveInterceptDrawerState(
    val events: List<PendingBreakpoint>,
    val activeEvent: PendingBreakpoint?,
    val isVisible: Boolean,
    val resolvedPayloads: Map<String, ResolvedInterceptPayload>,
    val requestDescriptors: Map<String, RequestDescriptor>,
)

/**
 * User actions emitted by the desktop live-interception drawer.
 *
 * @property selectEvent Selects one pending interception.
 * @property dropItem Drops one pending interception by ID.
 * @property dropAll Drops every pending interception.
 * @property forwardRequest Forwards a validated request-phase edit.
 * @property forwardResponse Forwards a validated response-phase edit.
 * @property forwardUnchanged Forwards retained transport bytes without rebuilding the body.
 * @property drop Drops the selected interception.
 * @property disableRule Disables the selected event's rule and drops that event.
 * @property dismiss Applies the feature's explicit drawer-dismiss policy.
 */
data class LiveInterceptDrawerActions(
    val selectEvent: (eventId: String) -> Unit,
    val dropItem: (eventId: String) -> Unit,
    val dropAll: () -> Unit,
    val forwardRequest: (modifiedRequest: BreakpointRequestEdit) -> Unit,
    val forwardResponse: (modifiedResponse: BreakpointResponseEdit) -> Unit,
    val forwardUnchanged: () -> Unit,
    val drop: () -> Unit,
    val disableRule: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Slide-out desktop drawer displaying active in-flight suspended HTTP transactions.
 * Supports Master-Detail layout with an animated left Queue Sidebar when multiple transactions are waiting,
 * and reuses `:ui:desktop:httpPanel` editors with [FORWARD], [DROP], and [DISABLE RULE] controls.
 *
 * @param state Immutable queue, selection, visibility, and resolved-payload state.
 * @param actions Cohesive user interactions delegated to the owning ViewModel.
 * @param modifier Optional layout modifier.
 */
@Composable
fun LiveInterceptDrawer(
    state: LiveInterceptDrawerState,
    actions: LiveInterceptDrawerActions,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    // Retain the last active event and non-empty queue snapshot so that during the slide-out
    // exit animation, Compose continues to render the full drawer UI smoothly without an abrupt 0x0 collapse.
    var lastActiveEvent by remember { mutableStateOf(state.activeEvent) }
    LaunchedEffect(state.activeEvent) {
        state.activeEvent?.let { lastActiveEvent = it }
    }

    var lastEvents by remember { mutableStateOf(state.events) }
    LaunchedEffect(state.events) {
        if (state.events.isNotEmpty()) lastEvents = state.events
    }

    val currentActiveEvent = state.activeEvent ?: lastActiveEvent
    val currentEvents = state.events.ifEmpty { lastEvents }

    var lastResolvedPayload by remember { mutableStateOf<ResolvedInterceptPayload?>(null) }
    LaunchedEffect(state.activeEvent, state.resolvedPayloads) {
        state.activeEvent?.let { event ->
            state.resolvedPayloads[event.id]?.let { lastResolvedPayload = it }
        }
    }

    KNetSideDrawer(
        visible = state.isVisible && state.activeEvent != null,
        size = KNetSideDrawerSize.EXPANDED,
        modifier = modifier,
    ) {
        val eventToRender = currentActiveEvent ?: return@KNetSideDrawer
        val preResolved = state.resolvedPayloads[eventToRender.id]
            ?: lastResolvedPayload?.takeIf { it.transactionId == eventToRender.id }
        val candidate = eventToRender.candidate
        val request = candidate.request
        val response = candidate.response

        var editedReqHeaders by remember(eventToRender.id) {
            mutableStateOf(request.head.headers.mapIndexed { index, header ->
                KeyValueEntry("intercept-header-$index", header.name.value, header.value)
            })
        }
        var reqBodyState by remember(eventToRender.id, preResolved) {
            mutableStateOf(RequestBodyState.from(preResolved?.requestPayloadSpec ?: PayloadInspectionSpec.EMPTY))
        }
        var editedTarget by remember(eventToRender.id) { mutableStateOf(request.head.target) }
        var graphQLParameters by remember(eventToRender.id, preResolved) { mutableStateOf(preResolved?.requestParameters) }
        var preserveParameterBuffers by remember(eventToRender.id) { mutableStateOf(false) }
        var parameterEditError by remember(eventToRender.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(eventToRender.id, editedTarget, preserveParameterBuffers) {
            if (request.head.method.token.equals("GET", true)) {
                val prepared = withContext(Dispatchers.Default) {
                    GraphQLParameterEditorState.prepare("GET",
                        request.copy(head = request.head.copy(target = editedTarget)).absoluteUrl(), prettify = !preserveParameterBuffers)
                }
                val previous = graphQLParameters
                graphQLParameters = if (preserveParameterBuffers && previous != null) previous.copy(diagnostic = prepared?.diagnostic
                    ?: GraphQLHttpDiagnostic.INVALID_DOCUMENT.takeIf { prepared == null })
                else prepared?.let { it.copy(graphQl = it.graphQl.copy(activeSubTab =
                    previous?.graphQl?.activeSubTab ?: GraphQlSubTab.QUERY)) }
            }
        }
        val bodySync = remember {
            SyncBodyStateUseCase(
                PayloadStrategyRegistry(listOf(GraphQlPayloadMapper())))
        }
        var requestBodyEdited by remember(eventToRender.id) { mutableStateOf(false) }
        var activeReqSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        var editedStatusCode by remember(eventToRender.id) {
            mutableStateOf(response?.head?.status?.code ?: 200)
        }
        var editedStatusText by remember(eventToRender.id) {
            mutableStateOf(response?.head?.reasonPhrase.orEmpty())
        }
        var editedRespHeaders by remember(eventToRender.id) {
            mutableStateOf(response?.head?.headers?.map { it.name.value to it.value }.orEmpty())
        }
        var respBodyState by remember(eventToRender.id, preResolved) {
            mutableStateOf(ResponseBodyState.from(preResolved?.responsePayloadSpec ?: PayloadInspectionSpec.EMPTY))
        }
        var responseBodyEdited by remember(eventToRender.id) { mutableStateOf(false) }
        var activeRespSubTab by remember(eventToRender.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        Row(modifier = Modifier.fillMaxSize()) {
                // Left Queue Sidebar (always visible in Master-Detail layout)
                InterceptQueueSidebar(
                    events = currentEvents,
                    requestDescriptors = state.requestDescriptors,
                    selectedEventId = eventToRender.id,
                    onSelectEvent = actions.selectEvent,
                    onDropItem = actions.dropItem,
                    onDropAll = actions.dropAll
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

                                val descriptor = state.requestDescriptors[eventToRender.id]
                                InterceptMetadataBadge(
                                    label = "TYPE ${descriptor?.badgeLabel ?: request.head.method.token}",
                                    color = themeColors.semantic.info,
                                )
                                InterceptMetadataBadge(
                                    label = "CLIENT ${request.head.protocol.token}",
                                    color = themeColors.textSecondary,
                                )
                                response?.head?.protocol?.let { upstreamProtocol ->
                                    InterceptMetadataBadge(
                                        label = "UPSTREAM ${upstreamProtocol.token}",
                                        color = themeColors.textSecondary,
                                    )
                                }
                                InterceptMetadataBadge(
                                    label = "SOURCE ${candidate.origin.displayName}",
                                    color = themeColors.semantic.success,
                                )

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
                                    InterceptMetadataBadge(
                                        label = "PAYLOAD $protocolHeaderLabel",
                                        color = protocolHeaderColor,
                                    )
                                }
                            }

                            IconButton(
                                onClick = actions.dismiss,
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
                            endpoint = request.displayUrl(),
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
                                    val headersToForward = editedReqHeaders.filter { entry ->
                                        entry.enabled && (!requestBodyEdited ||
                                            !entry.key.equals("Content-Encoding", ignoreCase = true))
                                    }
                                    val editedHeaderFields = headersToForward.map { entry ->
                                        HeaderField(HeaderName(entry.key), entry.value)
                                    }
                                    val metadataChanged = editedHeaderFields != request.head.headers || editedTarget != request.head.target
                                    if (!metadataChanged && !requestBodyEdited) {
                                        actions.forwardUnchanged()
                                        return@KNetButton
                                    }
                                    val modifiedReq = BreakpointRequestEdit(
                                        request = request.copy(
                                            head = request.head.copy(
                                                headers = editedHeaderFields,
                                                target = editedTarget,
                                            )
                                        ),
                                        body = if (requestBodyEdited) {
                                            BreakpointBodyEdit.Replace(
                                                BreakpointBody(reqBodyState.payloadText.encodeToByteArray()),
                                            )
                                        } else {
                                            BreakpointBodyEdit.Unchanged
                                        },
                                    )
                                    actions.forwardRequest(modifiedReq)
                                } else {
                                    val headersToForward = editedRespHeaders.filterNot { header ->
                                        responseBodyEdited &&
                                            header.first.equals("Content-Encoding", ignoreCase = true)
                                    }
                                    val originalResponse = requireNotNull(response)
                                    val editedHeaderFields = headersToForward.map { (name, value) ->
                                        HeaderField(HeaderName(name), value)
                                    }
                                    val metadataChanged = editedStatusCode != originalResponse.head.status.code ||
                                        editedStatusText.takeIf(String::isNotBlank) != originalResponse.head.reasonPhrase ||
                                        editedHeaderFields != originalResponse.head.headers
                                    if (!metadataChanged && !responseBodyEdited) {
                                        actions.forwardUnchanged()
                                        return@KNetButton
                                    }
                                    val modifiedResp = BreakpointResponseEdit(
                                        response = originalResponse.copy(
                                            head = originalResponse.head.copy(
                                                status = HttpStatus(editedStatusCode),
                                                reasonPhrase = editedStatusText.takeIf(String::isNotBlank),
                                                headers = editedHeaderFields,
                                            )
                                        ),
                                        body = if (responseBodyEdited) {
                                            BreakpointBodyEdit.Replace(
                                                BreakpointBody(respBodyState.payloadText.encodeToByteArray()),
                                            )
                                        } else {
                                            BreakpointBodyEdit.Unchanged
                                        },
                                    )
                                    actions.forwardResponse(modifiedResp)
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
                            onClick = actions.drop,
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
                            onClick = actions.disableRule,
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
                        if (preResolved == null) {
                            Text(
                                text = "Preparing intercepted payload…",
                                style = typography.bodyMedium,
                                color = themeColors.textSecondary,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else if (candidate.phase == BreakpointPhase.REQUEST) {
                            Column(Modifier.fillMaxSize()) {
                            parameterEditError?.let { com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph(it,
                                Modifier.fillMaxWidth(), color = themeColors.semantic.warning) }
                            RequestEditorPanel(
                                bodyState = reqBodyState,
                                graphQLParameters = graphQLParameters,
                                queryParams = com.devuloopers.knet.domain.util.UrlQueryStringParser.parseQueryParams(
                                    when (val target = editedTarget) {
                                        is RequestTarget.Absolute -> target.pathAndQuery
                                        is RequestTarget.Origin -> target.pathAndQuery
                                        else -> ""
                                    }).mapIndexed { index, pair -> KeyValueEntry("parameter-$index", pair.first, pair.second) },
                                headers = editedReqHeaders,
                                activeSubTab = activeReqSubTab,
                                actions = RequestEditorPanelActions(
                                    onBodyStateChanged = {
                                        requestBodyEdited = true
                                        reqBodyState = it
                                    },
                                    onGraphQlStateChanged = { updated ->
                                        val parameters = graphQLParameters
                                        if (activeReqSubTab == InspectorSubTab.PARAMS && parameters != null) {
                                            GraphQLHttpRequestFormatter.updateTarget(editedTarget,
                                                GraphQLHttpRequestFormatter.changedFields(parameters.graphQl.payload, updated.payload))
                                                .onSuccess { target ->
                                                    preserveParameterBuffers = true
                                                    editedTarget = target
                                                    graphQLParameters = parameters.copy(graphQl = updated)
                                                    parameterEditError = null
                                                }.onFailure { parameterEditError = "Edit the raw URL parameters to resolve ambiguous or invalid encoding." }
                                        } else {
                                            requestBodyEdited = true
                                            reqBodyState = bodySync.updateGraphQlState(reqBodyState, updated)
                                        }
                                    },
                                    onQueryParamsChanged = { entries ->
                                        val oldTarget = editedTarget
                                        val text = when (oldTarget) {
                                            is RequestTarget.Absolute -> oldTarget.pathAndQuery
                                            is RequestTarget.Origin -> oldTarget.pathAndQuery
                                            else -> ""
                                        }
                                        val target = com.devuloopers.knet.domain.util.UrlQueryStringParser.rebuildUrlWithQueryParams(text,
                                            entries.filter { it.enabled }.map { it.key to it.value })
                                        editedTarget = when (oldTarget) {
                                            is RequestTarget.Absolute -> oldTarget.copy(pathAndQuery = target)
                                            is RequestTarget.Origin -> oldTarget.copy(pathAndQuery = target)
                                            else -> oldTarget
                                        }
                                        preserveParameterBuffers = false
                                        graphQLParameters = null
                                    },
                                    onHeadersChanged = { editedReqHeaders = it },
                                    onSubTabSelected = { activeReqSubTab = it }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            }
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
                                    onBodyStateChanged = {
                                        responseBodyEdited = true
                                        respBodyState = it
                                    },
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

/** Compact typed metadata chip used by the horizontally scrollable interception header. */
@Composable
private fun InterceptMetadataBadge(label: String, color: Color) {
    val typography = KNetTheme.typography
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = typography.codeSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
