package com.devuloopers.knet.application.usecase.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
)

/** Reads one retained flow and observes saved/default policy without opening sockets or changing history. */
public class ObserveProtectedTrafficFlowHandlingUseCase(
    private val trafficQuery: TrafficQuery,
    private val repository: ProtectedTrafficRepository,
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
        emitAll(repository.configuration.map { configuration ->
            val decision = policy.evaluate(configuration, request)
            val winningId = (decision.evidence as? ProtectedTrafficDecisionEvidence.Rule)?.ruleId
            val destinationRule = configuration.rules.firstOrNull { rule ->
                rule.origin == ProtectedTrafficRuleOrigin.USER && rule.sourceApplication == null &&
                    rule.destination == destination && rule.port == port && rule.transport == request.transport &&
                    rule.ipFamily == null
            }
            ProtectedTrafficFlowHandling(flowId, decision, configuration.configurationReady,
                destinationRule, configuration.rules.firstOrNull { it.id == winningId })
        })
    }

}
