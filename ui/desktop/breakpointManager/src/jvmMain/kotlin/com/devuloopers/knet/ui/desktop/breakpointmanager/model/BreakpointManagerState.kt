package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor

/**
 * Presentation UI State for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * @param isGlobalInterceptionEnabled Master switch toggling global proxy interception.
 * @param searchQuery Active search query filter for rules.
 * @param rules Full list of configured breakpoint rules.
 * @param isAddEditDrawerVisible True if the Add/Edit rule side drawer is open.
 * @param editingRule Non-null if editing an existing rule; null if creating a new rule.
 * @param protocolDefinitions Installed protocol rule schemas supplied by application composition.
 * @param editingProtocolValues Decoded values for the rule currently being edited.
 * @param activeEvents List of all in-flight suspended HTTP connection events.
 * @param activeEvent The currently selected or top in-flight suspended HTTP connection event.
 * @param resolvedPayloads Zero-or-one entry containing the active transaction's prepared payload.
 *   Preparation runs off-thread after queue publication, so the drawer never waits for decoding and
 *   never calls formatters during Compose rendering.
 * @param requestDescriptors Bounded protocol-aware presentation descriptors keyed by pending event ID.
 * @param activeMessageEvents Complete framed-message breakpoint queue.
 * @param activeMessageEvent Currently selected framed-message breakpoint.
 */
data class BreakpointManagerState(
    val isGlobalInterceptionEnabled: Boolean = true,
    val searchQuery: String = "",
    val rules: List<BreakpointRule> = emptyList(),
    val isAddEditDrawerVisible: Boolean = false,
    val editingRule: BreakpointRule? = null,
    val protocolDefinitions: List<BreakpointProtocolDefinition> = emptyList(),
    val editingProtocolValues: List<ProtocolCriteriaValue> = emptyList(),
    val activeEvents: List<PendingBreakpoint> = emptyList(),
    val activeEvent: PendingBreakpoint? = null,
    val resolvedPayloads: Map<String, ResolvedInterceptPayload> = emptyMap(),
    val requestDescriptors: Map<String, RequestDescriptor> = emptyMap(),
    val activeMessageEvents: List<PendingProtocolMessageBreakpoint> = emptyList(),
    val activeMessageEvent: PendingProtocolMessageBreakpoint? = null,
) {
    /**
     * Filtered list of rules matching the active search query.
     */
    val filteredRules: List<BreakpointRule>
        get() {
            if (searchQuery.isBlank()) return rules
            val query = searchQuery.trim().lowercase()
            return rules.filter { rule ->
                rule.urlPattern.lowercase().contains(query) ||
                        (rule.method?.token?.lowercase()?.contains(query) ?: "all".contains(query))
            }
        }
}
