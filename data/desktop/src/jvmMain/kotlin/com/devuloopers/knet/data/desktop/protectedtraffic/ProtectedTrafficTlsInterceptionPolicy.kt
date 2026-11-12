package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.engine.proxy.tls.TlsInspectionFailure
import com.devuloopers.knet.traffic.model.InspectionFailureReason
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficDecisionEvidence
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficPolicy
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRequest
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionDecision
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicyAction
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRequest
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRuleId
import com.devuloopers.knet.traffic.model.ProtectionDecisionOrigin

/**
 * Adapts the shared protected-traffic policy to the desktop explicit-proxy CONNECT boundary.
 *
 * A true `BYPASS` must be applied by a platform packet ingress before the flow enters KNet. Once a
 * client has explicitly issued CONNECT to the desktop proxy, this adapter preserves availability by
 * using a payload-opaque tunnel; it never claims that the already-proxied connection bypassed KNet.
 */
class ProtectedTrafficTlsInterceptionPolicy(
    private val configuration: () -> ProtectedTrafficConfiguration,
    private val policy: ProtectedTrafficPolicy = ProtectedTrafficPolicy(),
    private val automaticFallback: AutomaticTlsFallback? = null,
    private val inspectionAuthorityIdentity: () -> String? = { null },
) : TlsInterceptionPolicy {
    /** Evaluates one CONNECT route against the latest atomically published policy snapshot. */
    override fun decide(request: TlsInterceptionRequest): TlsInterceptionDecision {
        val destination = request.serverName ?: request.connectHost
        val parsed = runCatching { ProtectedDestinationSelector.parse(destination) }.getOrNull()
        val address = (parsed as? ProtectedDestinationSelector.Exact)
            ?.takeIf(ProtectedDestinationSelector.Exact::isIpLiteral)
            ?.value
        val activeConfiguration = configuration()
        val generation = automaticFallback?.authorize(activeConfiguration, inspectionAuthorityIdentity())
        val domainDecision = policy.evaluate(
            activeConfiguration,
            ProtectedTrafficRequest(
                sourceApplication = request.sourceApplication?.value?.let(::ProtectedSourceApplicationId),
                destinationHost = destination.takeIf { address == null },
                destinationAddress = address,
                port = request.connectPort,
                transport = ProtectedTransportProtocol.TCP,
            ),
        )
        val key = automaticTlsFallbackKey(request.clientContext?.sessionId, request.clientContext?.client,
            destination, request.connectPort, request.sourceApplication?.value)
        val eligible = generation != null && key != null && request.clientHelloObserved &&
            domainDecision.evidence == ProtectedTrafficDecisionEvidence.GlobalDefault && domainDecision.action == ProtectedTrafficAction.INSPECT
        if (eligible && key in automaticFallback!!.state.value.keys)
            return TlsInterceptionDecision(TlsInterceptionMode.TUNNEL, origin = ProtectionDecisionOrigin.AUTOMATIC)
        val mode = when (domainDecision.action) {
            ProtectedTrafficAction.INSPECT -> TlsInterceptionMode.INSPECT
            ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.BYPASS -> TlsInterceptionMode.TUNNEL
            ProtectedTrafficAction.BLOCK -> TlsInterceptionMode.BLOCK
        }
        val ruleEvidence = domainDecision.evidence as? ProtectedTrafficDecisionEvidence.Rule
        val ruleId = ruleEvidence?.ruleId?.value?.let(::TlsInterceptionRuleId)
        return TlsInterceptionDecision(
            mode = mode,
            ruleId = ruleId,
            policyAction = TlsInterceptionPolicyAction.valueOf(domainDecision.action.name),
            automaticFallbackGeneration = generation.takeIf { eligible },
            policyGroupId = ruleEvidence?.groupId?.value,
            origin = when {
                ruleEvidence?.groupId != null || ruleEvidence != null && activeConfiguration.rules.any {
                    it.id == ruleEvidence.ruleId && it.origin == com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin.MIGRATED_PRESET
                } -> ProtectionDecisionOrigin.PRESET
                ruleEvidence != null -> ProtectionDecisionOrigin.MANUAL
                else -> ProtectionDecisionOrigin.GLOBAL
            },
        )
    }

    /** Records only explicit downstream certificate rejection from the originally authorized inspection epoch. */
    override fun inspectionFailed(request: TlsInterceptionRequest, decision: TlsInterceptionDecision, failure: TlsInspectionFailure) {
        val generation = decision.automaticFallbackGeneration ?: return
        if (!failure.certificatePresented || failure.reason != InspectionFailureReason.CERTIFICATE_REJECTED ||
            decision.mode != TlsInterceptionMode.INSPECT || decision.origin != ProtectionDecisionOrigin.GLOBAL) return
        // Re-evaluation synchronizes certificate/settings changes and gives new manual rules precedence.
        val current = decide(request)
        if (current.automaticFallbackGeneration != generation || current.mode != TlsInterceptionMode.INSPECT) return
        val key = automaticTlsFallbackKey(request.clientContext?.sessionId, request.clientContext?.client,
            request.serverName ?: request.connectHost, request.connectPort, request.sourceApplication?.value) ?: return
        automaticFallback?.record(key, generation)
    }

}
