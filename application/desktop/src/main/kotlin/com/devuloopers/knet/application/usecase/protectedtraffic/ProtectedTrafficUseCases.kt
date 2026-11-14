package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.AutomaticTlsFallback
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficDefaultHandling
import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.OpaqueTransportProtocol
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Observes the validated protected-traffic policy snapshot. */
public class ObserveProtectedTrafficConfigurationUseCase(private val repository: ProtectedTrafficRepository) {
    /** Current Other traffic configuration for compatibility with existing callers. */
    public operator fun invoke(): StateFlow<ProtectedTrafficConfiguration> = repository.configuration
    /** Manager projection includes availability and revision from the same committed snapshot. */
    public fun catalog(workspace: PolicyWorkspace): kotlinx.coroutines.flow.Flow<com.devuloopers.knet.application.contract.protectedtraffic.ProtectionPolicyCatalog> =
        repository.forWorkspace(workspace).observeCatalog()
}

/** Changes only the captured workspace's TLS fallback mode. */
public class SetProtectedTrafficDefaultActionUseCase(private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback? = null) {
    /** Persists an explicit action without automatic passthrough. */
    public suspend operator fun invoke(action: ProtectedTrafficAction, workspace: PolicyWorkspace = PolicyWorkspace.Other) {
        val scoped = repository.forWorkspace(workspace)
        scoped.setDefaultAction(action)
        automaticFallback?.clearWorkspace(workspace)
        awaitPolicy(scoped) { it.defaultAction == action && !it.automaticTlsFallbackEnabled }
    }
    /** Persists the supported mode and invalidates learning only for this workspace. */
    public suspend operator fun invoke(handling: ProtectedTrafficDefaultHandling, workspace: PolicyWorkspace = PolicyWorkspace.Other) {
        val scoped = repository.forWorkspace(workspace)
        scoped.setDefaultAction(handling.action, handling.automaticFallback)
        automaticFallback?.clearWorkspace(workspace)
        awaitPolicy(scoped) { it.defaultAction == handling.action && it.automaticTlsFallbackEnabled == handling.automaticFallback }
    }
    private suspend fun awaitPolicy(scoped: ProtectedTrafficRepository, matches: (ProtectedTrafficConfiguration) -> Boolean) {
        withTimeoutOrNull(5_000) { scoped.configuration.first { it.configurationReady && matches(it) } }
            ?: error("The default was saved, but its current policy could not be confirmed.")
    }
}

/** Creates or replaces a definition, preserving independent device activation. */
public class UpsertProtectedTrafficRuleUseCase(private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback? = null) {
    /** Updates an existing row/activation through its fixed workspace adapter. */
    public suspend operator fun invoke(rule: ProtectedTrafficRule, workspace: PolicyWorkspace = PolicyWorkspace.Other) {
        val scoped = repository.forWorkspace(workspace)
        scoped.upsertRule(rule)
        automaticFallback?.forgetWorkspace(workspace, rule)
        awaitRule(scoped, rule)
    }
    /** Saves the captured library target, revision and activation in one transaction. */
    public suspend fun saveDefinition(rule: ProtectedTrafficRule, context: RuleEditContext): RuleSaveResult {
        val scoped = repository.forWorkspace(context.workspace)
        val result = scoped.saveDefinition(rule, context)
        automaticFallback?.forgetWorkspace(context.workspace, rule)
        awaitRule(scoped, rule)
        return result
    }
    private suspend fun awaitRule(scoped: ProtectedTrafficRepository, rule: ProtectedTrafficRule) {
        withTimeoutOrNull(5_000) { scoped.configuration.first { it.configurationReady && rule in it.rules } }
            ?: error("The rule was saved, but its current policy could not be confirmed.")
    }
}

