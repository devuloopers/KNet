package com.devuloopers.knet.modifier

import com.devuloopers.knet.logger.KNetLogger
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "TrafficModifierManager"

/**
 * Thread-safe registry that maintains active [ModifierRule], [MapLocalRule], and [MapRemoteRule] sets.
 *
 * All underlying rule collections use [CopyOnWriteArrayList] to ensure concurrent read safety
 * without requiring explicit locking during Netty event loop rule lookups.
 */
class TrafficModifierManager {

    /**
     * The list of active header/body/query modifier rules.
     */
    val modifierRules: MutableList<ModifierRule> = CopyOnWriteArrayList()

    /**
     * The list of active Map Local rules that short-circuit requests with local file responses.
     */
    val mapLocalRules: MutableList<MapLocalRule> = CopyOnWriteArrayList()

    /**
     * The list of active Map Remote rules that redirect requests to alternate hosts.
     */
    val mapRemoteRules: MutableList<MapRemoteRule> = CopyOnWriteArrayList()

    /**
     * Adds a new [ModifierRule] to the active rule set.
     *
     * @param rule The rule to add.
     */
    fun addModifierRule(rule: ModifierRule) {
        modifierRules.add(rule)
        KNetLogger.debug(TAG) { "Added modifier rule [${rule.id}]: ${rule.name}" }
    }

    /**
     * Adds a new [MapLocalRule] to the active rule set.
     *
     * @param rule The rule to add.
     */
    fun addMapLocalRule(rule: MapLocalRule) {
        mapLocalRules.add(rule)
        KNetLogger.debug(TAG) { "Added map local rule [${rule.id}]: ${rule.name} -> ${rule.localFilePath}" }
    }

    /**
     * Adds a new [MapRemoteRule] to the active rule set.
     *
     * @param rule The rule to add.
     */
    fun addMapRemoteRule(rule: MapRemoteRule) {
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
}
