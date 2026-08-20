package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.BreakpointProtocolRuleUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointManagerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing presentation state and domain UseCase interactions for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * All rule mutations and in-flight interception operations are delegated strictly to domain UseCases from `:core:domain`.
 */
class BreakpointManagerViewModel(
    getRulesUseCase: GetRulesUseCase,
    observeGlobalInterceptionUseCase: ObserveGlobalInterceptionUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val toggleGlobalInterceptionUseCase: ToggleGlobalInterceptionUseCase,
    private val resolveBreakpointUseCase: ResolveBreakpointUseCase,
    private val clearPendingBreakpointsUseCase: ClearPendingBreakpointsUseCase,
    private val breakpointProtocolRuleUseCase: BreakpointProtocolRuleUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BreakpointManagerState(
            protocolDefinitions = breakpointProtocolRuleUseCase.definitions(),
        ),
    )
    val uiState: StateFlow<BreakpointManagerState> = _uiState.asStateFlow()
    private var requestDescriptorJob: kotlinx.coroutines.Job? = null

    init {
        getRulesUseCase()
            .onEach { rules ->
                _uiState.update { it.copy(rules = rules) }
                scheduleRequestDescriptors()
            }
            .launchIn(viewModelScope)

        observeGlobalInterceptionUseCase()
            .onEach { isGlobalEnabled ->
                _uiState.update { it.copy(isGlobalInterceptionEnabled = isGlobalEnabled) }
            }
            .launchIn(viewModelScope)

        // Publish the pending queue before decoding. A breakpoint drawer must become actionable
        // immediately even when a large compressed or structured payload is expensive to inspect.
        observePendingBreakpointsUseCase.execute()
            .onEach { events ->
                _uiState.update { current ->
                    val stillSelected = events.find { it.id == current.activeEvent?.id }
                    val activeEvent = stillSelected ?: events.firstOrNull()
                    current.copy(
                        activeEvents = events,
                        activeEvent = activeEvent,
                        resolvedPayloads = current.resolvedPayloads.filterKeys { it == activeEvent?.id },
                        requestDescriptors = current.requestDescriptors.filterKeys { eventId ->
                            events.any { it.id == eventId }
                        },
                    )
                }
                scheduleRequestDescriptors()
            }
            .launchIn(viewModelScope)

        // Resolve only the selected payload. mapLatest cancels obsolete work when the user moves
        // through the queue, while a one-entry cache keeps presentation memory strictly bounded.
        viewModelScope.launch {
            uiState
                .map { state: BreakpointManagerState -> state.activeEvent }
                .distinctUntilChangedBy { it?.id }
                .collectLatest { event ->
                    if (event == null) return@collectLatest
                    val payload = withContext(ioDispatcher) { resolvePayload(event) }
                    val eventId = event.id
                    _uiState.update { current ->
                        if (current.activeEvent?.id == eventId) {
                            current.copy(resolvedPayloads = mapOf(eventId to payload))
                        } else {
                            current
                        }
                    }
                }
        }
    }

    /**
     * Selects a specific active suspended transaction from the queue to display in the editor.
     *
     * @param eventId Unique identifier of the transaction to focus.
     */
    fun selectActiveEvent(eventId: String) {
        _uiState.update { current ->
            val selected = current.activeEvents.find { it.id == eventId }
            if (selected != null) {
                current.copy(activeEvent = selected, resolvedPayloads = emptyMap())
            } else {
                current
            }
        }
    }

    /**
     * Drops and terminates all active in-flight suspended connections in the queue immediately.
     */
    fun dropAllEvents() {
        viewModelScope.launch {
            clearPendingBreakpointsUseCase.execute()
        }
    }

    fun forwardRequest(transactionId: String, modifiedRequest: BreakpointRequestEdit) {
        viewModelScope.launch {
            resolveBreakpointUseCase.resumeRequest(transactionId, modifiedRequest)
        }
    }

    fun forwardResponse(transactionId: String, modifiedResponse: BreakpointResponseEdit) {
        viewModelScope.launch {
            resolveBreakpointUseCase.resumeResponse(transactionId, modifiedResponse)
        }
    }

    fun forwardUnchanged(transactionId: String) {
        viewModelScope.launch {
            resolveBreakpointUseCase.continueUnchanged(transactionId)
        }
    }

    fun dropEvent(transactionId: String) {
        viewModelScope.launch {
            resolveBreakpointUseCase.drop(transactionId)
        }
    }

    fun disableMatchingRule(ruleId: String) {
        if (ruleId.isNotBlank()) {
            setRuleEnabled(ruleId, enabled = false)
        }
        val currentEvent = _uiState.value.activeEvent
        if (currentEvent != null) {
            dropEvent(currentEvent.id)
        }
    }

    fun dismissCurrentEvent() {
        val currentEvent = _uiState.value.activeEvent
        if (currentEvent != null) {
            dropEvent(currentEvent.id)
        }
    }

    fun toggleGlobalInterception(enabled: Boolean) {
        viewModelScope.launch {
            toggleGlobalInterceptionUseCase(enabled)
            _uiState.update { it.copy(isGlobalInterceptionEnabled = enabled) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleRuleStatus(ruleId: String) {
        val targetRule = _uiState.value.rules.find { it.id == ruleId } ?: return
        setRuleEnabled(ruleId, enabled = !targetRule.enabled)
    }

    private fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        val targetRule = _uiState.value.rules.find { it.id == ruleId } ?: return
        val updated = targetRule.copy(enabled = enabled)
        viewModelScope.launch {
            toggleRuleUseCase(ruleId, enabled)
            _uiState.update { state ->
                state.copy(rules = state.rules.map { if (it.id == ruleId) updated else it })
            }
        }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = true,
                editingRule = null,
                editingProtocolValues = emptyList(),
            )
        }
    }

    fun openEditDialog(rule: BreakpointRule) {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = true,
                editingRule = rule,
                editingProtocolValues = breakpointProtocolRuleUseCase.editorValues(rule.protocolCriteria),
            )
        }
    }

    fun closeDialog() {
        _uiState.update {
            it.copy(
                isAddEditDialogVisible = false,
                editingRule = null,
                editingProtocolValues = emptyList(),
            )
        }
    }

    fun saveRule(
        urlPattern: String,
        method: HttpMethod?,
        phase: BreakpointPhase,
        enabled: Boolean,
        protocolId: BreakpointProtocolId = BreakpointProtocolId.HTTP,
        protocolValues: List<ProtocolCriteriaValue> = emptyList(),
    ) {
        val protocolCriteria = breakpointProtocolRuleUseCase.createCriteria(protocolId, protocolValues)
            ?: return
        val currentEditing = _uiState.value.editingRule
        val targetId = currentEditing?.id ?: @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()

        val rule = BreakpointRule(
            id = targetId,
            name = urlPattern,
            urlPattern = urlPattern,
            method = method,
            phase = phase,
            enabled = enabled,
            priority = currentEditing?.priority ?: 0,
            protocolCriteria = protocolCriteria
        )

        viewModelScope.launch {
            saveRuleUseCase.execute(rule)
            _uiState.update { state ->
                val existingIndex = state.rules.indexOfFirst { it.id == targetId }
                val newRules = if (existingIndex >= 0) {
                    state.rules.toMutableList().apply { set(existingIndex, rule) }
                } else {
                    state.rules + rule
                }
                state.copy(
                    rules = newRules,
                    isAddEditDialogVisible = false,
                    editingRule = null,
                    editingProtocolValues = emptyList(),
                )
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            deleteRuleUseCase(ruleId)
            _uiState.update { state ->
                state.copy(rules = state.rules.filterNot { it.id == ruleId })
            }
        }
    }

    private fun resolvePayload(event: com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint):
        ResolvedInterceptPayload {
        val requestBodySpec = PayloadInspectionSpec.fromBytes(
            body = event.candidate.requestBody?.copyBytes(),
            headers = event.candidate.request.head.headers.map { it.name.value to it.value },
        )
        val responseBodySpec = event.candidate.response?.let { response ->
            PayloadInspectionSpec.fromBytes(
                body = event.candidate.responseBody?.copyBytes(),
                headers = response.head.headers.map { it.name.value to it.value },
            )
        } ?: PayloadInspectionSpec.EMPTY
        return ResolvedInterceptPayload(
            transactionId = event.id,
            requestPayloadSpec = requestBodySpec,
            responsePayloadSpec = responseBodySpec,
        )
    }

    /** Resolves protocol-aware queue labels off-thread without delaying pending-event publication. */
    private fun scheduleRequestDescriptors() {
        requestDescriptorJob?.cancel()
        val state = _uiState.value
        val events = state.activeEvents
        if (events.isEmpty()) {
            _uiState.update { it.copy(requestDescriptors = emptyMap()) }
            return
        }
        val rulesById = state.rules.associateBy(BreakpointRule::id)
        requestDescriptorJob = viewModelScope.launch {
            val descriptors = withContext(ioDispatcher) {
                events.associate { event ->
                    val requestBody = event.candidate.requestBody
                    val retainedBody = requestBody?.let { body ->
                        RequestDescriptorBody(body.copyBytes(RequestDescriptorBody.MAXIMUM_BYTES))
                    }
                    val bodyComplete = when {
                        requestBody == null -> event.candidate.requestObservedBodyBytes == 0L
                        requestBody.size > RequestDescriptorBody.MAXIMUM_BYTES -> false
                        else -> event.candidate.requestObservedBodyBytes == requestBody.size.toLong()
                    }
                    val kindHint = rulesById[event.ruleId]
                        ?.protocolCriteria
                        ?.protocolId
                        ?.value
                        ?.toRequestKindIdOrNull()
                    event.id to describeRequestUseCase.execute(
                        request = event.candidate.request,
                        body = retainedBody,
                        bodyComplete = bodyComplete,
                        semanticKindHint = kindHint,
                    )
                }
            }
            _uiState.update { current ->
                if (current.activeEvents.map(PendingBreakpoint::id) == events.map(PendingBreakpoint::id)) {
                    current.copy(requestDescriptors = descriptors)
                } else {
                    current
                }
            }
        }
    }

}

private fun String.toRequestKindIdOrNull(): RequestKindId? =
    runCatching { RequestKindId(trim().lowercase()) }.getOrNull()
