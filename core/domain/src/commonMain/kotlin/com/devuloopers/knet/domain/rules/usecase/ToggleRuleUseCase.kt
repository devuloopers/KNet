package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.repository.RulesRepository

/**
 * Domain UseCase that toggles a rule's enabled state.
 *
 * @property repository Feature repository contract for rules.
 */
class ToggleRuleUseCase(
    private val repository: RulesRepository
) {
    /**
     * Toggles a rule enabled / disabled state by ID.
     */
    suspend fun execute(ruleId: String, enabled: Boolean) {
        repository.toggleRule(ruleId, enabled)
    }

    suspend operator fun invoke(ruleId: String, enabled: Boolean) {
        execute(ruleId, enabled)
    }
}
