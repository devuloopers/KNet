package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointPortCriteria
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.application.contract.breakpoint.BreakpointControl
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.storage.rules.dao.BreakpointRuleDao
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
    private val breakpointControl: BreakpointControl,
    private val coroutineScope: CoroutineScope,
) : RulesRepository, AutoCloseable {

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

    /** Stops Room-to-runtime rule synchronization for this repository instance. */
    override fun close() {
        coroutineScope.cancel()
    }

    private fun BreakpointRuleEntity.toDomainRule(): BreakpointRule {
        val protocolId = runCatching {
            BreakpointProtocolId(protocolCriteriaType.trim().lowercase())
        }.getOrElse { BreakpointProtocolId("invalid") }
        val criteria = ProtocolMatchCriteria(
            protocolId = protocolId,
            encodedPayload = protocolCriteriaData.orEmpty(),
        )

        return BreakpointRule(
            id = id,
            name = urlPattern,
            phase = BreakpointPhase.fromString(phase),
            urlPattern = urlPattern,
            portCriteria = port?.let(BreakpointPortCriteria::Exact) ?: BreakpointPortCriteria.Any,
            method = method?.takeIf(String::isNotBlank)?.let(HttpMethod::fromToken),
            enabled = enabled,
            priority = priority,
            protocolCriteria = criteria,
        )
    }

    private fun BreakpointRule.toStorageEntity(): BreakpointRuleEntity {
        return BreakpointRuleEntity(
            id = id,
            urlPattern = urlPattern,
            port = (portCriteria as? BreakpointPortCriteria.Exact)?.value,
            method = method?.token,
            phase = phase.name,
            enabled = enabled,
            priority = priority,
            protocolCriteriaType = protocolCriteria.protocolId.value,
            protocolCriteriaData = protocolCriteria.encodedPayload,
        )
    }

}
