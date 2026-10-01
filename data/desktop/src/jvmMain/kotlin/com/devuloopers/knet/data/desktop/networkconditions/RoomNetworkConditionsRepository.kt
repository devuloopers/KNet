package com.devuloopers.knet.data.desktop.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.storage.networkconditions.dao.NetworkConditionDao
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionProfileEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed, fail-closed Network Conditions source with atomic validated publication. */
class RoomNetworkConditionsRepository(
    private val dao: NetworkConditionDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : NetworkConditionsRepository {
    private val mutation = Mutex()

    override val configuration: StateFlow<NetworkConditionConfiguration> = combine(
        dao.observeSettings(),
        dao.observeProfiles(),
        dao.observeRules(),
    ) { settings, profiles, rules ->
        runCatching {
            NetworkConditionConfiguration(
                enabled = settings?.enabled ?: false,
                globalProfileId = settings?.globalProfileId?.let(::NetworkConditionProfileId),
                customProfiles = profiles.map(NetworkConditionProfileEntity::toDomain),
                rules = rules.map(NetworkConditionRuleEntity::toDomain),
                lastQuickAddProfileId = settings?.lastQuickAddProfileId?.let(::NetworkConditionProfileId),
            )
        }.getOrElse { NetworkConditionConfiguration() }
    }.stateIn(scope, SharingStarted.Eagerly, NetworkConditionConfiguration())

    override suspend fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }

    override suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?) = mutation.withLock {
        require(profileId == null || persistentConfiguration().profile(profileId) != null) { "Unknown global profile." }
        dao.upsertSettings(settings().copy(globalProfileId = profileId?.value))
    }

    override suspend fun upsertProfile(profile: NetworkConditionProfile) = mutation.withLock {
        require(!profile.builtIn) { "Built-in profiles cannot be overwritten." }
        val state = persistentConfiguration()
        val candidate = state.customProfiles.filterNot { it.id == profile.id } + profile
        NetworkConditionConfiguration(
            enabled = state.enabled,
            globalProfileId = state.globalProfileId,
            customProfiles = candidate,
            rules = state.rules,
            lastQuickAddProfileId = state.lastQuickAddProfileId,
        )
        dao.upsertProfile(profile.toEntity())
    }

    override suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean = mutation.withLock {
        val state = persistentConfiguration()
        if (state.globalProfileId == profileId || state.rules.any { it.profileId == profileId }) return@withLock false
        if (state.lastQuickAddProfileId == profileId) {
            dao.upsertSettings(settings().copy(lastQuickAddProfileId = null))
        }
        dao.deleteProfile(profileId.value)
        true
    }

    override suspend fun upsertRule(rule: NetworkConditionRule) = mutation.withLock {
        val state = persistentConfiguration()
        require(state.profile(rule.profileId) != null) { "Unknown rule profile." }
        val candidate = state.rules.filterNot { it.id == rule.id } + rule
        require(candidate.map { it.target }.distinct().size == candidate.size) { "An equivalent rule already exists." }
        dao.upsertRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: NetworkConditionRuleId) = mutation.withLock {
        dao.deleteRule(ruleId.value)
    }

    override suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?) = mutation.withLock {
        require(profileId == null || persistentConfiguration().profile(profileId) != null) { "Unknown quick-add profile." }
        dao.upsertSettings(settings().copy(lastQuickAddProfileId = profileId?.value))
    }

    override suspend fun resetShaping() = updateSettings { it.copy(enabled = false, globalProfileId = null) }

    private suspend fun updateSettings(transform: (NetworkConditionSettingsEntity) -> NetworkConditionSettingsEntity) {
        mutation.withLock { dao.upsertSettings(transform(settings())) }
    }

    private suspend fun settings(): NetworkConditionSettingsEntity =
        dao.getSettings() ?: NetworkConditionSettingsEntity(
            enabled = false,
            globalProfileId = null,
            lastQuickAddProfileId = null,
        )

    private suspend fun persistentConfiguration(): NetworkConditionConfiguration {
        val settings = settings()
        return NetworkConditionConfiguration(
            enabled = settings.enabled,
            globalProfileId = settings.globalProfileId?.let(::NetworkConditionProfileId),
            customProfiles = dao.getProfiles().map(NetworkConditionProfileEntity::toDomain),
            rules = dao.getRules().map(NetworkConditionRuleEntity::toDomain),
            lastQuickAddProfileId = settings.lastQuickAddProfileId?.let(::NetworkConditionProfileId),
        )
    }
}

private fun NetworkConditionProfile.toEntity() = NetworkConditionProfileEntity(
    id = id.value,
    name = name,
    downloadBitsPerSecond = download.bitsPerSecond,
    downloadUtilizationPercent = download.utilizationPercent,
    uploadBitsPerSecond = upload.bitsPerSecond,
    uploadUtilizationPercent = upload.utilizationPercent,
    latencyMillis = latencyMillis,
    jitterMillis = jitterMillis,
    virtualMtuBytes = virtualMtuBytes,
    failureKind = when (failure) {
        NetworkFailureBehavior.None -> "none"
        NetworkFailureBehavior.Offline -> "offline"
        NetworkFailureBehavior.Timeout -> "timeout"
        NetworkFailureBehavior.ResetFlow -> "reset"
        is NetworkFailureBehavior.SeededReset -> "seeded-reset"
    },
    failureProbabilityPercent = (failure as? NetworkFailureBehavior.SeededReset)?.probabilityPercent,
    failureSeed = (failure as? NetworkFailureBehavior.SeededReset)?.seed,
    packetLossPercent = packetLossPercent,
    packetDuplicationPercent = packetDuplicationPercent,
    packetReorderingPercent = packetReorderingPercent,
)

private fun NetworkConditionProfileEntity.toDomain() = NetworkConditionProfile(
    id = NetworkConditionProfileId(id),
    name = name,
    download = NetworkDirectionCondition(downloadBitsPerSecond, downloadUtilizationPercent),
    upload = NetworkDirectionCondition(uploadBitsPerSecond, uploadUtilizationPercent),
    latencyMillis = latencyMillis,
    jitterMillis = jitterMillis,
    virtualMtuBytes = virtualMtuBytes,
    failure = when (failureKind) {
        "none" -> NetworkFailureBehavior.None
        "offline" -> NetworkFailureBehavior.Offline
        "timeout" -> NetworkFailureBehavior.Timeout
        "reset" -> NetworkFailureBehavior.ResetFlow
        "seeded-reset" -> NetworkFailureBehavior.SeededReset(
            checkNotNull(failureProbabilityPercent),
            checkNotNull(failureSeed),
        )
        else -> error("Unknown network failure kind.")
    },
    packetLossPercent = packetLossPercent,
    packetDuplicationPercent = packetDuplicationPercent,
    packetReorderingPercent = packetReorderingPercent,
)

private fun NetworkConditionRule.toEntity() = NetworkConditionRuleEntity(
    id = id.value,
    normalizedHost = target.normalizedHost,
    wildcard = target.wildcard,
    port = target.port,
    profileId = profileId.value,
    enabled = enabled,
)

private fun NetworkConditionRuleEntity.toDomain() = NetworkConditionRule(
    id = NetworkConditionRuleId(id),
    target = NetworkConditionTarget.parse((if (wildcard) "*." else "") + normalizedHost, port),
    profileId = NetworkConditionProfileId(profileId),
    enabled = enabled,
)