/** Explicit definition deletion; Shared usage acknowledgement is separate from device activation. */
public class DeleteProtectedTrafficRuleUseCase(private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback? = null) {
    /** Compatibility entry point for scoped manager actions. */
    public suspend operator fun invoke(ruleId: ProtectedTrafficRuleId, workspace: PolicyWorkspace = PolicyWorkspace.Other) {
        val scoped = repository.forWorkspace(workspace)
        val previous = scoped.configuration.value.rules.firstOrNull { it.id == ruleId }
        scoped.deleteRule(ruleId)
        previous?.let { automaticFallback?.forgetWorkspace(workspace, it) }
    }
    /** Deletes the captured revision only after the UI has presented any shared impact. */
    public suspend fun deleteDefinition(id: ProtectedTrafficRuleId, context: RuleEditContext, acknowledgeSharedUsage: Boolean) {
        repository.forWorkspace(context.workspace).deleteDefinition(id, context, acknowledgeSharedUsage)
        automaticFallback?.clearWorkspace(context.workspace)
    }
}

/** Selector scope offered by a protected-flow Traffic context action. */
public enum class ProtectedTrafficQuickRuleScope {
    /** Match the verified source package or signing identity. */
    SOURCE_APPLICATION,

    /** Match the visible destination and port. */
    DESTINATION,
}

/** Result of converting one opaque Traffic row into a durable policy rule. */
public sealed interface SetProtectedTrafficFlowActionResult {
    /** A normalized user rule was persisted. */
    public data class Saved(public val rule: ProtectedTrafficRule) : SetProtectedTrafficFlowActionResult

    /** The selected flow no longer exists. */
    public data object MissingFlow : SetProtectedTrafficFlowActionResult

    /** The exact destination override was removed; inherited policy applies to new connections. */
    public data object DefaultRestored : SetProtectedTrafficFlowActionResult

    /** The selected flow has no verified application identity. */
    public data object SourceApplicationUnavailable : SetProtectedTrafficFlowActionResult

    /** The selected flow has no valid destination selector. */
    public data object DestinationUnavailable : SetProtectedTrafficFlowActionResult
}

