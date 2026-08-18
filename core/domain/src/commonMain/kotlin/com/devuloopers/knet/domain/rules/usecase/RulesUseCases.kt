package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain UseCase that streams active rules.
 */
class ObserveRulesUseCase(
    private val repository: RulesRepository
) {
    operator fun invoke(): Flow<List<BreakpointRule>> {
        return repository.rulesFlow
    }

    fun execute(): Flow<List<BreakpointRule>> {
        return repository.rulesFlow
    }
}

/**
 * Domain UseCase that streams global proxy interception engine state.
 */
class ObserveGlobalInterceptionUseCase(
    private val repository: RulesRepository
) {
    operator fun invoke(): Flow<Boolean> {
        return repository.isGlobalInterceptionEnabled
    }
}

/**
 * Domain UseCase that toggles global proxy interception engine state.
 */
class ToggleGlobalInterceptionUseCase(
    private val repository: RulesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.toggleGlobalInterception(enabled)
    }

    suspend fun execute(enabled: Boolean) {
        repository.toggleGlobalInterception(enabled)
    }
}

/**
 * Domain UseCase that deletes a rule by ID.
 */
class DeleteRuleUseCase(
    private val repository: RulesRepository
) {
    suspend operator fun invoke(ruleId: String) {
        repository.deleteRule(ruleId)
    }

    suspend fun execute(ruleId: String) {
        repository.deleteRule(ruleId)
    }
}
