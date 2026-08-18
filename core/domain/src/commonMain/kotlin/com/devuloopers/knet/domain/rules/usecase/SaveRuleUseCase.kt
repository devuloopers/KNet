package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository

/**
 * Domain UseCase that creates or updates an interceptor rule.
 *
 * @property repository Feature repository contract for rules.
 */
class SaveRuleUseCase(
    private val repository: RulesRepository
) {
    /**
     * Persists or updates a rule entity.
     */
    suspend fun execute(rule: BreakpointRule) {
        repository.saveRule(rule)
    }

    suspend operator fun invoke(rule: BreakpointRule) {
        execute(rule)
    }
}
