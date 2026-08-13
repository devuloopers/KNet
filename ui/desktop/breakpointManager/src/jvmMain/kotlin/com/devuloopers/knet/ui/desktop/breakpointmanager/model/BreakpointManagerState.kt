package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.engine.interceptor.BreakpointPhase

/**
 * Presentation UI State for Breakpoint Manager Screen.
 *
 * @param isGlobalInterceptionEnabled Master switch toggling global proxy interception.
 * @param searchQuery Active search query filter for rules.
 * @param rules Full list of configured breakpoint rules.
 * @param isAddEditDialogVisible True if the Add/Edit rule modal dialog is open.
 * @param editingRule Non-null if editing an existing rule; null if creating a new rule.
 */
data class BreakpointManagerState(
    val isGlobalInterceptionEnabled: Boolean = true,
    val searchQuery: String = "",
    val rules: List<BreakpointRuleUiModel> = emptyList(),
    val isAddEditDialogVisible: Boolean = false,
    val editingRule: BreakpointRuleUiModel? = null
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
                        (rule.method?.name?.lowercase()?.contains(query) ?: "all".contains(query)) ||
                        rule.phase.name.lowercase().contains(query)
            }
        }
}
