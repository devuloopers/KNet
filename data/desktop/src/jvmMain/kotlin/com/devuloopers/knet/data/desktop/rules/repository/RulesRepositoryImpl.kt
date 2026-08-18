package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.traffic.model.http.HttpMethod
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
 * as the Single Source of Truth (SSOT), reactively synchronizing compiled application rules.
 *
 * All mutation operations ([saveRule], [deleteRule], [toggleRule]) write strictly to SQLite via [BreakpointRuleDao].
 * Reactive Flow emissions from Room DB automatically update both the UI state and Netty's live proxy engine.
 */
class RulesRepositoryImpl(
    private val breakpointRuleDao: BreakpointRuleDao,
    private val breakpointControl: BreakpointControlPort,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : RulesRepository {

    init {
        // Eagerly project Room rows into one immutable application-owned compiled snapshot.
        breakpointRuleDao.observeAllRules()
            .onEach { entities ->
                breakpointControl.replaceRules(entities.map { it.toDomainRule() })
            }
            .launchIn(coroutineScope)
    }

    override val rulesFlow: Flow<List<BreakpointRule>> = breakpointRuleDao.observeAllRules()
        .map { entities -> entities.map { it.toDomainRule() } }

    override val isGlobalInterceptionEnabled: Flow<Boolean> = breakpointControl.isEnabled

    override suspend fun toggleGlobalInterception(enabled: Boolean) {
        breakpointControl.setEnabled(enabled)
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        breakpointRuleDao.toggleRule(ruleId, enabled)
    }

    override suspend fun saveRule(rule: BreakpointRule) {
        breakpointRuleDao.upsertRule(rule.toStorageEntity())
    }

    override suspend fun deleteRule(ruleId: String) {
        breakpointRuleDao.deleteRule(ruleId)
    }

    private fun BreakpointRuleEntity.toDomainRule(): BreakpointRule {
        val criteria = when (protocolCriteriaType.uppercase()) {
            "GRAPHQL" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.GraphQL(operationName = protocolCriteriaData)
            "GRPC" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.Grpc()
            "WEBSOCKET" -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.WebSocket()
            else -> com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault
        }

        return BreakpointRule(
            id = id,
            name = urlPattern,
            phase = BreakpointPhase.fromString(phase),
            urlPattern = urlPattern,
            method = method?.takeIf(String::isNotBlank)?.let(HttpMethod::fromToken),
            enabled = enabled,
            protocolCriteria = criteria
        )
    }

    private fun BreakpointRule.toStorageEntity(): BreakpointRuleEntity {
        val (criteriaType, criteriaData) = when (val c = protocolCriteria) {
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.GraphQL -> "GRAPHQL" to (c.operationName ?: "")
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.Grpc -> "GRPC" to ""
            is com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.WebSocket -> "WEBSOCKET" to ""
            com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria.HttpDefault -> "HTTP" to ""
        }
        return BreakpointRuleEntity(
            id = id,
            urlPattern = urlPattern,
            method = method?.token,
            phase = phase.name,
            enabled = enabled,
            protocolCriteriaType = criteriaType,
            protocolCriteriaData = criteriaData
        )
    }

}
