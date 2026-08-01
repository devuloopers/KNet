package com.devuloopers.knet.engine.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for breakpoint rules.
 * Encapsulates rule management and priority ordering.
 */
object BreakpointRuleRegistry {

    private val rules = ConcurrentHashMap<String, BreakpointRule>()

    /**
     * Adds or updates a breakpoint rule in the registry.
     */
    fun addRule(rule: BreakpointRule) {
        require(rule.id.isNotBlank()) { "Breakpoint rule ID must not be blank" }
        rules[rule.id] = rule
    }

    /**
     * Removes a rule by its ID.
     */
    fun removeRule(ruleId: String) {
        rules.remove(ruleId)
    }

    /**
     * Clears all registered breakpoint rules.
     */
    fun clearRules() {
        rules.clear()
    }

    /**
     * Obtains a snapshot list of registered rules, sorted by priority ASC.
     */
    fun getRules(): List<BreakpointRule> {
        return rules.values.sortedBy { it.priority }
    }
}
