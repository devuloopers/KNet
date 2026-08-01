package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop implementation of [RulesRepository].
 */
class RulesRepositoryImpl : RulesRepository {

    private val _rulesFlow = MutableStateFlow<List<RuleModel>>(emptyList())
    override val rulesFlow: Flow<List<RuleModel>> = _rulesFlow.asStateFlow()

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        _rulesFlow.value = _rulesFlow.value.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
    }

    override suspend fun saveRule(rule: RuleModel) {
        val current = _rulesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        _rulesFlow.value = current
    }

    override suspend fun deleteRule(ruleId: String) {
        _rulesFlow.value = _rulesFlow.value.filterNot { it.id == ruleId }
    }
}
