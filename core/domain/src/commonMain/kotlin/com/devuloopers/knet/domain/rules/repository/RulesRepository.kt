package com.devuloopers.knet.domain.rules.repository

import com.devuloopers.knet.domain.rules.model.RuleModel
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for the Rules engine.
 */
interface RulesRepository {
    /**
     * Cold stream emitting persistent active interceptor rules.
     */
    val rulesFlow: Flow<List<RuleModel>>

    /**
     * Toggles rule enabled/disabled state by ID.
     */
    suspend fun toggleRule(ruleId: String, enabled: Boolean)

    /**
     * Saves or updates a rule entity.
     */
    suspend fun saveRule(rule: RuleModel)

    /**
     * Deletes a rule entity by ID.
     */
    suspend fun deleteRule(ruleId: String)
}
