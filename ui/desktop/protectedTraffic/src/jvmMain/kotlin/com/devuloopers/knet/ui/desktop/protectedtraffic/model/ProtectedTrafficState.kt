package com.devuloopers.knet.ui.desktop.protectedtraffic.model

import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId

/** Immutable presentation state for protected-traffic policy management. */
data class ProtectedTrafficState(
    /** Authoritative saved default and rules. */
    val configuration: ProtectedTrafficConfiguration = ProtectedTrafficConfiguration(),
    /** Currently edited manual rule, if an editor is open. */
    val ruleDraft: ProtectedTrafficRuleDraft? = null,
    /** Current validation or persistence error. */
    val errorMessage: String? = null,
)

/** User-editable values used to construct one normalized protected-traffic rule. */
data class ProtectedTrafficRuleDraft(
    /** Existing identity while editing, null for a new rule. */
    val id: ProtectedTrafficRuleId? = null,
    val sourceApplication: String = "",
    val destination: String = "",
    val port: String = "443",
    val transport: ProtectedTransportProtocol = ProtectedTransportProtocol.TCP,
    val ipFamily: ProtectedIpFamily? = null,
    val action: ProtectedTrafficAction = ProtectedTrafficAction.TUNNEL,
)
