package com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficDefaultActionUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.UpsertProtectedTrafficRuleUseCase
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficDefaultHandling
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficRuleDraft
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedTrafficState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Coordinates the protected-traffic policy workspace without exposing persistence details to Compose. */
class ProtectedTrafficViewModel(
    observeConfiguration: ObserveProtectedTrafficConfigurationUseCase,
    private val setDefaultAction: SetProtectedTrafficDefaultActionUseCase,
    private val upsertRule: UpsertProtectedTrafficRuleUseCase,
    private val deleteRule: DeleteProtectedTrafficRuleUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProtectedTrafficState())

    /** Current immutable workspace state. */
    val state: StateFlow<ProtectedTrafficState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeConfiguration().collect { configuration ->
                mutableState.update { current -> current.copy(configuration = configuration) }
            }
        }
    }

    /** Updates presentation search without changing stored policy or rule order. */
    fun updateSearchQuery(query: String): Unit = mutableState.update { it.copy(searchQuery = query) }

    /** Persists the explicit proxy's supported fallback while preventing out-of-order duplicate writes. */
    fun selectDefaultHandling(handling: ProtectedTrafficDefaultHandling) {
        if (!state.value.configuration.configurationReady || state.value.isSettingDefault) return
        mutableState.update { it.copy(isSettingDefault = true) }
        mutate(onFinished = { mutableState.update { it.copy(isSettingDefault = false) } }) { setDefaultAction(handling) }
    }

    /** Opens an empty rule editor. */
    fun beginAddRule() {
        if (!state.value.configuration.configurationReady || state.value.isSaving) return
        mutableState.update { it.copy(ruleDraft = ProtectedTrafficRuleDraft(), draftError = null) }
    }

    /** Opens the latest saved rule by stable identity; stale or removed table rows cannot replace newer policy. */
    fun beginEditRule(rule: ProtectedTrafficRule) {
        val current = state.value
        if (!current.configuration.configurationReady || current.isSaving || rule.id in current.pendingRuleIds) return
        val latest = current.configuration.rules.firstOrNull {
            it.id == rule.id && it.origin != ProtectedTrafficRuleOrigin.BUILT_IN
        } ?: return
        mutableState.update { it.copy(ruleDraft = ProtectedTrafficRuleDraft(
            id = latest.id,
            sourceApplication = latest.sourceApplication?.value.orEmpty(),
            destination = when (val destination = latest.destination) {
                is ProtectedDestinationSelector.Exact -> destination.value
                is ProtectedDestinationSelector.WildcardDomain -> "*.${destination.suffix}"
                is ProtectedDestinationSelector.Cidr -> "${destination.networkAddress}/${destination.prefixLength}"
                null -> ""
            },
            port = latest.port?.toString().orEmpty(),
            transport = latest.transport,
            ipFamily = latest.ipFamily,
            action = latest.action,
        ), draftError = null) }
    }

    /** Replaces the active rule editor values. */
    fun updateRuleDraft(draft: ProtectedTrafficRuleDraft) {
        if (!state.value.isSaving && state.value.ruleDraft != null)
            mutableState.update { it.copy(ruleDraft = draft, draftError = null) }
    }

    /** Closes the rule editor without changing policy. */
    fun dismissRuleDraft() {
        if (!state.value.isSaving) mutableState.update { it.copy(ruleDraft = null, draftError = null) }
    }

    /** Validates, normalizes, and persists the active user rule. */
    @OptIn(ExperimentalUuidApi::class)
    fun saveRuleDraft() {
        val current = state.value
        val draft = current.ruleDraft ?: return
        if (current.isSaving || !current.configuration.configurationReady || draft.id in current.pendingRuleIds) return
        mutableState.update { it.copy(isSaving = true, draftError = null) }
        viewModelScope.launch {
            try {
                val existing = draft.id?.let { id -> current.configuration.rules.firstOrNull { it.id == id }
                    ?: error("This rule was removed. Close the editor and add a new rule.") }
                val application = draft.sourceApplication.trim().takeIf(String::isNotEmpty)?.let(::ProtectedSourceApplicationId)
                val destination = draft.destination.trim().takeIf(String::isNotEmpty)?.let(ProtectedDestinationSelector::parse)
                require(draft.isPortValid) { "Port must be a number between 1 and 65535, or empty for all ports." }
                val port = draft.port.trim().takeIf(String::isNotEmpty)?.toInt()
                val proposed = existing?.copy(sourceApplication = application, destination = destination, port = port,
                    transport = draft.transport, ipFamily = draft.ipFamily, action = draft.action)
                    ?: ProtectedTrafficRule(id = ProtectedTrafficRuleId("protected_${Uuid.random()}"), action = draft.action,
                        sourceApplication = application, destination = destination, port = port,
                        transport = draft.transport, ipFamily = draft.ipFamily)
                // Imported exceptions may be toggled unchanged. A deliberate scope/action edit is a user rule.
                val rule = if (existing?.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET &&
                    (proposed.selectorKey != existing.selectorKey || proposed.action != existing.action))
                    proposed.copy(origin = ProtectedTrafficRuleOrigin.USER) else proposed
                upsertRule(rule)
                mutableState.update { it.copy(ruleDraft = null, draftError = null) }
            } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: Exception) { mutableState.update { it.copy(draftError = failure.message ?: "Unable to save this rule.") } }
            finally { mutableState.update { it.copy(isSaving = false) } }
        }
    }

    /** Enables/disables the latest authoritative row, retaining selectors and imported provenance. */
    fun setRuleEnabled(rule: ProtectedTrafficRule, enabled: Boolean) = mutateRule(rule.id) { latest -> upsertRule(latest.copy(enabled = enabled)) }

    /** Deletes a saved rule; repeat clicks cannot submit concurrent writes for the same identity. */
    fun deleteRule(ruleId: ProtectedTrafficRuleId) = mutateRule(ruleId) { deleteRule.invoke(it.id) }

    /** Dismisses a non-editor mutation error. */
    fun clearError(): Unit = mutableState.update { it.copy(errorMessage = null) }

    private fun mutateRule(id: ProtectedTrafficRuleId, operation: suspend (ProtectedTrafficRule) -> Unit) {
        val current = state.value
        if (current.isSaving || id in current.pendingRuleIds || !current.configuration.configurationReady) return
        val rule = current.configuration.rules.firstOrNull { it.id == id && it.origin != ProtectedTrafficRuleOrigin.BUILT_IN } ?: return
        mutableState.update { it.copy(pendingRuleIds = it.pendingRuleIds + id) }
        mutate(onFinished = { mutableState.update { it.copy(pendingRuleIds = it.pendingRuleIds - id) } }) { operation(rule) }
    }

    private fun mutate(onFinished: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
            catch (failure: Exception) {
                mutableState.update { it.copy(errorMessage = failure.message ?: "Unable to update protected traffic policy.") }
            } finally { onFinished() }
        }
    }
}
