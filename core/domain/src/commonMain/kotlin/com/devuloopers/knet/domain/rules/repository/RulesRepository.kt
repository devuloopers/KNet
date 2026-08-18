package com.devuloopers.knet.domain.rules.repository

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for the Rules engine.
 */
interface RulesRepository {

    /**
     * Reactive stream emitting persistent active interceptor rules.
     */
    val rulesFlow: Flow<List<BreakpointRule>>

    /**
     * Reactive stream emitting global proxy interception engine status.
     */
    val isGlobalInterceptionEnabled: Flow<Boolean>

    /**
     * Toggles global proxy interception engine state.
     */
    suspend fun toggleGlobalInterception(enabled: Boolean)

    /**
     * Toggles rule enabled/disabled state by ID.
     */
    suspend fun toggleRule(ruleId: String, enabled: Boolean)

    /**
     * Saves or updates a rule entity.
     */
    suspend fun saveRule(rule: BreakpointRule)

    /**
     * Deletes a rule entity by ID.
     */
    suspend fun deleteRule(ruleId: String)
}
