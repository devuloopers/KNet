package com.devuloopers.knet.ui.desktop.networkconditions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleResult
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleUseCase
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDraft
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputSampler
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
    private val repository: NetworkConditionsRepository,
    private val runtimeTelemetry: NetworkConditionRuntimeTelemetry,
    private val prepareRule: PrepareNetworkConditionRuleUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NetworkConditionsState())
    val state: StateFlow<NetworkConditionsState> = mutableState.asStateFlow()
    private val throughputSampler = NetworkThroughputSampler()
    private var runtimeMonitoringJob: Job? = null

    init {
        viewModelScope.launch {
            repository.configuration.collect { configuration ->
                mutableState.update { it.copy(configuration = configuration) }
            }
        }
    }

    /** Starts the one-second UI telemetry window. Repeated calls from recomposition are harmless. */
    fun startRuntimeMonitoring() {
        if (runtimeMonitoringJob?.isActive == true) return
        throughputSampler.reset()
        mutableState.update { it.copy(throughput = NetworkThroughputHistory()) }
        runtimeMonitoringJob = viewModelScope.launch {
            // Ignore StateFlow's possibly stale retained value. The engine publishes a fresh baseline on subscribe.
            runtimeTelemetry.snapshot.drop(1).collect { runtime ->
                val throughput = throughputSampler.accept(runtime)
                mutableState.update { current ->
                    current.copy(
                        runtime = runtime,
                        throughput = throughput ?: current.throughput,
                    )
                }
            }
        }
    }

    /** Stops graph work when the destination leaves composition; history restarts on the next visit. */
    fun stopRuntimeMonitoring() {
        runtimeMonitoringJob?.cancel()
        runtimeMonitoringJob = null
        throughputSampler.reset()
    }

    fun setEnabled(enabled: Boolean) = mutate { repository.setEnabled(enabled) }

    fun selectGlobalProfile(profileId: NetworkConditionProfileId?) = mutate {
        repository.setGlobalProfile(profileId)
    }

    fun saveProfile(profile: NetworkConditionProfile) = mutate { repository.upsertProfile(profile) }

    fun deleteProfile(profileId: NetworkConditionProfileId) = mutate {
        if (!repository.deleteProfile(profileId)) error("Profile is still used by a global setting or domain rule.")
    }

    fun deleteRule(ruleId: NetworkConditionRuleId) = mutate { repository.deleteRule(ruleId) }

    fun setRuleEnabled(rule: NetworkConditionRule, enabled: Boolean) = mutate {
        repository.upsertRule(rule.copy(enabled = enabled))
    }

    fun beginAddRule() {
        val configuration = state.value.configuration
        mutableState.update {
            it.copy(
                ruleDraft = NetworkConditionRuleDraft(
                    hostPattern = "",
                    port = "",
                    existingRuleId = null,
                    profileId = configuration.lastQuickAddProfileId
                        ?: NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
                ),
                errorMessage = null,
            )
        }
    }

    fun beginEditRule(rule: NetworkConditionRule) {
        mutableState.update {
            it.copy(
                ruleDraft = NetworkConditionRuleDraft(
                    hostPattern = (if (rule.target.wildcard) "*." else "") + rule.target.normalizedHost,
                    port = rule.target.port?.toString().orEmpty(),
                    existingRuleId = rule.id,
                    profileId = rule.profileId,
                    enabled = rule.enabled,
                ),
                errorMessage = null,
            )
        }
    }

    fun prepareFromTraffic(exchangeId: String) {
        viewModelScope.launch {
            when (val result = prepareRule.execute(ExchangeId(exchangeId))) {
                is PrepareNetworkConditionRuleResult.Ready -> mutableState.update {
                    val existingRule = result.existingRuleId?.let { ruleId ->
                        it.configuration.rules.firstOrNull { rule -> rule.id == ruleId }
                    }
                    it.copy(
                        ruleDraft = NetworkConditionRuleDraft(
                            hostPattern = (if (result.target.wildcard) "*." else "") +
                                result.target.normalizedHost,
                            port = result.target.port?.toString().orEmpty(),
                            existingRuleId = result.existingRuleId,
                            profileId = result.suggestedProfileId,
                            enabled = existingRule?.enabled ?: true,
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

    fun updateDraftHostPattern(hostPattern: String) = updateDraft { copy(hostPattern = hostPattern) }

    fun updateDraftPort(port: String) = updateDraft { copy(port = port.filter(Char::isDigit)) }

    fun updateDraftEnabled(enabled: Boolean) = updateDraft { copy(enabled = enabled) }

    @OptIn(ExperimentalUuidApi::class)
    fun confirmRuleDraft() {
        val draft = state.value.ruleDraft ?: return
        mutate {
            val port = draft.port.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
            if (draft.port.isNotBlank() && port == null) error("Domain rule port must be between 1 and 65535.")
            repository.upsertRule(
                NetworkConditionRule(
                    id = draft.existingRuleId ?: NetworkConditionRuleId("condition_${Uuid.random()}"),
                    target = NetworkConditionTarget.parse(draft.hostPattern, port),
                    profileId = draft.profileId,
                    enabled = draft.enabled,
                ),
            )
            repository.setLastQuickAddProfile(draft.profileId)
            mutableState.update { it.copy(ruleDraft = null) }
        }
    }

    fun dismissRuleDraft() = mutableState.update { it.copy(ruleDraft = null) }
    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    fun reset() = mutate { repository.resetShaping() }

    private fun updateDraft(transform: NetworkConditionRuleDraft.() -> NetworkConditionRuleDraft) {
        mutableState.update { current -> current.copy(ruleDraft = current.ruleDraft?.transform()) }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { failure ->
                setError(failure.message ?: "Unable to update Network Conditions.")
            }
        }
    }

    private fun setError(message: String) = mutableState.update { it.copy(errorMessage = message) }
}
