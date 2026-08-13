package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.engine.interceptor.BreakpointPhase
import com.devuloopers.knet.engine.interceptor.BreakpointRule
import com.devuloopers.knet.engine.interceptor.BreakpointRuleRegistry
import com.devuloopers.knet.storage.rules.dao.BreakpointRuleDao
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Desktop implementation of [RulesRepository] enforcing Room [BreakpointRuleDao] SQLite database
 * as the Single Source of Truth (SSOT), reactively synchronizing rules to [BreakpointRuleRegistry].
 *
 * All mutation operations ([saveRule], [deleteRule], [toggleRule]) write strictly to SQLite via [BreakpointRuleDao].
 * Reactive Flow emissions from Room DB automatically update both the UI state and Netty's live proxy engine.
 */
public class RulesRepositoryImpl(
    private val breakpointRuleDao: BreakpointRuleDao,
    private val registry: BreakpointRuleRegistry = BreakpointRuleRegistry
) : RulesRepository {

    override val rulesFlow: Flow<List<RuleModel>> = breakpointRuleDao.observeAllRules()
        .onEach { entities ->
            // Single Source of Truth: DB emissions update Netty proxy interception engine
            val engineRules = entities.map { it.toEngineBreakpointRule() }
            registry.clearRules()
            engineRules.forEach { registry.addRule(it) }
        }
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
        return RuleModel(
            id = id,
            name = urlPattern.ifBlank { "All Traffic" },
            type = phase,
            condition = urlPattern.ifBlank { ".*" },
            action = method ?: "ALL",
            enabled = enabled
        )
    }

    private fun BreakpointRuleEntity.toEngineBreakpointRule(): BreakpointRule {
        val phaseEnum = runCatching { BreakpointPhase.valueOf(phase) }.getOrDefault(BreakpointPhase.BOTH)
        val methodValue = if (method.equals("ALL", ignoreCase = true)) null else method
        return BreakpointRule(
            id = id,
            urlPattern = urlPattern.ifBlank { ".*" },
            method = methodValue,
            phase = phaseEnum,
            enabled = enabled,
            priority = priority
        )
    }

    private fun RuleModel.toStorageEntity(): BreakpointRuleEntity {
        return BreakpointRuleEntity(
            id = id,
            urlPattern = condition.ifBlank { ".*" },
            method = if (action.equals("ALL", ignoreCase = true)) null else action,
            phase = type,
            enabled = enabled
        )
    }
}
