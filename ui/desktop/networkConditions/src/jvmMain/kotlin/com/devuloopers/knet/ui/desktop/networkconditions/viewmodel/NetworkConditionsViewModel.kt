package com.devuloopers.knet.ui.desktop.networkconditions.viewmodel

import com.devuloopers.knet.ui.core.components.policy.RuleReplacementNoticeState
import com.devuloopers.knet.ui.core.components.policy.RULE_REPLACEMENT_NOTICE_MILLIS
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.application.usecase.networkconditions.ManageNetworkConditionsUseCase
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionProtocolRuleUseCase
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleResult
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleUseCase
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDraft
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab
import com.devuloopers.knet.ui.desktop.networkconditions.model.networkConditionRuleDetails
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputSampler
import com.devuloopers.knet.domain.policy.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NetworkConditionsViewModel(
    private val repository: ManageNetworkConditionsUseCase,
    private val runtimeTelemetry: NetworkConditionRuntimeTelemetry,
    private val prepareRule: PrepareNetworkConditionRuleUseCase,
    private val protocolRules: NetworkConditionProtocolRuleUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        NetworkConditionsState(protocolDefinitions = protocolRules.definitions()),
    )
    val state: StateFlow<NetworkConditionsState> = mutableState.asStateFlow()
    private val throughputSampler = NetworkThroughputSampler()
    private var runtimeMonitoringJob: Job? = null

    private var replacementNoticeJob: kotlinx.coroutines.Job? = null

    /** Uses the committed replacement result and original target, never the current tab or a pre-save guess. */
    private fun confirmReplacement(result: RuleSaveResult, context: RuleEditContext, deviceName: String) {
        if (!result.replacedExistingRule) return
        replacementNoticeJob?.cancel()
        mutableState.update { it.copy(replacementNotice = RuleReplacementNoticeState(context.editorId, deviceName)) }
        replacementNoticeJob = viewModelScope.launch {
            delay(RULE_REPLACEMENT_NOTICE_MILLIS)
            mutableState.update { current -> if (current.replacementNotice?.commandId == context.editorId)
                current.copy(replacementNotice = null) else current }
        }
    }

    private var catalogJob: Job? = null

    init { observeWorkspace(PolicyWorkspace.Other) }

    /** Captures deletion identity/revision before displaying the affected workspaces. */
    fun requestRuleDeletion(ruleId: NetworkConditionRuleId) {
        val current = state.value
        val definition = current.definitions.firstOrNull { it.rule.id == ruleId } ?: return
        mutableState.update { it.copy(pendingDeletion = PolicyDeletionRequest(ruleId.value,
            definition.editContext(current.workspace), definition.usageNames(current.workspaceNames))) }
    }
    /** Cancels deletion without changing definitions or activations. */
    fun dismissRuleDeletion() { mutableState.update { it.copy(pendingDeletion = null) } }
    /** Deletes exactly the acknowledged revision and original target. */
    fun confirmRuleDeletion() {
        val request = state.value.pendingDeletion ?: return
        dismissRuleDeletion()
        mutate(request.context.workspace) { scoped -> scoped.deleteDefinition(NetworkConditionRuleId(request.id), request.context, true) }
    }

    /** Switches presentation while preserving an already-open editor's immutable save target. */
    fun selectWorkspace(workspace: PolicyWorkspace) {
        if (workspace == state.value.workspace) return
        val monitoring = runtimeMonitoringJob?.isActive == true
        if (monitoring) stopRuntimeMonitoring()
        mutableState.update { it.copy(workspace = workspace,
            workspaceName = it.workspaceNames[workspace] ?: if (workspace == PolicyWorkspace.Other) "Other traffic" else "Selected device", configuration = com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration(),
            runtime = com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot(),
            definitions = emptyList(), ruleDetails = emptyMap(), throughput = NetworkThroughputHistory()) }
        observeWorkspace(workspace)
        if (monitoring) startRuntimeMonitoring()
    }

    private fun observeWorkspace(workspace: PolicyWorkspace) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            repository.forWorkspace(workspace).observeCatalog().collect { catalog ->
                if (state.value.workspace != workspace) return@collect
                val configuration = catalog.configuration
                val definitions = protocolRules.definitions().associateBy { it.protocolId }
                val details = configuration.rules.associate { rule ->
                    rule.id to networkConditionRuleDetails(
                        definition = definitions[rule.protocolCriteria.protocolId],
                        values = protocolRules.editorValues(rule.protocolCriteria),
                        transportOnly = rule.protocolCriteria.isTransportOnly,
                    )
                }
                mutableState.update { it.copy(configuration = configuration, ruleDetails = details, definitions = catalog.definitions, workspaceName = catalog.workspaceName, workspaceNames = catalog.workspaceNames, profileUsage = catalog.profileUsage) }
            }
        }
    }

    /** Starts the one-second UI telemetry window. Repeated calls from recomposition are harmless. */
    fun startRuntimeMonitoring() {
        if (runtimeMonitoringJob?.isActive == true) return
        throughputSampler.reset()
        mutableState.update { it.copy(throughput = NetworkThroughputHistory()) }
        val workspace = state.value.workspace
        runtimeMonitoringJob = viewModelScope.launch {
            // Ignore StateFlow's possibly stale retained value. The engine publishes a fresh baseline on subscribe.
            runtimeTelemetry.observeWorkspace(workspace).drop(1).collect { runtime ->
                val throughput = throughputSampler.accept(runtime)
                mutableState.update { current ->
                    if (current.workspace != workspace) current else current.copy(
                        runtime = runtime,
                        throughput = throughput ?: current.throughput,
                    )
                }
            }
        }
    }

    /** Stops telemetry when the shared host leaves both throughput destinations; the next visit starts fresh. */
    fun stopRuntimeMonitoring() {
        runtimeMonitoringJob?.cancel()
        runtimeMonitoringJob = null
        throughputSampler.reset()
    }

    fun setEnabled(enabled: Boolean) = mutate { scoped -> scoped.setEnabled(enabled) }

    fun selectTab(tab: NetworkConditionsTab) = mutableState.update { it.copy(activeTab = tab) }

    fun selectGlobalProfile(profileId: NetworkConditionProfileId?) = mutate { scoped ->
        scoped.setGlobalProfile(profileId)
    }

    /** Traffic quick selection applies the chosen default and master enablement as one persisted change. */
    fun activateGlobalProfile(profileId: NetworkConditionProfileId?) = mutate { scoped ->
        scoped.activateGlobalProfile(profileId)
    }

    fun saveProfile(profile: NetworkConditionProfile) = mutate { scoped -> scoped.upsertProfile(profile) }

    fun deleteProfile(profileId: NetworkConditionProfileId) = mutate { scoped ->
        if (!scoped.deleteProfile(profileId)) error("Profile is still used by a global setting or domain rule.")
    }

    fun deleteRule(ruleId: NetworkConditionRuleId) = mutate { scoped -> scoped.deleteRule(ruleId) }

    fun setRuleEnabled(rule: NetworkConditionRule, enabled: Boolean) = mutate { scoped ->
        scoped.upsertRule(rule.copy(enabled = enabled))
    }

    fun beginAddRule() {
        if (state.value.isSaving) return
        mutableState.update {
            it.copy(
                activeTab = NetworkConditionsTab.RULES,
                ruleDraft = NetworkConditionRuleDraft(
                    context = RuleEditContext(it.workspace),
                    workspaceName = it.workspaceName,
                    hostPattern = "",
                    port = "",
                    existingRuleId = null,
                    profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
                    protocolId = NetworkConditionProtocolId.TRANSPORT,
                ),
                errorMessage = null,
            )
        }
    }

    fun beginEditRule(rule: NetworkConditionRule) {
        mutableState.update {
            it.copy(
                activeTab = NetworkConditionsTab.RULES,
                ruleDraft = NetworkConditionRuleDraft(
                    context = it.definitions.firstOrNull { entry -> entry.rule.id == rule.id }?.editContext(it.workspace) ?: RuleEditContext(it.workspace),
                    workspaceName = it.workspaceName,
                    usageNames = it.definitions.firstOrNull { entry -> entry.rule.id == rule.id }?.usageNames(it.workspaceNames).orEmpty(),
                    hostPattern = (if (rule.target.wildcard) "*." else "") + rule.target.normalizedHost,
                    port = rule.target.port?.toString().orEmpty(),
                    existingRuleId = rule.id,
                    profileId = rule.profileId,
                    enabled = rule.enabled,
                    priority = rule.priority.toString(),
                    protocolId = rule.protocolCriteria.protocolId,
                    protocolValues = protocolRules.editorValues(rule.protocolCriteria),
                ),
                errorMessage = null,
            )
        }
    }

    fun prepareFromTraffic(exchangeId: String) {
        viewModelScope.launch {
            when (val result = prepareRule.execute(ExchangeId(exchangeId))) {
                is PrepareNetworkConditionRuleResult.Ready -> mutableState.update {
                    val existingRule = result.definition?.rule
                    it.copy(
                        activeTab = NetworkConditionsTab.RULES,
                        ruleDraft = NetworkConditionRuleDraft(
                            context = result.definition?.editContext(result.workspace) ?: RuleEditContext(result.workspace),
                            workspaceName = result.workspaceName,
                            hostPattern = (if (result.target.wildcard) "*." else "") +
                                result.target.normalizedHost,
                            port = result.target.port?.toString().orEmpty(),
                            existingRuleId = result.existingRuleId,
                            profileId = result.suggestedProfileId,
                            enabled = result.definition?.activeOn(result.workspace) ?: true,
                            priority = existingRule?.priority?.toString() ?: "0",
                            protocolId = result.protocolCriteria.protocolId,
                            protocolValues = protocolRules.editorValues(result.protocolCriteria),
                        ),
                        errorMessage = null,
                    )
                }
                PrepareNetworkConditionRuleResult.DestinationUnavailable -> setError(
                    "This traffic row does not contain a trusted destination host and port.",
                )
                PrepareNetworkConditionRuleResult.MissingExchange -> setError("The selected traffic row no longer exists.")
            }
        }
    }

    fun selectDraftProfile(profileId: NetworkConditionProfileId) {
        mutableState.update { current ->
            current.copy(ruleDraft = current.ruleDraft?.copy(profileId = profileId))
        }
    }

    /** Availability is independent from the immutable activation target. */
    fun setDraftShared(shared: Boolean) = updateDraft {
        if (existingRuleId != null && context.originalAvailability == RuleAvailability.Shared && !shared) this
        else copy(context = context.copy(availability = if (shared) RuleAvailability.Shared else RuleAvailability.Private(context.workspace)))
    }

    /** Starts an independent private variation; the original shared definition is untouched. */
    fun duplicateRule(rule: NetworkConditionRule) {
        beginEditRule(rule)
        updateDraft { copy(existingRuleId = null, enabled = true, context = RuleEditContext(state.value.workspace), workspaceName = state.value.workspaceName) }
    }

    /** Duplicates the currently edited fields into its original workspace's private library. */
    fun duplicateDraft() = updateDraft {
        copy(existingRuleId = null, enabled = true, usageNames = emptyList(), context = RuleEditContext(context.workspace))
    }

    fun updateDraftHostPattern(hostPattern: String) = updateDraft { copy(hostPattern = hostPattern) }

    fun updateDraftPort(port: String) = updateDraft { copy(port = port.filter(Char::isDigit)) }

    fun updateDraftEnabled(enabled: Boolean) = updateDraft { copy(enabled = enabled) }

    fun updateDraftPriority(priority: String) = updateDraft {
        copy(priority = priority.filter(Char::isDigit).take(5))
    }

    fun selectDraftProtocol(protocolId: NetworkConditionProtocolId) = updateDraft {
        val definition = state.value.protocolDefinitions.firstOrNull { it.protocolId == protocolId }
            ?: return@updateDraft this
        copy(
            protocolId = protocolId,
            protocolValues = definition.fields.map { field ->
                ProtocolCriteriaValue(
                    fieldId = field.id,
                    value = when (field) {
                        is ProtocolCriteriaFieldDefinition.Text -> ""
                        is ProtocolCriteriaFieldDefinition.Choice -> field.defaultValue
                    },
                )
            },
        )
    }

    fun updateDraftProtocolValue(fieldId: ProtocolCriteriaFieldId, value: String) = updateDraft {
        copy(
            protocolValues = protocolValues.filterNot { item -> item.fieldId == fieldId } +
                ProtocolCriteriaValue(fieldId, value),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun confirmRuleDraft() {
        if (state.value.isSaving) return
        val draft = state.value.ruleDraft ?: return
        mutableState.update { it.copy(isSaving = true) }
        mutate(draft.context.workspace, onFinished = { mutableState.update { it.copy(isSaving = false) } }) { scoped ->
            val port = draft.port.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
            if (draft.port.isNotBlank() && port == null) error("Domain rule port must be between 1 and 65535.")
            val priority = draft.priority.toIntOrNull()
                ?.takeIf { value -> value in NetworkConditionRule.MINIMUM_PRIORITY..NetworkConditionRule.MAXIMUM_PRIORITY }
                ?: error("Rule priority must be between 0 and ${NetworkConditionRule.MAXIMUM_PRIORITY}.")
            val protocolCriteria = protocolRules.createCriteria(draft.protocolId, draft.protocolValues)
                ?: error("Protocol criteria are invalid.")
            val result = scoped.saveDefinition(
                NetworkConditionRule(
                    id = draft.existingRuleId ?: NetworkConditionRuleId("condition_${Uuid.random()}"),
                    target = NetworkConditionTarget.parse(draft.hostPattern, port),
                    profileId = draft.profileId,
                    enabled = if (draft.existingRuleId == null) true else draft.enabled,
                    priority = priority,
                    protocolCriteria = protocolCriteria,
                ), draft.context,
            )
            scoped.setLastQuickAddProfile(draft.profileId)
            confirmReplacement(result, draft.context, draft.workspaceName)
            mutableState.update { if (it.ruleDraft?.context?.editorId == draft.context.editorId) it.copy(ruleDraft = null) else it }
        }
    }

    fun dismissRuleDraft() = mutableState.update { it.copy(ruleDraft = null) }
    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    fun reset() = mutate { scoped -> scoped.resetShaping() }

    private fun updateDraft(transform: NetworkConditionRuleDraft.() -> NetworkConditionRuleDraft) {
        mutableState.update { current -> current.copy(ruleDraft = current.ruleDraft?.transform()) }
    }

    private fun mutate(workspace: PolicyWorkspace = state.value.workspace, onFinished: () -> Unit = {}, block: suspend (ManageNetworkConditionsUseCase) -> Unit) {
        val scoped = repository.forWorkspace(workspace)
        viewModelScope.launch {
            try { block(scoped) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { setError(failure.message ?: "Unable to update Network Conditions.") }
            finally { onFinished() }
        }
    }

    private fun setError(message: String) = mutableState.update { it.copy(errorMessage = message) }
}
