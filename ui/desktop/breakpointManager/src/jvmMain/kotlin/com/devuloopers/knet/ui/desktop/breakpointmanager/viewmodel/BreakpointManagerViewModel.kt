package com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel

import com.devuloopers.knet.ui.core.components.policy.RuleReplacementNoticeState
import com.devuloopers.knet.ui.core.components.policy.RULE_REPLACEMENT_NOTICE_MILLIS
import kotlinx.coroutines.delay
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.domain.policy.*
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingProtocolMessageBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveProtocolMessageBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.BreakpointProtocolRuleUseCase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointPortCriteria
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointManagerState
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.ResolvedInterceptPayload
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.application.usecase.graphql.ObserveGraphQLPersistedQueriesUseCase
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
    private val getRulesUseCase: GetRulesUseCase,
    private val observeGlobalInterceptionUseCase: ObserveGlobalInterceptionUseCase,
    observePendingBreakpointsUseCase: ObservePendingBreakpointsUseCase,
    observePendingProtocolMessageBreakpointsUseCase: ObservePendingProtocolMessageBreakpointsUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val toggleGlobalInterceptionUseCase: ToggleGlobalInterceptionUseCase,
    private val resolveBreakpointUseCase: ResolveBreakpointUseCase,
    private val clearPendingBreakpointsUseCase: ClearPendingBreakpointsUseCase,
    private val resolveProtocolMessageBreakpointUseCase: ResolveProtocolMessageBreakpointUseCase,
    private val breakpointProtocolRuleUseCase: BreakpointProtocolRuleUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    observeGraphQLPersistedQueriesUseCase: ObserveGraphQLPersistedQueriesUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BreakpointManagerState(
            protocolDefinitions = breakpointProtocolRuleUseCase.definitions(),
        ),
    )
    val uiState: StateFlow<BreakpointManagerState> = _uiState.asStateFlow()
    private var requestDescriptorJob: kotlinx.coroutines.Job? = null

    private var replacementNoticeJob: kotlinx.coroutines.Job? = null

    /** Uses the committed replacement result and original target, never the current tab or a pre-save guess. */
    private fun confirmReplacement(result: RuleSaveResult, context: RuleEditContext, deviceName: String) {
        if (!result.replacedExistingRule) return
        replacementNoticeJob?.cancel()
        _uiState.update { it.copy(replacementNotice = RuleReplacementNoticeState(context.editorId, deviceName)) }
        replacementNoticeJob = viewModelScope.launch {
            delay(RULE_REPLACEMENT_NOTICE_MILLIS)
            _uiState.update { current -> if (current.replacementNotice?.commandId == context.editorId)
                current.copy(replacementNotice = null) else current }
        }
    }

    private var catalogJob: kotlinx.coroutines.Job? = null
    private var allPending = emptyList<PendingBreakpoint>()
    private var allPendingMessages = emptyList<com.devuloopers.knet.application.contract.breakpoint.PendingProtocolMessageBreakpoint>()

    /** Captures deletion identity/revision before displaying the affected workspaces. */
    fun requestRuleDeletion(ruleId: String) {
        val current = uiState.value
        val definition = current.definitions.firstOrNull { it.rule.id == ruleId } ?: return
        _uiState.update { it.copy(pendingDeletion = PolicyDeletionRequest(ruleId,
            definition.editContext(current.workspace), definition.usageNames(current.workspaceNames))) }
    }
    /** Cancels deletion without changing definitions or activations. */
    fun dismissRuleDeletion() { _uiState.update { it.copy(pendingDeletion = null) } }
    /** Deletes exactly the acknowledged revision and original target. */
    fun confirmRuleDeletion() {
        val request = uiState.value.pendingDeletion ?: return
        dismissRuleDeletion()
        mutate { deleteRuleUseCase.execute(request.id, request.context, true) }
    }

    /** Selects presentation only; saved rules and pending native connections keep their original owner. */
    fun selectWorkspace(workspace: PolicyWorkspace) {
        if (workspace == _uiState.value.workspace) return
        _uiState.update { it.copy(workspace = workspace,
            workspaceName = it.workspaceNames[workspace] ?: if (workspace == PolicyWorkspace.Other) "Other traffic" else "Selected device", rules = emptyList(), definitions = emptyList()) }
        observeWorkspace(workspace)
        publishPending()
    }
    private fun observeWorkspace(workspace: PolicyWorkspace) {
        catalogJob?.cancel()
        catalogJob = combine(getRulesUseCase.catalog(workspace), observeGlobalInterceptionUseCase(workspace)) { catalog, enabled -> catalog to enabled }
            .onEach { (catalog, enabled) ->
                _uiState.update { current -> if (current.workspace == workspace) current.copy(
                    workspaceName = catalog.workspaceName, workspaceNames = catalog.workspaceNames, definitions = catalog.definitions,
                    rules = catalog.definitions.map { it.rule.copy(enabled = it.activeOn(workspace)) }, isGlobalInterceptionEnabled = enabled) else current }
                scheduleRequestDescriptors()
            }.launchIn(viewModelScope)
    }
    private fun publishPending() {
        _uiState.update { current ->
            val events = allPending.filter { it.candidate.workspace == current.workspace }
            val messages = allPendingMessages.filter { it.candidate.workspace == current.workspace }
            val active = events.firstOrNull { it.id == current.activeEvent?.id } ?: events.firstOrNull()
            current.copy(activeEvents = events, activeEvent = active,
                activeMessageEvents = messages, activeMessageEvent = messages.firstOrNull { it.id == current.activeMessageEvent?.id } ?: messages.firstOrNull(),
                resolvedPayloads = current.resolvedPayloads.filterKeys { it == active?.id },
                requestDescriptors = current.requestDescriptors.filterKeys { id -> events.any { it.id == id } },
                pendingByWorkspace = (allPending.map { it.candidate.workspace } + allPendingMessages.map { it.candidate.workspace }).groupingBy { it }.eachCount())
        }
        scheduleRequestDescriptors()
    }

    init {
        observeWorkspace(PolicyWorkspace.Other)

        // Publish the pending queue before decoding. A breakpoint drawer must become actionable
        // immediately even when a large compressed or structured payload is expensive to inspect.
        observePendingBreakpointsUseCase.execute().onEach { events ->
            allPending = events
            publishPending()
        }.launchIn(viewModelScope)
        observePendingProtocolMessageBreakpointsUseCase.execute().onEach { events ->
            allPendingMessages = events
            publishPending()
        }.launchIn(viewModelScope)

        // Resolve only the selected payload. mapLatest cancels obsolete work when the user moves
        // through the queue, while a one-entry cache keeps presentation memory strictly bounded.
        viewModelScope.launch {
            combine(
                uiState.map { state: BreakpointManagerState -> state.activeEvent }.distinctUntilChangedBy { it?.id },
                observeGraphQLPersistedQueriesUseCase.execute(),
            ) { event, _ -> event }
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

    fun selectActiveMessageEvent(eventId: String) {
        _uiState.update { current ->
            current.copy(
                activeMessageEvent = current.activeMessageEvents.find { it.id == eventId }
                    ?: current.activeMessageEvent,
            )
        }
    }

    fun continueProtocolMessage(pendingId: String) {
        viewModelScope.launch {
            resolveProtocolMessageBreakpointUseCase.continueUnchanged(pendingId)
        }
    }

    fun replaceProtocolMessage(pendingId: String, body: ByteArray) {
        viewModelScope.launch {
            resolveProtocolMessageBreakpointUseCase.replace(pendingId, body)
        }
    }

    fun dropProtocolMessageStream(pendingId: String) {
        viewModelScope.launch {
            resolveProtocolMessageBreakpointUseCase.dropStream(pendingId)
        }
    }

    /**
     * Drops and terminates all active in-flight suspended connections in the queue immediately.
     */
    fun dropAllEvents() {
        val workspace = _uiState.value.workspace
        val messages = allPendingMessages.filter { it.candidate.workspace == workspace }.map { it.id }
        viewModelScope.launch {
            clearPendingBreakpointsUseCase.execute(workspace)
            messages.forEach { resolveProtocolMessageBreakpointUseCase.dropStream(it) }
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
        val workspace = _uiState.value.workspace
        mutate {
            toggleGlobalInterceptionUseCase(enabled, workspace)
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
        val workspace = _uiState.value.workspace
        if (_uiState.value.rules.none { it.id == ruleId }) return
        mutate { toggleRuleUseCase.execute(ruleId, enabled, workspace) }
    }

    fun openAddDrawer() {
        _uiState.update {
            it.copy(
                isAddEditDrawerVisible = true,
                editingRule = null,
                editingContext = RuleEditContext(it.workspace), editingWorkspaceName = it.workspaceName,
                editingProtocolValues = emptyList(),
            )
        }
    }

    /** Hands a Traffic draft into the same editor, preserving the record's authenticated target. */
    fun openPreparedDraft(rule: BreakpointRule, values: List<ProtocolCriteriaValue>, workspace: PolicyWorkspace) {
        val name = _uiState.value.workspaceNames[workspace] ?: if (workspace == PolicyWorkspace.Other) "Other traffic" else "Saved device"
        _uiState.update { it.copy(isAddEditDrawerVisible = true, editingRule = rule, editingProtocolValues = values,
            editingContext = RuleEditContext(workspace), editingWorkspaceName = name, editingUsageNames = emptyList()) }
    }

    /** Opens an imported rule for review without replacing an existing unsaved editor. */
    fun openImportedDraft(draft: com.devuloopers.knet.application.contract.importcapture.CaptureImportActionDraft.Intercept) {
        check(!_uiState.value.isAddEditDrawerVisible && !_uiState.value.isSaving) { "Finish or close the current Intercept rule first." }
        openPreparedDraft(draft.rule, draft.values, draft.workspace)
    }

    fun openEditDrawer(rule: BreakpointRule) {
        _uiState.update {
            it.copy(
                isAddEditDrawerVisible = true,
                editingRule = rule,
                editingContext = it.definitions.firstOrNull { entry -> entry.rule.id == rule.id }?.editContext(it.workspace) ?: RuleEditContext(it.workspace),
                editingWorkspaceName = it.workspaceName,
                editingUsageNames = it.definitions.firstOrNull { entry -> entry.rule.id == rule.id }?.usageNames(it.workspaceNames).orEmpty(),
                editingProtocolValues = breakpointProtocolRuleUseCase.editorValues(rule.protocolCriteria),
            )
        }
    }

    fun closeDrawer() {
        _uiState.update {
            it.copy(
                isAddEditDrawerVisible = false,
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
        portCriteria: BreakpointPortCriteria = BreakpointPortCriteria.Any,
        protocolId: BreakpointProtocolId = BreakpointProtocolId.HTTP,
        protocolValues: List<ProtocolCriteriaValue> = emptyList(),
    ) {
        if (_uiState.value.isSaving || !_uiState.value.isAddEditDrawerVisible) return
        val protocolCriteria = breakpointProtocolRuleUseCase.createCriteria(protocolId, protocolValues)
            ?: return
        val context = _uiState.value.editingContext
        val deviceName = _uiState.value.editingWorkspaceName
        val currentEditing = _uiState.value.editingRule
        val targetId = currentEditing?.id ?: @OptIn(kotlin.uuid.ExperimentalUuidApi::class) kotlin.uuid.Uuid.random().toString()

        val rule = BreakpointRule(
            id = targetId,
            name = urlPattern,
            urlPattern = urlPattern,
            portCriteria = portCriteria,
            method = method,
            phase = phase,
            enabled = if (currentEditing == null) true else enabled,
            priority = currentEditing?.priority ?: 0,
            protocolCriteria = protocolCriteria
        )

        _uiState.update { it.copy(isSaving = true) }
        mutate(onFinished = { _uiState.update { it.copy(isSaving = false) } }) {
            val result = saveRuleUseCase.execute(rule, context)
            confirmReplacement(result, context, deviceName)
            _uiState.update { state -> if (state.editingContext == context) state.copy(
                isAddEditDrawerVisible = false, editingRule = null, editingProtocolValues = emptyList()) else state }
        }
    }

    /** Selects availability for this editor; changing it never changes the captured activation target. */
    fun setDraftShared(shared: Boolean) {
        _uiState.update { current ->
            if (current.editingRule != null && current.editingContext.originalAvailability == RuleAvailability.Shared && !shared) current
            else current.copy(editingContext = current.editingContext.copy(availability = if (shared) RuleAvailability.Shared else RuleAvailability.Private(current.editingContext.workspace)))
        }
    }
    /** Starts an independent variation without modifying the shared original. */
    fun duplicateRule(rule: BreakpointRule) {
        openEditDrawer(rule)
        _uiState.update { it.copy(editingRule = rule.copy(id = kotlin.uuid.Uuid.random().toString()),
            editingContext = RuleEditContext(it.workspace), editingWorkspaceName = it.workspaceName) }
    }
    /** Duplicates the open definition while the drawer retains its locally edited matching fields. */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun duplicateDraft() {
        _uiState.update { it.copy(editingRule = it.editingRule?.copy(id = kotlin.uuid.Uuid.random().toString()),
            editingUsageNames = emptyList(), editingContext = RuleEditContext(it.editingContext.workspace)) }
    }
    /** Dismisses persistence/validation errors without losing the current draft. */
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    private fun mutate(onFinished: () -> Unit = {}, operation: suspend () -> Unit) {
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _uiState.update { it.copy(errorMessage = failure.message ?: "Unable to update interception rules.") } }
            finally { onFinished() }
        }
    }

    fun deleteRule(ruleId: String) {
        val current = _uiState.value
        val definition = current.definitions.firstOrNull { it.rule.id == ruleId } ?: return
        val context = definition.editContext(current.workspace)
        mutate { deleteRuleUseCase.execute(ruleId, context) }
    }

    private fun resolvePayload(event: com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint):
        ResolvedInterceptPayload {
        val requestBodySpec = PayloadInspectionSpec.fromRequest(
            method = event.candidate.request.head.method.token,
            url = event.candidate.request.absoluteUrl(),
            body = event.candidate.requestBody?.copyBytes(),
            headers = event.candidate.request.head.headers.map { it.name.value to it.value },
            bodyComplete = event.candidate.requestObservedBodyBytes == (event.candidate.requestBody?.size?.toLong() ?: 0L),
            learnCaptured = true,
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
            requestParameters = com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLParameterEditorState.prepare(
                event.candidate.request.head.method.token, event.candidate.request.absoluteUrl()),
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
