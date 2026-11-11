package com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficDefaultActionUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.UpsertProtectedTrafficRuleUseCase
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
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

    /** Persists the global action applied when no explicit or built-in rule matches. */
    fun selectDefaultAction(action: ProtectedTrafficAction): Unit = mutate { setDefaultAction(action) }

    /** Opens an empty rule editor. */
    fun beginAddRule(): Unit = mutableState.update { it.copy(ruleDraft = ProtectedTrafficRuleDraft()) }

    /** Opens a user rule with its stable identity so Save edits rather than duplicates it. */
    fun beginEditRule(rule: ProtectedTrafficRule): Unit = mutableState.update { current ->
        current.copy(ruleDraft = ProtectedTrafficRuleDraft(
            id = rule.id,
            sourceApplication = rule.sourceApplication?.value.orEmpty(),
            destination = when (val destination = rule.destination) {
                is ProtectedDestinationSelector.Exact -> destination.value
                is ProtectedDestinationSelector.WildcardDomain -> "*.${destination.suffix}"
                is ProtectedDestinationSelector.Cidr -> "${destination.networkAddress}/${destination.prefixLength}"
                null -> ""
            },
            port = rule.port?.toString().orEmpty(),
            transport = rule.transport ?: ProtectedTransportProtocol.TCP,
            ipFamily = rule.ipFamily,
            action = rule.action,
        ))
    }

    /** Replaces the active rule editor values. */
    fun updateRuleDraft(draft: ProtectedTrafficRuleDraft): Unit = mutableState.update { it.copy(ruleDraft = draft) }

    /** Closes the rule editor without changing policy. */
    fun dismissRuleDraft(): Unit = mutableState.update { it.copy(ruleDraft = null) }

    /** Validates, normalizes, and persists the active user rule. */
    @OptIn(ExperimentalUuidApi::class)
    fun saveRuleDraft(): Unit {
        val draft = state.value.ruleDraft ?: return
        mutate {
            val application = draft.sourceApplication.trim().takeIf(String::isNotEmpty)
                ?.let(::ProtectedSourceApplicationId)
            val destination = draft.destination.trim().takeIf(String::isNotEmpty)
                ?.let(ProtectedDestinationSelector::parse)
            val port = draft.port.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                ?: draft.port.takeIf(String::isNotBlank)?.let {
                    throw IllegalArgumentException("Port must be a number between 1 and 65535.")
                }
            upsertRule(
                ProtectedTrafficRule(
                    id = draft.id ?: ProtectedTrafficRuleId("protected_${Uuid.random()}"),
                    action = draft.action,
                    enabled = draft.id?.let { id -> state.value.configuration.rules.firstOrNull { it.id == id }?.enabled } ?: true,
                    sourceApplication = application,
                    destination = destination,
                    port = port,
                    transport = draft.transport,
                    ipFamily = draft.ipFamily,
                ),
            )
            mutableState.update { current -> current.copy(ruleDraft = null, errorMessage = null) }
        }
    }

    /** Enables or disables one user-authored rule while preserving its selectors. */
    fun setRuleEnabled(rule: ProtectedTrafficRule, enabled: Boolean): Unit =
        mutate { upsertRule(rule.copy(enabled = enabled)) }

    /** Deletes one user-authored rule. */
    fun deleteRule(ruleId: ProtectedTrafficRuleId): Unit = mutate { deleteRule.invoke(ruleId) }

    /** Dismisses the current mutation or validation error. */
    fun clearError(): Unit = mutableState.update { it.copy(errorMessage = null) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                mutableState.update { current ->
                    current.copy(errorMessage = failure.message ?: "Unable to update protected traffic policy.")
                }
            }
        }
    }
}
