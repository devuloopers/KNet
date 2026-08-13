package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.domain.rules.repository.RulesRepository
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
    /**
     * Streams domain rule models.
     */
    operator fun invoke(): Flow<List<RuleModel>> {
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
