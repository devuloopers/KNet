package com.devuloopers.knet.engine.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for breakpoint rules.
 * Encapsulates rule management, priority ordering, and reactive state streams.
 */
object BreakpointRuleRegistry {

    private val rulesMap = ConcurrentHashMap<String, BreakpointRule>()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    public val isGlobalInterceptionEnabled: StateFlow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    private val _rulesStream = MutableStateFlow<List<BreakpointRule>>(emptyList())
    public val rulesStream: StateFlow<List<BreakpointRule>> = _rulesStream.asStateFlow()

    /**
     * Toggles global interception engine state.
     */
    fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    /**
     * Adds or updates a breakpoint rule in the registry.
     */
    fun addRule(rule: BreakpointRule) {
        require(rule.id.isNotBlank()) { "Breakpoint rule ID must not be blank" }
        rulesMap[rule.id] = rule
        notifyRulesChanged()
    }

    /**
     * Removes a rule by its ID.
     */
    fun removeRule(ruleId: String) {
        rulesMap.remove(ruleId)
        notifyRulesChanged()
    }

    /**
     * Clears all registered breakpoint rules.
     */
    fun clearRules() {
        rulesMap.clear()
        notifyRulesChanged()
    }

    /**
     * Obtains a snapshot list of registered rules, sorted by priority ASC.
     */
    fun getRules(): List<BreakpointRule> {
        return rulesMap.values.sortedBy { it.priority }
    }

    private fun notifyRulesChanged() {
        _rulesStream.value = getRules()
    }
}
