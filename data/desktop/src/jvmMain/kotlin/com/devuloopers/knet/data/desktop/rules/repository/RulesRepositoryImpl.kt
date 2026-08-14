package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.engine.interceptor.BreakpointRuleRegistry
import com.devuloopers.knet.storage.rules.dao.BreakpointRuleDao
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Desktop implementation of [RulesRepository] enforcing Room [BreakpointRuleDao] SQLite database
 * as the Single Source of Truth (SSOT), reactively synchronizing rules to [BreakpointRuleRegistry].
 *
 * All mutation operations ([saveRule], [deleteRule], [toggleRule]) write strictly to SQLite via [BreakpointRuleDao].
 * Reactive Flow emissions from Room DB automatically update both the UI state and Netty's live proxy engine.
 */
class RulesRepositoryImpl(
    private val breakpointRuleDao: BreakpointRuleDao,
    private val registry: BreakpointRuleRegistry = BreakpointRuleRegistry,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : RulesRepository {

    init {
        // Eagerly observe Room DB rules on app startup so Netty's BreakpointRuleRegistry is ALWAYS populated
        breakpointRuleDao.observeAllRules()
            .onEach { entities ->
                val domainRules = entities.map { it.toDomainRuleModel() }
                registry.clearRules()
                domainRules.forEach { registry.addRule(it) }
            }
            .launchIn(coroutineScope)
    }

    override val rulesFlow: Flow<List<RuleModel>> = breakpointRuleDao.observeAllRules()
        .map { entities -> entities.map { it.toDomainRuleModel() } }

    override val isGlobalInterceptionEnabled: Flow<Boolean> = registry.isGlobalInterceptionEnabled

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        registry.toggleGlobalInterception(enabled)
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        breakpointRuleDao.toggleRule(ruleId, enabled)
    }

    override suspend fun saveRule(rule: RuleModel) {
        breakpointRuleDao.upsertRule(rule.toStorageEntity())
    }

    override suspend fun deleteRule(ruleId: String) {
        breakpointRuleDao.deleteRule(ruleId)
    }

    private fun BreakpointRuleEntity.toDomainRuleModel(): RuleModel {
        val criteria = when (protocolCriteriaType.uppercase()) {
            "GRAPHQL" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.GraphQL(operationName = protocolCriteriaData)
            "GRPC" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.Grpc()
            "WEBSOCKET" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.WebSocket()
            else -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault
        }

        return RuleModel(
            id = id,
            name = urlPattern,
            type = BreakpointPhase.fromString(phase),
            condition = urlPattern,
            action = method ?: "ALL",
            enabled = enabled,
            protocolCriteria = criteria
        )
    }

    private fun RuleModel.toStorageEntity(): BreakpointRuleEntity {
        val (criteriaType, criteriaData) = when (val c = protocolCriteria) {
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.GraphQL -> "GRAPHQL" to (c.operationName ?: "")
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.Grpc -> "GRPC" to ""
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.WebSocket -> "WEBSOCKET" to ""
            com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault -> "HTTP" to ""
        }
        return BreakpointRuleEntity(
            id = id,
            urlPattern = condition,
            method = if (action.equals("ALL", ignoreCase = true)) null else action,
            phase = type.name,
            enabled = enabled,
            protocolCriteriaType = criteriaType,
            protocolCriteriaData = criteriaData
        )
    }
}
