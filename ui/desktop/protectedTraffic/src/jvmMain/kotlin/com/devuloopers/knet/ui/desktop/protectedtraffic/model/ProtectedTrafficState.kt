package com.devuloopers.knet.ui.desktop.protectedtraffic.model

import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId

/** Immutable presentation state for protected-traffic policy management. */
data class ProtectedTrafficState(
    /** Authoritative saved default and rules. */
    val configuration: ProtectedTrafficConfiguration = ProtectedTrafficConfiguration(configurationReady = false),
    /** Currently edited manual rule, if an editor is open. */
    val ruleDraft: ProtectedTrafficRuleDraft? = null,
    /** Current validation or persistence error. */
    val errorMessage: String? = null,
    /** Local, literal search; never changes saved policy or rule precedence. */
    val searchQuery: String = "",
    /** Inline editor error; a failed save leaves the draft intact. */
    val draftError: String? = null,
    /** Prevents duplicate submissions or switching the editor while saving. */
    val isSaving: Boolean = false,
    /** Rules with an in-flight enable/disable/delete operation. */
    val pendingRuleIds: Set<ProtectedTrafficRuleId> = emptySet(),
    /** Prevents concurrent default-action writes. */
    val isSettingDefault: Boolean = false,
) {
    /** Visible user/imported rules retain the repository's order and exclude retired built-ins. */
    val filteredRules: List<com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule>
        get() {
            val terms = searchQuery.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            return configuration.rules.filter { rule ->
                rule.origin != com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin.BUILT_IN &&
                    terms.all { term -> rule.searchText.contains(term, ignoreCase = true) }
            }
        }
}

/** User-editable values used to construct one normalized protected-traffic rule. */
data class ProtectedTrafficRuleDraft(
    /** Existing identity while editing, null for a new rule. */
    val id: ProtectedTrafficRuleId? = null,
    /** Optional verified application identity supplied by the user. */
    val sourceApplication: String = "",
    /** Host, wildcard domain, IP address or CIDR input. */
    val destination: String = "",
    /** Optional port input; blank means all ports. */
    val port: String = "443",
    /** Optional transport; null retains all transports. */
    val transport: ProtectedTransportProtocol? = ProtectedTransportProtocol.TCP,
    /** Optional address-family restriction. */
    val ipFamily: ProtectedIpFamily? = null,
    /** Handling applied to matching new connections. */
    val action: ProtectedTrafficAction = ProtectedTrafficAction.TUNNEL,
) {
    /** Blank port means all ports; invalid pasted input is rejected rather than silently rewritten. */
    val isPortValid: Boolean get() = port.isBlank() || port.trim().toIntOrNull()?.let { it in 1..65_535 } == true
}
