package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.rules.model.RuleModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thread-safe in-memory registry holding active [RuleModel] instances for Netty proxy engine.
 * Directly synchronized from Room DB via `:data:desktop`.
 */
object BreakpointRuleRegistry {

    private val _isGlobalInterceptionEnabled = MutableStateFlow(true)
    val isGlobalInterceptionEnabled: StateFlow<Boolean> = _isGlobalInterceptionEnabled.asStateFlow()

    private val _rulesMap = MutableStateFlow<Map<String, RuleModel>>(emptyMap())
    private val _rulesStream = MutableStateFlow<List<RuleModel>>(emptyList())
    val rulesStream: StateFlow<List<RuleModel>> = _rulesStream.asStateFlow()

    /**
     * Toggles global interception engine state.
     *
     * @param enabled True to enable global interception matching, false to bypass all breakpoints.
     */
    fun toggleGlobalInterception(enabled: Boolean) {
        _isGlobalInterceptionEnabled.value = enabled
    }

    /**
     * Adds or updates a domain [RuleModel] in the registry.
     *
     * @param rule The breakpoint rule to register.
     * @throws IllegalArgumentException if the rule ID is blank.
     */
    fun addRule(rule: RuleModel) {
        require(rule.id.isNotBlank()) { "Breakpoint rule ID must not be blank" }
        _rulesMap.update { current ->
            val updated = current + (rule.id to rule)
            _rulesStream.value = updated.values.toList()
            updated
        }
    }

    /**
     * Removes a rule by its unique identifier.
     *
     * @param ruleId Unique identifier of the rule to remove.
     */
    fun removeRule(ruleId: String) {
        _rulesMap.update { current ->
            val updated = current - ruleId
            _rulesStream.value = updated.values.toList()
            updated
        }
    }

    /**
     * Clears all registered breakpoint rules.
     */
    fun clearRules() {
        _rulesMap.update {
            _rulesStream.value = emptyList()
            emptyMap()
        }
    }

    /**
     * Returns a snapshot list of all currently registered rules.
     *
     * @return Immutable list of active [RuleModel] records.
     */
    fun getRules(): List<RuleModel> {
        return _rulesStream.value
    }
}
