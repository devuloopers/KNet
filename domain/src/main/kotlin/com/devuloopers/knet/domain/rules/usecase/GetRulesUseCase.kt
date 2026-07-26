package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Domain UseCase that streams active rules formatted as [RulesUiState].
 *
 * @property repository Feature repository contract for rules.
 */
class GetRulesUseCase(
    private val repository: RulesRepository
) {
    /**
     * Executes rules stream lookup.
     *
     * @param activeTab Currently selected tab.
     * @return Cold Flow emitting [RulesUiState].
     */
    fun execute(activeTab: String = "Rules"): Flow<RulesUiState> {
        return repository.rulesFlow.map { rules ->
            RulesUiState.Success(rules = rules, activeTab = activeTab)
        }.flowOn(Dispatchers.Default)
    }
}
