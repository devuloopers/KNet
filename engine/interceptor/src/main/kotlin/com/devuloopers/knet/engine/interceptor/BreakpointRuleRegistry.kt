package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory registry holding active [RuleModel] instances for Netty proxy engine.
 * Directly synchronized from Room DB via `:data:desktop`.
 */
object BreakpointRuleRegistry {

    private val rulesMap = ConcurrentHashMap<String, RuleModel>()

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    public val isGlobalInterceptionEnabled: StateFlow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    private val _rulesStream = MutableStateFlow<List<RuleModel>>(emptyList())
    public val rulesStream: StateFlow<List<RuleModel>> = _rulesStream.asStateFlow()

    /**
     * Toggles global interception engine state.
     */
    fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    /**
     * Adds or updates a domain [RuleModel] in the registry.
     */
    fun addRule(rule: RuleModel) {
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
     * Returns a snapshot list of all currently registered rules.
     */
    fun getRules(): List<RuleModel> {
        return rulesMap.values.toList()
    }

    private fun notifyRulesChanged() {
        _rulesStream.value = getRules()
    }
}
