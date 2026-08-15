package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.core.logger.KNetLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "TrafficModifierManager"

/**
 * Thread-safe reactive registry maintaining active [ModifierRule], [MapLocalRule], and [MapRemoteRule] sets.
 *
 * Stores pre-sorted immutable lists updated atomically via [MutableStateFlow] to guarantee
 * zero-allocation, O(1) concurrent lookups during high-frequency Netty event loop matching.
 */
class TrafficModifierManager {

    private val _modifierRules = MutableStateFlow<List<ModifierRule>>(emptyList())
    val modifierRulesStream: StateFlow<List<ModifierRule>> = _modifierRules.asStateFlow()

    private val _mapLocalRules = MutableStateFlow<List<MapLocalRule>>(emptyList())
    val mapLocalRulesStream: StateFlow<List<MapLocalRule>> = _mapLocalRules.asStateFlow()

    private val _mapRemoteRules = MutableStateFlow<List<MapRemoteRule>>(emptyList())
    val mapRemoteRulesStream: StateFlow<List<MapRemoteRule>> = _mapRemoteRules.asStateFlow()

    /**
     * Returns an immutable read-only snapshot of active modifier rules pre-sorted by priority.
     */
    fun getModifierRules(): List<ModifierRule> = _modifierRules.value

    /**
     * Returns an immutable read-only snapshot of active Map Local rules pre-sorted by priority.
     */
    fun getMapLocalRules(): List<MapLocalRule> = _mapLocalRules.value

    /**
     * Returns an immutable read-only snapshot of active Map Remote rules pre-sorted by priority.
     */
    fun getMapRemoteRules(): List<MapRemoteRule> = _mapRemoteRules.value

    /**
     * Adds a new [ModifierRule] to the active rule set after validating.
     *
     * @param rule The rule to add.
     * @throws IllegalArgumentException if the rule properties or regex pattern are invalid.
     */
    fun addModifierRule(rule: ModifierRule) {
        validateRule(rule.id, rule.urlPattern)
        _modifierRules.update { current ->
            (current.filterNot { it.id == rule.id } + rule).sortedBy { it.priority }
        }
        KNetLogger.debug(TAG) { "Added modifier rule [${rule.id}]: ${rule.name}" }
    }

    /**
     * Adds a new [MapLocalRule] to the active rule set after validating.
     *
     * @param rule The rule to add.
     * @throws IllegalArgumentException if the rule properties or regex pattern are invalid.
     */
    fun addMapLocalRule(rule: MapLocalRule) {
        validateRule(rule.id, rule.urlPattern)
        if (rule.localFilePath.isBlank()) throw IllegalArgumentException("Local file path cannot be blank")
        _mapLocalRules.update { current ->
            (current.filterNot { it.id == rule.id } + rule).sortedBy { it.priority }
        }
        KNetLogger.debug(TAG) { "Added map local rule [${rule.id}]: ${rule.name} -> ${rule.localFilePath}" }
    }

    /**
     * Adds a new [MapRemoteRule] to the active rule set after validating.
     *
     * @param rule The rule to add.
     * @throws IllegalArgumentException if the rule properties, regex pattern, or ports are invalid.
     */
    fun addMapRemoteRule(rule: MapRemoteRule) {
        validateRule(rule.id, rule.urlPattern)
        if (rule.targetHost.isBlank()) throw IllegalArgumentException("Target host cannot be blank")
        if (rule.targetPort !in 1..65535) throw IllegalArgumentException("Invalid target port: ${rule.targetPort}")
        _mapRemoteRules.update { current ->
            (current.filterNot { it.id == rule.id } + rule).sortedBy { it.priority }
        }
        KNetLogger.debug(TAG) { "Added map remote rule [${rule.id}]: ${rule.name} -> ${rule.targetHost}:${rule.targetPort}" }
    }

    /**
     * Removes an existing modifier rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeModifierRule(id: String) {
        _modifierRules.update { current -> current.filterNot { it.id == id } }
    }

    /**
     * Removes an existing map local rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeMapLocalRule(id: String) {
        _mapLocalRules.update { current -> current.filterNot { it.id == id } }
    }

    /**
     * Removes an existing map remote rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeMapRemoteRule(id: String) {
        _mapRemoteRules.update { current -> current.filterNot { it.id == id } }
    }

    /**
     * Clears all active rules across all rule types.
     */
    fun clearAllRules() {
        _modifierRules.value = emptyList()
        _mapLocalRules.value = emptyList()
        _mapRemoteRules.value = emptyList()
        KNetLogger.debug(TAG) { "All traffic modifier rules cleared" }
    }

    private fun validateRule(id: String, urlPattern: String) {
        if (id.isBlank()) throw IllegalArgumentException("Rule ID cannot be blank")
        if (urlPattern.isBlank()) throw IllegalArgumentException("URL pattern cannot be blank")
        if (RegexCache.getOrNull(urlPattern) == null) {
            throw IllegalArgumentException("Invalid URL regex pattern: $urlPattern")
        }
    }
}
