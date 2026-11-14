package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.policy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Domain UseCase that streams active rules list from [RulesRepository].
 *
 * @property repository Feature repository contract for rules.
 */
class GetRulesUseCase(
    private val repository: RulesRepository
) {
    /** Observes definitions and per-device activation from one committed snapshot. */
    fun catalog(workspace: PolicyWorkspace): Flow<PolicyRuleCatalog<BreakpointRule>> = repository.forWorkspace(workspace).observeCatalog()

    /**
     * Streams domain rule models.
     */
    operator fun invoke(): Flow<List<BreakpointRule>> {
        return repository.rulesFlow
    }

    /**
     * Executes rules stream lookup formatted as [RulesUiState].
     */
    fun execute(): Flow<RulesUiState> {
        return repository.rulesFlow.map { rules ->
            RulesUiState.Success(rules = rules)
        }.flowOn(Dispatchers.Default)
    }
}
