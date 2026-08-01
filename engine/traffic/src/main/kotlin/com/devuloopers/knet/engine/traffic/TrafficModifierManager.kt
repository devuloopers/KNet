package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "TrafficModifierManager"

/**
 * Thread-safe registry that maintains active [ModifierRule], [MapLocalRule], and [MapRemoteRule] sets.
 *
 * All underlying rule collections use [CopyOnWriteArrayList] to ensure concurrent read safety
 * without requiring explicit locking during Netty event loop rule lookups.
 */
class TrafficModifierManager {

    private val modifierRules: MutableList<ModifierRule> = CopyOnWriteArrayList()
    private val mapLocalRules: MutableList<MapLocalRule> = CopyOnWriteArrayList()
    private val mapRemoteRules: MutableList<MapRemoteRule> = CopyOnWriteArrayList()

    /**
     * Returns an immutable read-only snapshot of active modifier rules sorted by priority.
     */
    fun getModifierRules(): List<ModifierRule> = modifierRules.sortedBy { it.priority }

    /**
     * Returns an immutable read-only snapshot of active Map Local rules sorted by priority.
     */
    fun getMapLocalRules(): List<MapLocalRule> = mapLocalRules.sortedBy { it.priority }

    /**
     * Returns an immutable read-only snapshot of active Map Remote rules sorted by priority.
     */
    fun getMapRemoteRules(): List<MapRemoteRule> = mapRemoteRules.sortedBy { it.priority }

    /**
     * Adds a new [ModifierRule] to the active rule set after validating.
     *
     * @param rule The rule to add.
     * @throws IllegalArgumentException if the rule properties or regex pattern are invalid.
     */
    fun addModifierRule(rule: ModifierRule) {
        validateRule(rule.id, rule.urlPattern)
        modifierRules.add(rule)
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
        mapLocalRules.add(rule)
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
        mapRemoteRules.add(rule)
        KNetLogger.debug(TAG) { "Added map remote rule [${rule.id}]: ${rule.name} -> ${rule.targetHost}:${rule.targetPort}" }
    }

    /**
     * Removes an existing modifier rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeModifierRule(id: String) {
        modifierRules.removeAll { it.id == id }
    }

    /**
     * Removes an existing map local rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeMapLocalRule(id: String) {
        mapLocalRules.removeAll { it.id == id }
    }

    /**
     * Removes an existing map remote rule by its ID.
     *
     * @param id The rule ID to remove.
     */
    fun removeMapRemoteRule(id: String) {
        mapRemoteRules.removeAll { it.id == id }
    }

    /**
     * Clears all active rules across all rule types.
     */
    fun clearAllRules() {
        modifierRules.clear()
        mapLocalRules.clear()
        mapRemoteRules.clear()
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
