package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedServiceGroupId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedSourceApplicationId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficBuiltIns
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleOrigin
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol
import com.devuloopers.knet.storage.protectedtraffic.dao.ProtectedTrafficDao
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedServiceGroupEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficRuleEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed protected-traffic repository with validated, fail-safe snapshot publication. */
class RoomProtectedTrafficRepository(
    private val dao: ProtectedTrafficDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ProtectedTrafficRepository {
    private val mutation = Mutex()
    private val fallback = ProtectedTrafficConfiguration(rules = ProtectedTrafficBuiltIns.rules)

    override val configuration: StateFlow<ProtectedTrafficConfiguration> = combine(
        dao.observeSettings(),
        dao.observeRules(),
        dao.observeGroups(),
    ) { settings, rules, groups ->
        runCatching {
            ProtectedTrafficConfiguration(
                defaultAction = settings?.defaultAction?.decodeAction() ?: ProtectedTrafficAction.INSPECT,
                rules = ProtectedTrafficBuiltIns.rules + rules.map(ProtectedTrafficRuleEntity::toDomain),
                enabledBuiltInGroups = groups.filter { it.enabled }.mapTo(mutableSetOf()) {
                    ProtectedServiceGroupId(it.groupId)
                },
            )
        }.getOrElse { fallback }
    }.stateIn(scope, SharingStarted.Eagerly, fallback)

    override suspend fun setDefaultAction(action: ProtectedTrafficAction) = mutation.withLock {
        dao.upsertSettings(ProtectedTrafficSettingsEntity(defaultAction = action.name))
    }

    override suspend fun upsertRule(rule: ProtectedTrafficRule) = mutation.withLock {
        require(rule.origin == ProtectedTrafficRuleOrigin.USER) { "Built-in protected-traffic rules are immutable." }
        val existing = dao.getRules().map(ProtectedTrafficRuleEntity::toDomain)
        ProtectedTrafficConfiguration(
            rules = ProtectedTrafficBuiltIns.rules + existing.filterNot { it.id == rule.id } + rule,
            enabledBuiltInGroups = configuration.value.enabledBuiltInGroups,
        )
        dao.upsertRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: ProtectedTrafficRuleId) = mutation.withLock {
        require(ProtectedTrafficBuiltIns.rules.none { it.id == ruleId }) { "Built-in rules cannot be deleted." }
        dao.deleteRule(ruleId.value)
    }

    override suspend fun setBuiltInGroupEnabled(groupId: ProtectedServiceGroupId, enabled: Boolean) =
        mutation.withLock {
            require(ProtectedTrafficBuiltIns.rules.any { it.groupId == groupId }) {
                "Unknown protected-service compatibility group."
            }
            dao.upsertGroup(ProtectedServiceGroupEntity(groupId.value, enabled))
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
