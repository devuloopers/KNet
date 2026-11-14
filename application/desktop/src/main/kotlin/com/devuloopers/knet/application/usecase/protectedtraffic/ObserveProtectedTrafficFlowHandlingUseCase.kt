package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.devuloopers.knet.application.contract.protectedtraffic.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.captureWorkspace

/** Live future-connection policy, kept separate from a selected flow's immutable captured outcome. */
public data class ProtectedTrafficFlowHandling(
    /** Selected retained flow; used to fence presentation updates after selection changes. */
    public val flowId: OpaqueFlowId,
    /** Current explicit/default rule result; observing it never changes connection handling. */
    public val decision: ProtectedTrafficDecision,
    /** Whether authoritative policy settings are available. */
    public val configurationReady: Boolean,
    /** Equivalent user-authored destination/port/transport rule, including a currently disabled rule. */
    public val destinationRule: ProtectedTrafficRule? = null,
    /** Rule actually controlling this flow under current precedence; null for the global default. */
    public val winningRule: ProtectedTrafficRule? = null,
    /** Whether the inherited Inspect default permits automatic passthrough. */
    public val automaticFallbackEnabled: Boolean = false,
)

/** Reads one retained flow and observes saved/default policy without opening sockets or changing history. */
public class ObserveProtectedTrafficFlowHandlingUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: ProtectedTrafficRepository,
    private val automaticFallback: AutomaticTlsFallback? = null,
) {
    /**
     * Current read-only manual/default presentation from bounded canonical row metadata and its explicit
     * source filter. It never authorizes a write. Mixed-source views and ambiguous learned fallback must
     * wait for the canonical connection/session lookup; a different connection's fallback is never reused.
     */
    public fun currentManualHandling(captured: OpaqueFlowSnapshot, source: TrafficSource): ProtectedTrafficFlowHandling? {
        if (source == TrafficSource.All) return null
        val configuration = repository.forWorkspace(source.captureWorkspace()).configuration.value
        return manualHandling(captured, configuration)
    }

    /** Keeps the immediate policy view live while canonical metadata/ingress is being resolved. */
    public fun observeSelection(captured: OpaqueFlowSnapshot, source: TrafficSource): Flow<ProtectedTrafficFlowHandling?> {
        val previews = if (source == TrafficSource.All) kotlinx.coroutines.flow.flowOf(null)
            else repository.forWorkspace(source.captureWorkspace()).configuration.map { manualHandling(captured, it) }
        val canonical = invoke(captured.id).map<ProtectedTrafficFlowHandling?, ResolvedHandling> { ResolvedHandling.Ready(it) }
            .onStart { emit(ResolvedHandling.Loading) }
        return combine(previews, canonical) { preview, resolved ->
            when (resolved) {
                ResolvedHandling.Loading -> preview
                is ResolvedHandling.Ready -> resolved.handling
            }
        }.distinctUntilChanged()
    }

    private sealed interface ResolvedHandling {
        data object Loading : ResolvedHandling
        data class Ready(val handling: ProtectedTrafficFlowHandling?) : ResolvedHandling
    }

    private fun manualHandling(captured: OpaqueFlowSnapshot, configuration: ProtectedTrafficConfiguration): ProtectedTrafficFlowHandling? {
        if (!configuration.configurationReady) return null
        return try {
            val request = requestFor(captured) ?: return null
            val selected = ProtectedTrafficPolicy().evaluate(configuration, request)
            val automaticEnabled = automaticEnabled(captured, configuration)
            if (automaticEnabled && selected.evidence == ProtectedTrafficDecisionEvidence.GlobalDefault) return null
            handling(captured, configuration, request, selected, automaticEnabled)
        } catch (_: IllegalArgumentException) {
            // Retained malformed metadata must follow the asynchronous unavailable/error path, never
            // throw from a first-frame projection running during Compose layout.
            null
        }
    }

    private fun automaticEnabled(captured: OpaqueFlowSnapshot, configuration: ProtectedTrafficConfiguration): Boolean =
        configuration.automaticTlsFallbackEnabled && configuration.defaultAction == ProtectedTrafficAction.INSPECT &&
            captured.transport == OpaqueTransportProtocol.TCP && captured.security == OpaqueSecurityProtocol.TLS

    private fun requestFor(captured: OpaqueFlowSnapshot): ProtectedTrafficRequest? {
        val port = captured.destination.port ?: return null
        val destination = ProtectedDestinationSelector.parse(captured.serverName ?: captured.destination.host)
        val address = (destination as? ProtectedDestinationSelector.Exact)?.takeIf { it.isIpLiteral }?.value
        return ProtectedTrafficRequest(sourceApplication = captured.sourceApplicationId?.let(::ProtectedSourceApplicationId),
            destinationHost = (captured.serverName ?: captured.destination.host).takeIf { address == null },
            destinationAddress = address, port = port,
            transport = if (captured.transport == OpaqueTransportProtocol.TCP) ProtectedTransportProtocol.TCP else ProtectedTransportProtocol.UDP)
    }

    private fun handling(captured: OpaqueFlowSnapshot, configuration: ProtectedTrafficConfiguration,
        request: ProtectedTrafficRequest, decision: ProtectedTrafficDecision, automaticEnabled: Boolean): ProtectedTrafficFlowHandling {
        val destination = ProtectedDestinationSelector.parse(captured.serverName ?: captured.destination.host)
        val winningId = (decision.evidence as? ProtectedTrafficDecisionEvidence.Rule)?.ruleId
        val destinationRule = configuration.rules.sortedByDescending { it.enabled }.firstOrNull { rule ->
            rule.origin == ProtectedTrafficRuleOrigin.USER && rule.sourceApplication == null &&
                rule.destination == destination && rule.port == request.port && rule.transport == request.transport && rule.ipFamily == null
        }
        return ProtectedTrafficFlowHandling(captured.id, decision, configuration.configurationReady,
            destinationRule, configuration.rules.firstOrNull { it.id == winningId }, automaticEnabled)
    }

    /** Emits null for missing history or a missing port; otherwise follows authoritative saved policy. */
    public operator fun invoke(flowId: OpaqueFlowId): Flow<ProtectedTrafficFlowHandling?> = flow {
        val captured = trafficQuery.getOpaqueFlow(flowId)
        val port = captured?.destination?.port
        if (captured == null || port == null) { emit(null); return@flow }
        val request = checkNotNull(requestFor(captured))
        val policy = ProtectedTrafficPolicy()
        val admission = trafficQuery.getConnectionIngress(captured.connectionId)
        val scoped = repository.forWorkspace(com.devuloopers.knet.domain.policy.PolicyWorkspace.fromIngress(admission?.ingress))
        val key = automaticTlsFallbackKey(admission?.sessionId,
            admission?.let { tlsFallbackClientScope(it.ingress, it.downstreamHost) },
            captured.serverName ?: captured.destination.host, port, captured.sourceApplicationId)
        val automaticState = automaticFallback?.state ?: kotlinx.coroutines.flow.MutableStateFlow(AutomaticTlsFallbackState())
        emitAll(combine(scoped.configuration, automaticState) { configuration, fallback ->
            val selected = policy.evaluate(configuration, request)
            val automaticEnabled = automaticEnabled(captured, configuration)
            val decision = if (configuration.configurationReady && automaticEnabled &&
                selected.evidence == ProtectedTrafficDecisionEvidence.GlobalDefault && key != null && key in fallback.keys)
                ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL, ProtectedTrafficDecisionEvidence.AutomaticFallback)
                else selected
            handling(captured, configuration, request, decision, automaticEnabled)
        })
    }

}
