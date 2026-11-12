package com.devuloopers.knet.data.desktop.networkconditions

import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionMatcher
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomNetworkConditionsRepositoryTest {
    @Test
    fun `saved unlimited aliases and renamed unstable profile references survive database reopen`() = runTest {
        for (globalId in listOf(null, NetworkConditionBuiltIns.NO_THROTTLING.id)) {
            val root = Files.createTempDirectory("knet-preset-compatibility-").toFile()
            val file = root.resolve("knet.db")
            val rules = listOf(
                NetworkConditionRule(NetworkConditionRuleId("saved-unlimited"), NetworkConditionTarget.parse("unlimited.example", 443),
                    NetworkConditionProfileId("no-throttling")),
                NetworkConditionRule(NetworkConditionRuleId("saved-unstable"), NetworkConditionTarget.parse("unstable.example", 443),
                    NetworkConditionProfileId("lossy-unstable")),
            )
            val database = DatabaseFactory.create(file)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val repository = RoomNetworkConditionsRepository(database, scope)
                rules.forEach { repository.upsertRule(it) }
                repository.activateGlobalProfile(globalId)
                repository.configuration.first { it.enabled && it.rules.size == rules.size && it.globalProfileId == globalId }
            } finally {
                scope.coroutineContext.job.cancelAndJoin()
                database.close()
            }
            val reopened = DatabaseFactory.create(file)
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val restored = RoomNetworkConditionsRepository(reopened, reopenedScope).configuration.first {
                    it.enabled && it.rules.size == rules.size
                }
                assertEquals(globalId, restored.globalProfileId, "Presentation cleanup must not migrate stored identities")
                assertEquals(rules.toSet(), restored.rules.toSet())
                val unlimited = NetworkConditionMatcher.resolve(restored, "unlimited.example", 443)!!.profile
                assertEquals("No throttling", unlimited.name)
                assertTrue(unlimited.isPassThrough)
                val unstable = NetworkConditionMatcher.resolve(restored, "unstable.example", 443)!!.profile
                assertEquals("lossy-unstable", unstable.id.value)
                assertEquals("Unstable connection", unstable.name)
            } finally {
                reopenedScope.coroutineContext.job.cancelAndJoin()
                reopened.close()
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun `quick activation preserves rules and profiles while off unlimited and domain only stay distinct`() = runTest {
        val root = Files.createTempDirectory("knet-network-quick-control-").toFile()
        val file = root.resolve("knet.db")
        val custom = NetworkConditionProfile(NetworkConditionProfileId("quick-custom"), "Custom 100 kbps",
            download = NetworkDirectionCondition(100_000L))
        val rule = NetworkConditionRule(NetworkConditionRuleId("quick-rule"),
            NetworkConditionTarget.parse("video.example", 443), NetworkConditionBuiltIns.SLOW_3G.id)
        val database = DatabaseFactory.create(file)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dao = database.networkConditionDao()
            val repository = RoomNetworkConditionsRepository(database, scope)
            repository.upsertProfile(custom)
            repository.upsertRule(rule)
            repository.activateGlobalProfile(custom.id)
            val active = repository.configuration.first { it.enabled && it.globalProfileId == custom.id && it.rules.isNotEmpty() }
            assertEquals(custom, active.customProfiles.single())
            assertEquals(rule, active.rules.single())
            assertTrue(dao.getSettings()!!.enabled)
            assertEquals(custom.id.value, dao.getSettings()!!.globalProfileId)

            repository.setEnabled(false)
            val off = repository.configuration.first { !it.enabled && it.globalProfileId == custom.id }
            assertEquals(rule, off.rules.single())
            assertEquals(null, NetworkConditionMatcher.resolve(off, "video.example", 443))

            repository.activateGlobalProfile(NetworkConditionBuiltIns.NO_THROTTLING.id)
            val unlimited = repository.configuration.first { it.enabled && it.globalProfileId == NetworkConditionBuiltIns.NO_THROTTLING.id }
            assertEquals(rule.profileId, NetworkConditionMatcher.resolve(unlimited, "video.example", 443)!!.profile.id)

            repository.activateGlobalProfile(null)
            val domainOnly = repository.configuration.first { it.enabled && it.globalProfileId == null && it.rules.isNotEmpty() }
            assertEquals(rule, domainOnly.rules.single())
            val settingsBeforeFailure = dao.getSettings()
            assertFailsWith<IllegalArgumentException> { repository.activateGlobalProfile(NetworkConditionProfileId("missing-profile")) }
            assertEquals(settingsBeforeFailure, dao.getSettings())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            database.close()
        }
        val restoredDatabase = DatabaseFactory.create(file)
        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val restored = RoomNetworkConditionsRepository(restoredDatabase, restoredScope)
                .configuration.first { it.enabled && it.rules.isNotEmpty() }
            assertEquals(null, restored.globalProfileId)
            assertEquals(custom, restored.customProfiles.single())
            assertEquals(rule, restored.rules.single())
        } finally {
            restoredScope.coroutineContext.job.cancelAndJoin()
            restoredDatabase.close()
            root.deleteRecursively()
        }
    }

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
            priority = 42,
            protocolCriteria = NetworkConditionProtocolCriteria(
                NetworkConditionProtocolId.GRAPHQL_HTTP,
                "{\"version\":1,\"operationName\":\"LiveFeed\",\"operationType\":\"query\"}",
            ),
        )
        val firstDatabase = DatabaseFactory.create(file)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(firstDatabase, firstScope)
            repository.upsertProfile(profile)
            repository.configuration.first { it.profile(profile.id) != null }
            repository.setGlobalProfile(profile.id)
            repository.upsertRule(rule)
            repository.setLastQuickAddProfile(profile.id)
            repository.setEnabled(true)
            repository.configuration.first { it.enabled && it.rules.singleOrNull() == rule }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(file)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(restartedDatabase, restartedScope)
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
            restartedScope.coroutineContext.job.cancelAndJoin()
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
            val repository = RoomNetworkConditionsRepository(database, scope)
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
            scope.coroutineContext.job.cancelAndJoin()
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `reset persists bypass while retaining saved profiles rules and quick-add preference`() = runTest {
        val root = Files.createTempDirectory("knet-network-conditions-reset-").toFile()
        val file = root.resolve("knet.db")
        val profile = NetworkConditionProfile(
            id = NetworkConditionProfileId("retained"),
            name = "Retained",
            download = NetworkDirectionCondition(100_000L),
        )
        val rule = NetworkConditionRule(
            NetworkConditionRuleId("retained-rule"),
            NetworkConditionTarget.parse("video.example", 443),
            profile.id,
            enabled = false,
        )
        val firstDatabase = DatabaseFactory.create(file)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(firstDatabase, firstScope)
            repository.upsertProfile(profile)
            repository.setGlobalProfile(profile.id)
            repository.setLastQuickAddProfile(profile.id)
            repository.upsertRule(rule)
            repository.setEnabled(true)
            repository.configuration.first { it.enabled && it.rules.isNotEmpty() }

            repository.resetShaping()
            val reset = repository.configuration.first { !it.enabled && it.globalProfileId == null }
            assertEquals(profile, reset.customProfiles.single())
            assertEquals(rule, reset.rules.single())
            assertEquals(profile.id, reset.lastQuickAddProfileId)
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(file)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val restored = RoomNetworkConditionsRepository(
                restartedDatabase,
                restartedScope,
            ).configuration.first { it.customProfiles.isNotEmpty() }
            assertFalse(restored.enabled)
            assertEquals(null, restored.globalProfileId)
            assertEquals(profile, restored.customProfiles.single())
            assertEquals(rule, restored.rules.single())
            assertEquals(profile.id, restored.lastQuickAddProfileId)
        } finally {
            restartedScope.coroutineContext.job.cancelAndJoin()
            restartedDatabase.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `profile deletion clears a quick-add-only reference but a disabled rule still protects its profile`() = runTest {
        val root = Files.createTempDirectory("knet-network-conditions-delete-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(database, scope)
            val quickAddOnly = NetworkConditionProfile(
                id = NetworkConditionProfileId("quick-add-only"),
                name = "Quick add only",
                download = NetworkDirectionCondition(100_000L),
            )
            repository.upsertProfile(quickAddOnly)
            repository.setLastQuickAddProfile(quickAddOnly.id)
            repository.configuration.first { it.lastQuickAddProfileId == quickAddOnly.id }

            assertTrue(repository.deleteProfile(quickAddOnly.id))
            val deleted = repository.configuration.first {
                it.profile(quickAddOnly.id) == null && it.lastQuickAddProfileId == null
            }
            assertEquals(null, deleted.lastQuickAddProfileId)

            val protected = quickAddOnly.copy(
                id = NetworkConditionProfileId("disabled-rule-profile"),
                name = "Disabled rule profile",
            )
            val disabledRule = NetworkConditionRule(
                id = NetworkConditionRuleId("disabled-protection"),
                target = NetworkConditionTarget.parse("protected.example"),
                profileId = protected.id,
                enabled = false,
            )
            repository.upsertProfile(protected)
            repository.upsertRule(disabledRule)
            repository.configuration.first { it.rules.singleOrNull() == disabledRule }

            assertFalse(repository.deleteProfile(protected.id))
            assertEquals(protected, repository.configuration.value.profile(protected.id))
            assertEquals(disabledRule, repository.configuration.value.rules.single())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `concurrent settings and rule writes serialize without losing independent updates`() = runTest {
        val root = Files.createTempDirectory("knet-network-conditions-concurrent-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomNetworkConditionsRepository(database, scope)
            val profile = NetworkConditionProfile(
                id = NetworkConditionProfileId("concurrent-profile"),
                name = "Concurrent profile",
                download = NetworkDirectionCondition(100_000L),
            )
            val rule = NetworkConditionRule(
                NetworkConditionRuleId("concurrent-rule"),
                NetworkConditionTarget.parse("stream.example", 443),
                profile.id,
            )
            repository.upsertProfile(profile)
            repository.configuration.first { it.profile(profile.id) == profile }

            coroutineScope {
                listOf(
                    async { repository.setEnabled(true) },
                    async { repository.setGlobalProfile(profile.id) },
                    async { repository.activateGlobalProfile(profile.id) },
                    async { repository.setLastQuickAddProfile(profile.id) },
                    async { repository.upsertRule(rule) },
                ).awaitAll()
            }

            val result = repository.configuration.first {
                it.enabled && it.globalProfileId == profile.id &&
                    it.lastQuickAddProfileId == profile.id && it.rules.singleOrNull() == rule
            }
            assertEquals(profile, result.profile(profile.id))
            assertEquals(rule, result.rules.single())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            database.close()
            root.deleteRecursively()
        }
    }
}
