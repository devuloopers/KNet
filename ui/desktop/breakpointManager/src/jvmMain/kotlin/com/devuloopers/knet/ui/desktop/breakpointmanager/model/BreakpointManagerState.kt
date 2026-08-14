package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.domain.rules.model.InterceptedTransaction

/**
 * Presentation UI State for Breakpoint Manager Screen and Live Intercept Drawer.
 *
 * @param isGlobalInterceptionEnabled Master switch toggling global proxy interception.
 * @param searchQuery Active search query filter for rules.
 * @param rules Full list of configured breakpoint rules.
 * @param isAddEditDialogVisible True if the Add/Edit rule modal dialog is open.
 * @param editingRule Non-null if editing an existing rule; null if creating a new rule.
 * @param activeEvents List of all in-flight suspended HTTP connection events.
 * @param activeEvent The currently selected or top in-flight suspended HTTP connection event.
 */
data class BreakpointManagerState(
    val isGlobalInterceptionEnabled: Boolean = true,
    val searchQuery: String = "",
    val rules: List<BreakpointRuleUiModel> = emptyList(),
    val isAddEditDialogVisible: Boolean = false,
    val editingRule: BreakpointRuleUiModel? = null,
    val activeEvents: List<InterceptedTransaction> = emptyList(),
    val activeEvent: InterceptedTransaction? = null
) {
    /**
     * Filtered list of rules matching the active search query.
     */
    val filteredRules: List<BreakpointRuleUiModel>
        get() {
            if (searchQuery.isBlank()) return rules
            val query = searchQuery.trim().lowercase()
            return rules.filter { rule ->
                rule.urlPattern.lowercase().contains(query) ||
                        (rule.method?.name?.lowercase()?.contains(query) ?: "all".contains(query))
            }
        }
}
