package com.devuloopers.knet.data.desktop.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionProfileEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException

/**
 * Room-backed Network Conditions source with committed snapshots and serialized validated mutations.
 * Invalid stored policy data bypasses shaping without deleting saved rows; mutation validation failures propagate.
 *
 * @param database Owns policy tables and transaction-confined connections.
 * @param scope Application-owned observer lifetime; cancel and join it before closing [database].
 */
class RoomNetworkConditionsRepository(
    private val database: KNetDatabase,
    scope: CoroutineScope,
) : NetworkConditionsRepository {
    private val dao = database.networkConditionDao()
    private val transactions = NetworkConditionTransactions(database)

    override val configuration: StateFlow<NetworkConditionConfiguration> = database.invalidationTracker.createFlow(
        "network_condition_settings", "network_condition_profiles", "network_condition_rules",
    ).map {
        // Independently combined table flows can pair settings with an older profile/rule emission.
        // One read transaction instead publishes only a committed, internally consistent policy.
        transactions.read {
            runCatching { persistentConfiguration() }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                NetworkConditionConfiguration()
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, NetworkConditionConfiguration())

    override suspend fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }

    override suspend fun setGlobalProfile(profileId: NetworkConditionProfileId?) = writePolicy {
        require(profileId == null || persistentConfiguration().profile(profileId) != null) { "Unknown global profile." }
        dao.upsertSettings(settings().copy(globalProfileId = profileId?.value))
    }

    override suspend fun activateGlobalProfile(profileId: NetworkConditionProfileId?) = writePolicy {
        require(profileId == null || persistentConfiguration().profile(profileId) != null) { "Unknown global profile." }
        dao.upsertSettings(settings().copy(enabled = true, globalProfileId = profileId?.value))
    }

    override suspend fun upsertProfile(profile: NetworkConditionProfile) = writePolicy {
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

    override suspend fun deleteProfile(profileId: NetworkConditionProfileId): Boolean = writePolicy {
        val state = persistentConfiguration()
        if (state.globalProfileId == profileId || state.rules.any { it.profileId == profileId }) return@writePolicy false
        if (state.lastQuickAddProfileId == profileId) {
            dao.upsertSettings(settings().copy(lastQuickAddProfileId = null))
        }
        dao.deleteProfile(profileId.value)
        true
    }

    override suspend fun upsertRule(rule: NetworkConditionRule) = writePolicy {
        val state = persistentConfiguration()
        require(state.profile(rule.profileId) != null) { "Unknown rule profile." }
        val candidate = state.rules.filterNot { it.id == rule.id } + rule
        require(candidate.map { it.target to it.protocolCriteria }.distinct().size == candidate.size) {
            "An equivalent rule already exists."
        }
        dao.upsertRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: NetworkConditionRuleId) = writePolicy {
        dao.deleteRule(ruleId.value)
    }

    override suspend fun setLastQuickAddProfile(profileId: NetworkConditionProfileId?) = writePolicy {
        require(profileId == null || persistentConfiguration().profile(profileId) != null) { "Unknown quick-add profile." }
        dao.upsertSettings(settings().copy(lastQuickAddProfileId = profileId?.value))
    }

    override suspend fun resetShaping() = updateSettings { it.copy(enabled = false, globalProfileId = null) }

    private suspend fun updateSettings(transform: (NetworkConditionSettingsEntity) -> NetworkConditionSettingsEntity) {
        writePolicy { dao.upsertSettings(transform(settings())) }
    }

    /** Serializes validation/read/modify/write across repository instances and rolls back on cancellation. */
    private suspend fun <Result> writePolicy(block: suspend () -> Result): Result =
        transactions.write(block)

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
    priority = priority,
    protocolId = protocolCriteria.protocolId.value,
    protocolCriteriaPayload = protocolCriteria.encodedPayload,
)

private fun NetworkConditionRuleEntity.toDomain() = NetworkConditionRule(
    id = NetworkConditionRuleId(id),
    target = NetworkConditionTarget.parse((if (wildcard) "*." else "") + normalizedHost, port),
    profileId = NetworkConditionProfileId(profileId),
    enabled = enabled,
    priority = priority,
    protocolCriteria = NetworkConditionProtocolCriteria(
        protocolId = NetworkConditionProtocolId(protocolId),
        encodedPayload = protocolCriteriaPayload,
    ),
)
