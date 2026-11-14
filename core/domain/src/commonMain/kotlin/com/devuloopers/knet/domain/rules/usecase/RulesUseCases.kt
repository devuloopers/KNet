package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import com.devuloopers.knet.domain.policy.*

/**
 * Domain UseCase that streams active rules.
 */
class ObserveRulesUseCase(
    private val repository: RulesRepository
) {
    operator fun invoke(workspace: PolicyWorkspace = PolicyWorkspace.Other): Flow<List<BreakpointRule>> {
        return repository.forWorkspace(workspace).rulesFlow
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
    operator fun invoke(workspace: PolicyWorkspace = PolicyWorkspace.Other): Flow<Boolean> {
        return repository.forWorkspace(workspace).isGlobalInterceptionEnabled
    }
}

/**
 * Domain UseCase that toggles global proxy interception engine state.
 */
class ToggleGlobalInterceptionUseCase(
    private val repository: RulesRepository
) {
    suspend operator fun invoke(enabled: Boolean, workspace: PolicyWorkspace = PolicyWorkspace.Other) {
        repository.forWorkspace(workspace).toggleGlobalInterception(enabled)
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
    /** Deletes the captured definition after the editor has shown its usage impact. */
    suspend fun execute(ruleId: String, context: RuleEditContext, acknowledgeSharedUsage: Boolean = false) {
        repository.deleteDefinition(ruleId, context, acknowledgeSharedUsage)
    }

    suspend operator fun invoke(ruleId: String) {
        repository.deleteRule(ruleId)
    }

    suspend fun execute(ruleId: String) {
        repository.deleteRule(ruleId)
    }
}
