package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection

/** Binds a framed-protocol adapter to its authenticated parent connection for its entire lifetime. */
public class WorkspaceProtocolMessageGate(
    private val delegate: ProtocolMessageBreakpointGate,
    private val workspace: PolicyWorkspace,
) : ProtocolMessageBreakpointGate {
    override fun mayInterceptMessage(request: HttpRequestSnapshot, protocolRoute: List<BreakpointProtocolId>, direction: TrafficDirection): Boolean =
        delegate.mayInterceptMessageFor(workspace, request, protocolRoute, direction)
    override suspend fun interceptMessage(candidate: ProtocolMessageBreakpointCandidate): ProtocolMessageBreakpointDecision =
        delegate.interceptMessage(candidate.copy(workspace = workspace))
    override fun cancelProtocolMessages(exchangeId: ExchangeId): Unit = delegate.cancelProtocolMessages(exchangeId)
}
