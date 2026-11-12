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
    /** Emits null for missing history or a missing port; otherwise follows authoritative saved policy. */
    public operator fun invoke(flowId: OpaqueFlowId): Flow<ProtectedTrafficFlowHandling?> = flow {
        val captured = trafficQuery.getOpaqueFlow(flowId)
        val port = captured?.destination?.port
        if (captured == null || port == null) { emit(null); return@flow }
        val destination = ProtectedDestinationSelector.parse(captured.serverName ?: captured.destination.host)
        val address = (destination as? ProtectedDestinationSelector.Exact)?.takeIf { it.isIpLiteral }?.value
        val request = ProtectedTrafficRequest(
            sourceApplication = captured.sourceApplicationId?.let(::ProtectedSourceApplicationId),
            destinationHost = (captured.serverName ?: captured.destination.host).takeIf { address == null },
            destinationAddress = address, port = port,
            transport = if (captured.transport == OpaqueTransportProtocol.TCP) ProtectedTransportProtocol.TCP else ProtectedTransportProtocol.UDP,
        )
        val policy = ProtectedTrafficPolicy()
        val admission = trafficQuery.getConnectionIngress(captured.connectionId)
        val key = automaticTlsFallbackKey(admission?.sessionId,
            admission?.let { tlsFallbackClientScope(it.ingress, it.downstreamHost) },
            captured.serverName ?: captured.destination.host, port, captured.sourceApplicationId)
        val automaticState = automaticFallback?.state ?: kotlinx.coroutines.flow.MutableStateFlow(AutomaticTlsFallbackState())
        emitAll(combine(repository.configuration, automaticState) { configuration, fallback ->
            val selected = policy.evaluate(configuration, request)
            val automaticEnabled = configuration.automaticTlsFallbackEnabled && configuration.defaultAction == ProtectedTrafficAction.INSPECT &&
                captured.transport == OpaqueTransportProtocol.TCP && captured.security == OpaqueSecurityProtocol.TLS
            val decision = if (configuration.configurationReady && automaticEnabled &&
                selected.evidence == ProtectedTrafficDecisionEvidence.GlobalDefault && key != null && key in fallback.keys)
                ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL, ProtectedTrafficDecisionEvidence.AutomaticFallback)
                else selected
            val winningId = (decision.evidence as? ProtectedTrafficDecisionEvidence.Rule)?.ruleId
            val destinationRule = configuration.rules.firstOrNull { rule ->
                rule.origin == ProtectedTrafficRuleOrigin.USER && rule.sourceApplication == null &&
                    rule.destination == destination && rule.port == port && rule.transport == request.transport &&
                    rule.ipFamily == null
            }
            ProtectedTrafficFlowHandling(flowId, decision, configuration.configurationReady,
                destinationRule, configuration.rules.firstOrNull { it.id == winningId }, automaticEnabled)
        })
    }

}
