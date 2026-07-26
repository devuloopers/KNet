package com.devuloopers.knet.data.rules.repository

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Production implementation of [RulesRepository] maintaining active rules configuration.
 */
class RulesRepositoryImpl : RulesRepository {

    private val _rules = MutableStateFlow(
        listOf(
            RuleModel(
                id = "1",
                name = "Pause on /login",
                type = "Request",
                condition = "Path contains \"/login\"",
                action = "Breakpoint",
                enabled = true,
                hitCount = 12,
                lastHit = "10:15:30"
            ),
            RuleModel(
                id = "2",
                name = "Modify User-Agent",
                type = "Request",
                condition = "Host contains \"api.example.com\"",
                action = "Rewrite Header",
                enabled = true,
                hitCount = 34,
                lastHit = "10:14:58"
            ),
            RuleModel(
                id = "3",
                name = "Block Ads",
                type = "Request",
                condition = "Host contains \"doubleclick.net\"",
                action = "Drop",
                enabled = true,
                hitCount = 66,
                lastHit = "10:15:29"
            )
        )
    )

    override val rulesFlow: Flow<List<RuleModel>> = _rules.asStateFlow()

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        _rules.update { list ->
            list.map { if (it.id == ruleId) it.copy(enabled = enabled) else it }
        }
    }

    override suspend fun saveRule(rule: RuleModel) {
        _rules.update { list ->
            val existing = list.find { it.id == rule.id }
            if (existing != null) {
                list.map { if (it.id == rule.id) rule else it }
            } else {
                list + rule.copy(id = (list.size + 1).toString())
            }
        }
    }

    override suspend fun deleteRule(ruleId: String) {
        _rules.update { list ->
            list.filterNot { it.id == ruleId }
        }
    }
}
