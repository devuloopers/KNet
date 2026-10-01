package com.devuloopers.knet.data.desktop.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomNetworkConditionsRepositoryTest {
    @Test
    fun `profiles rules activation and quick-add preference survive restart`() = runTest {
        val root = Files.createTempDirectory("knet-network-conditions-").toFile()
        val file = root.resolve("knet.db")
        val profile = NetworkConditionProfile(
            id = NetworkConditionProfileId("custom-stream"),
            name = "Custom stream",
            download = NetworkDirectionCondition(100_000L, 80),
            upload = NetworkDirectionCondition(),
            latencyMillis = 25L,
            jitterMillis = 5L,
        )
        val rule = NetworkConditionRule(
            id = NetworkConditionRuleId("rule-video"),
            target = NetworkConditionTarget.parse("*.video.example", 443),
            profileId = profile.id,
        )
        val firstDatabase = DatabaseFactory.create(file)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(firstDatabase.networkConditionDao(), firstScope)
            repository.upsertProfile(profile)
            repository.configuration.first { it.profile(profile.id) != null }
            repository.setGlobalProfile(profile.id)
            repository.upsertRule(rule)
            repository.setLastQuickAddProfile(profile.id)
            repository.setEnabled(true)
            repository.configuration.first { it.enabled && it.rules.singleOrNull() == rule }
        } finally {
            firstScope.cancel()
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(file)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(restartedDatabase.networkConditionDao(), restartedScope)
            val restored = repository.configuration.first { it.enabled && it.rules.isNotEmpty() }
            assertEquals(profile, restored.customProfiles.single())
            assertEquals(profile.id, restored.globalProfileId)
            assertEquals(profile.id, restored.lastQuickAddProfileId)
            assertEquals(rule, restored.rules.single())
            assertFalse(repository.deleteProfile(profile.id))

            repository.deleteRule(rule.id)
            repository.setGlobalProfile(null)
            repository.setLastQuickAddProfile(null)
            assertTrue(repository.deleteProfile(profile.id))
        } finally {
            restartedScope.cancel()
            restartedDatabase.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `equivalent normalized targets are rejected and corrupt settings fail closed`() = runTest {
        val root = Files.createTempDirectory("knet-network-conditions-invalid-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(database.networkConditionDao(), scope)
            val builtIn = repository.configuration.value.profiles.first().id
            repository.upsertRule(
                NetworkConditionRule(
                    NetworkConditionRuleId("first"),
                    NetworkConditionTarget.parse("API.Example.Test."),
                    builtIn,
                ),
            )
            repository.configuration.first { it.rules.size == 1 }
            assertFailsWith<IllegalArgumentException> {
                repository.upsertRule(
                    NetworkConditionRule(
                        NetworkConditionRuleId("second"),
                        NetworkConditionTarget.parse("api.example.test"),
                        builtIn,
                    ),
                )
            }

            database.networkConditionDao().upsertSettings(
                NetworkConditionSettingsEntity(
                    enabled = true,
                    globalProfileId = "missing-profile",
                    lastQuickAddProfileId = null,
                ),
            )
            val failedClosed = repository.configuration.first { !it.enabled && it.rules.isEmpty() }
            assertEquals(null, failedClosed.globalProfileId)
        } finally {
            scope.cancel()
            database.close()
            root.deleteRecursively()
        }
    }
}
