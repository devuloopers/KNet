package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.storage.protectedtraffic.dao.ProtectedTrafficDao
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficRuleEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed protected-traffic policy with validated, fail-safe snapshot publication.
 * @param dao Reads and writes persisted policy without adding a runtime service catalogue.
 * @param scope Product-owned observer lifetime; cancel and join it before storage closes.
 */
class RoomProtectedTrafficRepository(
    private val dao: ProtectedTrafficDao,
    scope: CoroutineScope,
) : ProtectedTrafficRepository {
    private val mutation = Mutex()
    private val fallback = ProtectedTrafficConfiguration(configurationReady = false)

    override val configuration: StateFlow<ProtectedTrafficConfiguration> = combine(
        dao.observeSettings(),
        dao.observeRules(),
    ) { settings, rules ->
        runCatching {
            ProtectedTrafficConfiguration(
                defaultAction = settings?.defaultAction?.decodeAction() ?: ProtectedTrafficAction.INSPECT,
                rules = rules.map(ProtectedTrafficRuleEntity::toDomain),
                automaticTlsFallbackEnabled = settings?.automaticTlsFallbackEnabled ?: true,
            )
        }.getOrElse { fallback }
    }.stateIn(scope, SharingStarted.Eagerly, fallback)

    override suspend fun setDefaultAction(action: ProtectedTrafficAction, automaticFallbackEnabled: Boolean) = mutation.withLock {
        val existing = dao.getSettings() ?: ProtectedTrafficSettingsEntity(defaultAction = ProtectedTrafficAction.INSPECT.name)
        dao.upsertSettings(existing.copy(defaultAction = action.name, automaticTlsFallbackEnabled = automaticFallbackEnabled))
    }

    override suspend fun upsertRule(rule: ProtectedTrafficRule) = mutation.withLock {
        val existing = dao.getRules().map(ProtectedTrafficRuleEntity::toDomain)
        require(rule.origin == ProtectedTrafficRuleOrigin.USER ||
            rule.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET && existing.any {
                it.id == rule.id && it.origin == rule.origin && it.selectorKey == rule.selectorKey && it.action == rule.action
            }) { "Only explicit user rules or existing imported exceptions may be saved." }
        ProtectedTrafficConfiguration(
            rules = existing.filterNot { it.id == rule.id } + rule,
        )
        dao.upsertRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) = mutation.withLock {
        dao.deleteRule(ruleId.value)
    }
}

private fun ProtectedTrafficRule.toEntity(): ProtectedTrafficRuleEntity {
    val destinationKind: String?
    val destinationValue: String?
    val destinationPrefixLength: Int?
    val destinationIpFamily: String?
    when (val selector = destination) {
        is ProtectedDestinationSelector.Exact -> {
            destinationKind = if (selector.isIpLiteral) "exact-ip" else "exact-host"
            destinationValue = selector.value
            destinationPrefixLength = null
            destinationIpFamily = null
        }
        is ProtectedDestinationSelector.WildcardDomain -> {
            destinationKind = "wildcard-host"
            destinationValue = selector.suffix
            destinationPrefixLength = null
            destinationIpFamily = null
        }
        is ProtectedDestinationSelector.Cidr -> {
            destinationKind = "cidr"
            destinationValue = selector.networkAddress
            destinationPrefixLength = selector.prefixLength
            destinationIpFamily = selector.family.name
        }
        null -> {
            destinationKind = null
            destinationValue = null
            destinationPrefixLength = null
            destinationIpFamily = null
        }
    }
    return ProtectedTrafficRuleEntity(
        id = id.value,
        enabled = enabled,
        action = action.name,
        sourceApplication = sourceApplication?.value,
        destinationKind = destinationKind,
        destinationValue = destinationValue,
        destinationPrefixLength = destinationPrefixLength,
        destinationIpFamily = destinationIpFamily,
        port = port,
        transport = transport?.name,
        ipFamily = ipFamily?.name,
        origin = origin.name,
    )
}

private fun ProtectedTrafficRuleEntity.toDomain(): ProtectedTrafficRule = ProtectedTrafficRule(
    id = ProtectedTrafficRuleId(id),
    enabled = enabled,
    action = action.decodeAction(),
    sourceApplication = sourceApplication?.let(::ProtectedSourceApplicationId),
    destination = decodeDestination(),
    port = port,
    transport = transport?.let(ProtectedTransportProtocol::valueOf),
    ipFamily = ipFamily?.let(ProtectedIpFamily::valueOf),
    origin = ProtectedTrafficRuleOrigin.valueOf(origin),
)

private fun ProtectedTrafficRuleEntity.decodeDestination(): ProtectedDestinationSelector? = when (destinationKind) {
    null -> null
    "exact-host" -> ProtectedDestinationSelector.Exact(checkNotNull(destinationValue), isIpLiteral = false)
    "exact-ip" -> ProtectedDestinationSelector.Exact(checkNotNull(destinationValue), isIpLiteral = true)
    "wildcard-host" -> ProtectedDestinationSelector.WildcardDomain(checkNotNull(destinationValue))
    "cidr" -> ProtectedDestinationSelector.Cidr(
        networkAddress = checkNotNull(destinationValue),
        prefixLength = checkNotNull(destinationPrefixLength),
        family = ProtectedIpFamily.valueOf(checkNotNull(destinationIpFamily)),
    )
    else -> error("Unknown protected destination kind.")
}

private fun String.decodeAction(): ProtectedTrafficAction = ProtectedTrafficAction.valueOf(this)
