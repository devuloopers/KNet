package com.devuloopers.knet.data.desktop.protectedtraffic

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
) : TlsInterceptionPolicy {
    /** Evaluates one CONNECT route against the latest atomically published policy snapshot. */
    override fun decide(request: TlsInterceptionRequest): TlsInterceptionDecision {
        val destination = request.serverName ?: request.connectHost
        val parsed = runCatching { ProtectedDestinationSelector.parse(destination) }.getOrNull()
        val address = (parsed as? ProtectedDestinationSelector.Exact)
            ?.takeIf(ProtectedDestinationSelector.Exact::isIpLiteral)
            ?.value
        val domainDecision = policy.evaluate(
            configuration(),
            ProtectedTrafficRequest(
                sourceApplication = request.sourceApplication?.value?.let(::ProtectedSourceApplicationId),
                destinationHost = destination.takeIf { address == null },
                destinationAddress = address,
                port = request.connectPort,
                transport = ProtectedTransportProtocol.TCP,
            ),
        )
        val mode = when (domainDecision.action) {
            ProtectedTrafficAction.INSPECT -> TlsInterceptionMode.INSPECT
            ProtectedTrafficAction.TUNNEL,
            ProtectedTrafficAction.BYPASS,
            -> TlsInterceptionMode.TUNNEL
            ProtectedTrafficAction.BLOCK -> TlsInterceptionMode.BLOCK
        }
        val ruleEvidence = domainDecision.evidence as? ProtectedTrafficDecisionEvidence.Rule
        val ruleId = ruleEvidence?.ruleId?.value?.let(::TlsInterceptionRuleId)
        return TlsInterceptionDecision(
            mode = mode,
            ruleId = ruleId,
            policyAction = TlsInterceptionPolicyAction.valueOf(domainDecision.action.name),
            policyGroupId = ruleEvidence?.groupId?.value,
        )
    }
}
