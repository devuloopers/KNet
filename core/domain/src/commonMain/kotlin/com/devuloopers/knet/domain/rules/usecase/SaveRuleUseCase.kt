package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.RuleModel
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
    suspend fun execute(rule: RuleModel) {
        repository.saveRule(rule)
    }

    suspend operator fun invoke(rule: RuleModel) {
        execute(rule)
    }
}
