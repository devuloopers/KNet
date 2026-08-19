package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointRule

/**
 * Presentation UI State for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * @param isGlobalInterceptionEnabled Master switch toggling global proxy interception.
 * @param searchQuery Active search query filter for rules.
 * @param rules Full list of configured breakpoint rules.
 * @param isAddEditDialogVisible True if the Add/Edit rule modal dialog is open.
 * @param editingRule Non-null if editing an existing rule; null if creating a new rule.
 * @param protocolDefinitions Installed protocol rule schemas supplied by application composition.
 * @param editingProtocolValues Decoded values for the rule currently being edited.
 * @param activeEvents List of all in-flight suspended HTTP connection events.
 * @param activeEvent The currently selected or top in-flight suspended HTTP connection event.
 * @param resolvedPayloads Map from transaction ID to its pre-resolved request/response payloads.
 *   Computed once off-thread by [BreakpointManagerViewModel] via [PayloadInspectionSpec.fromBytes], so that
 *   [LiveInterceptDrawer] never calls [BodyFormatterRegistry] at Compose render time.
 */
data class BreakpointManagerState(
    val isGlobalInterceptionEnabled: Boolean = true,
    val searchQuery: String = "",
    val rules: List<BreakpointRule> = emptyList(),
    val isAddEditDialogVisible: Boolean = false,
    val editingRule: BreakpointRule? = null,
    val protocolDefinitions: List<BreakpointProtocolDefinition> = emptyList(),
    val editingProtocolValues: List<ProtocolCriteriaValue> = emptyList(),
    val activeEvents: List<PendingBreakpoint> = emptyList(),
    val activeEvent: PendingBreakpoint? = null,
    val resolvedPayloads: Map<String, ResolvedInterceptPayload> = emptyMap()
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
