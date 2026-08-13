package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain UseCase that streams global proxy interception engine state.
 */
public class ObserveGlobalInterceptionUseCase(
    private val repository: RulesRepository
) {
    public operator fun invoke(): Flow<Boolean> {
        return repository.isGlobalInterceptionEnabled
    }
}

/**
 * Domain UseCase that toggles global proxy interception engine state.
 */
public class ToggleGlobalInterceptionUseCase(
    private val repository: RulesRepository
) {
    public suspend operator fun invoke(enabled: Boolean) {
        repository.toggleGlobalInterception(enabled)
    }

    public suspend fun execute(enabled: Boolean) {
        repository.toggleGlobalInterception(enabled)
    }
}

/**
 * Domain UseCase that deletes a rule by ID.
 */
public class DeleteRuleUseCase(
    private val repository: RulesRepository
) {
    public suspend operator fun invoke(ruleId: String) {
        repository.deleteRule(ruleId)
    }

    public suspend fun execute(ruleId: String) {
        repository.deleteRule(ruleId)
    }
}
