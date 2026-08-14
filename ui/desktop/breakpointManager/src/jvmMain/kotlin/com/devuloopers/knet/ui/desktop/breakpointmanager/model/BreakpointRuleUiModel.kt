package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria

/**
 * Presentation UI model for a breakpoint interception rule.
 *
 * @param id Unique identifier of the rule.
 * @param urlPattern URL regular expression or wildcard pattern.
 * @param method Target [HttpMethod] filter or null to match any HTTP method.
 * @param phase Target [BreakpointPhase] filter (REQUEST, RESPONSE, BOTH).
 * @param enabled Whether this rule is actively evaluated.
 * @param protocolCriteria Extensible protocol-specific criteria ([ProtocolMatchCriteria]).
 */
public data class BreakpointRuleUiModel(
    val id: String,
    val urlPattern: String,
    val method: HttpMethod? = null,
    val phase: BreakpointPhase = BreakpointPhase.BOTH,
    val enabled: Boolean = true,
    val protocolCriteria: ProtocolMatchCriteria = ProtocolMatchCriteria.HttpDefault
)