/** Creates an application- or destination-scoped rule directly from one payload-opaque Traffic row. */
public class SetProtectedTrafficFlowActionUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback? = null,
) {
    /**
     * Resolves [flowId], creates the requested [scope], and persists [action] for future flows.
     *
     * @return A typed result without trusting caller-supplied host or package strings.
     * @throws IllegalStateException if authoritative settings or committed-rule publication remain unavailable.
     */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun execute(
        flowId: OpaqueFlowId,
        action: ProtectedTrafficAction,
        scope: ProtectedTrafficQuickRuleScope,
    ): SetProtectedTrafficFlowActionResult {
        val flow = trafficQuery.getOpaqueFlow(flowId) ?: return SetProtectedTrafficFlowActionResult.MissingFlow
        val workspace = PolicyWorkspace.fromIngress(trafficQuery.getConnectionIngress(flow.connectionId)?.ingress)
        val scoped = repository.forWorkspace(workspace)
        if (scope == ProtectedTrafficQuickRuleScope.DESTINATION && flow.destination.port == null)
            return SetProtectedTrafficFlowActionResult.DestinationUnavailable
        val application = when (scope) {
            ProtectedTrafficQuickRuleScope.SOURCE_APPLICATION -> flow.sourceApplicationId
                ?.let(::ProtectedSourceApplicationId)
                ?: return SetProtectedTrafficFlowActionResult.SourceApplicationUnavailable
            ProtectedTrafficQuickRuleScope.DESTINATION -> null
        }
        val destination = when (scope) {
            ProtectedTrafficQuickRuleScope.SOURCE_APPLICATION -> null
            ProtectedTrafficQuickRuleScope.DESTINATION -> runCatching {
                ProtectedDestinationSelector.parse(flow.serverName ?: flow.destination.host)
            }.getOrNull() ?: return SetProtectedTrafficFlowActionResult.DestinationUnavailable
        }
        val candidate = ProtectedTrafficRule(
            id = ProtectedTrafficRuleId("protected_${Uuid.random()}"),
            action = action,
            sourceApplication = application,
            destination = destination,
            port = flow.destination.port.takeIf { scope == ProtectedTrafficQuickRuleScope.DESTINATION },
            transport = when (flow.transport) {
                OpaqueTransportProtocol.TCP -> ProtectedTransportProtocol.TCP
                OpaqueTransportProtocol.UDP -> ProtectedTransportProtocol.UDP
            },
        )
        val catalog = withTimeoutOrNull(POLICY_PUBLICATION_TIMEOUT_MILLIS) { scoped.observeCatalog().first { it.ready } }
            ?: error("Current connection handling could not be loaded. Try again when policy settings are ready.")
        val existingDefinition = catalog.definitions.firstOrNull { it.availability == RuleAvailability.Private(workspace) &&
            it.rule.origin == com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin.USER && it.rule.selectorKey == candidate.selectorKey }
        val existing = existingDefinition?.rule
        val saved = candidate.copy(id = existing?.id ?: candidate.id)
        scoped.saveDefinition(saved, RuleEditContext(workspace, RuleAvailability.Private(workspace), existingDefinition?.revision, activateAfterSave = true))
        automaticFallback?.forgetWorkspace(workspace, saved)
        // Room invalidation is asynchronous. A successful action includes observing the committed rule;
        // otherwise an immediately revisited row could still render the previous rule as actionable.
        withTimeoutOrNull(POLICY_PUBLICATION_TIMEOUT_MILLIS) {
            scoped.configuration.first { current -> current.configurationReady && saved in current.rules }
        } ?: error("The rule was saved, but its current policy could not be confirmed.")
        return SetProtectedTrafficFlowActionResult.Saved(saved)
    }

    private companion object {
        const val POLICY_PUBLICATION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Removes only this destination's explicit user override; unrelated/application rules remain authoritative. */
public class ResetProtectedTrafficFlowActionUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback,
) {
    /** Resolves retained admission metadata, removes the exact destination rule and confirms publication. */
    public suspend fun execute(flowId: OpaqueFlowId): SetProtectedTrafficFlowActionResult {
        val flow = trafficQuery.getOpaqueFlow(flowId) ?: return SetProtectedTrafficFlowActionResult.MissingFlow
        val workspace = PolicyWorkspace.fromIngress(trafficQuery.getConnectionIngress(flow.connectionId)?.ingress)
        val scoped = repository.forWorkspace(workspace)
        val destination = runCatching { ProtectedDestinationSelector.parse(flow.serverName ?: flow.destination.host) }
            .getOrNull() ?: return SetProtectedTrafficFlowActionResult.DestinationUnavailable
        val port = flow.destination.port ?: return SetProtectedTrafficFlowActionResult.DestinationUnavailable
        val transport = if (flow.transport == OpaqueTransportProtocol.TCP) ProtectedTransportProtocol.TCP else ProtectedTransportProtocol.UDP
        val configuration = withTimeoutOrNull(5_000) { scoped.configuration.first { it.configurationReady } }
            ?: error("Current connection handling could not be loaded.")
        val matching = configuration.rules.filter { rule ->
            rule.origin == com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin.USER &&
                rule.sourceApplication == null && rule.destination == destination && rule.port == port &&
                rule.transport == transport && rule.ipFamily == null
        }.map { it.id }.toSet()
        matching.forEach { scoped.resetRule(it) }
        automaticFallback.forgetWorkspace(workspace, ProtectedTrafficRule(ProtectedTrafficRuleId("reset-destination"), action = ProtectedTrafficAction.INSPECT,
            destination = destination, port = port, transport = transport))
        withTimeoutOrNull(5_000) {
            scoped.configuration.first { current -> current.configurationReady && current.rules.none { it.enabled && it.id in matching } }
        } ?: error("The override was removed, but its current policy could not be confirmed.")
        return SetProtectedTrafficFlowActionResult.DefaultRestored
    }
}
