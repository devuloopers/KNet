package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.BreakpointRule

/** Minimal shell routing projection; payload preparation and ordinary row arrivals stay inside Traffic. */
data class TrafficHostState(
    /** One explicitly requested rule draft awaiting routing to its editor. */
    val prefilledBreakpointRule: BreakpointRule? = null,
    /** Protocol-owned criteria accompanying the prepared draft. */
    val prefilledBreakpointProtocolValues: List<ProtocolCriteriaValue> = emptyList(),
    /** Original device target of the prepared draft. */
    val breakpointWorkspace: PolicyWorkspace = PolicyWorkspace.Other,
    /** Canonical exchange -> pending decision identities required by global interception drawers. */
    val paused: Map<String, String> = emptyMap(),
)

/** Projects body-free routing facts without retaining the full row window in a shell observer. */
fun TrafficState.toHostState(): TrafficHostState = TrafficHostState(prefilledBreakpointRule,
    prefilledBreakpointProtocolValues, breakpointWorkspace, buildMap {
        transactions.forEach { row -> (row.interception as? TrafficInterceptionUiState.Paused)?.let { put(row.transactionId, it.pendingId) } }
    })
