package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Restart, validation, retired-setting compatibility, and corrupt-state coverage for the Room repository. */
class RoomProtectedTrafficRepositoryTest {
    @Test
    fun `saved rules and default handling survive restart`() = runTest {
        val root = Files.createTempDirectory("knet-protected-traffic-").toFile()
        val databaseFile = root.resolve("knet.db")
        val rule = ProtectedTrafficRule(
            id = ProtectedTrafficRuleId("pinned-video"),
            action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse("*.video.example"),
            port = 443,
        )
        val firstDatabase = DatabaseFactory.create(databaseFile)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(firstDatabase.protectedTrafficDao(), firstScope)
            repository.setDefaultAction(ProtectedTrafficAction.BLOCK)
            repository.upsertRule(rule)
            repository.configuration.first { state ->
                state.defaultAction == ProtectedTrafficAction.BLOCK &&
                    rule in state.rules
            }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(databaseFile)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(restartedDatabase.protectedTrafficDao(), restartedScope)
            val restored = repository.configuration.first { state ->
                state.defaultAction == ProtectedTrafficAction.BLOCK && rule in state.rules
            }
            assertTrue(restored.enabledBuiltInGroups.isEmpty())
            assertEquals(listOf(rule), restored.rules)

            repository.deleteRule(rule.id)
            assertFalse(repository.configuration.first { rule !in it.rules }.rules.contains(rule))
        } finally {
            restartedScope.coroutineContext.job.cancelAndJoin()
            restartedDatabase.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `legacy automatic setting never changes explicit or default policy`() = runTest {
        val root = Files.createTempDirectory("knet-retired-automatic-setting-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            for (legacyEnabled in listOf(true, false)) {
                database.protectedTrafficDao().upsertSettings(ProtectedTrafficSettingsEntity(
                    defaultAction = ProtectedTrafficAction.INSPECT.name, smartCompatibilityEnabled = legacyEnabled))
                val configuration = repository.configuration.first { it.configurationReady && it.defaultAction == ProtectedTrafficAction.INSPECT }
                assertTrue(configuration.rules.isEmpty())
                val policy = ProtectedTrafficTlsInterceptionPolicy({ repository.configuration.value })
                val request = com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRequest("new.example", 443)
                repeat(10) {
                    assertEquals(com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode.INSPECT, policy.decide(request).mode)
                }
                repository.setDefaultAction(ProtectedTrafficAction.TUNNEL)
                repository.configuration.first { it.defaultAction == ProtectedTrafficAction.TUNNEL }
                assertEquals(com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode.TUNNEL, policy.decide(request).mode)
                assertEquals(legacyEnabled, database.protectedTrafficDao().getSettings()!!.smartCompatibilityEnabled,
                    "Writing current policy must preserve compatible legacy storage without consulting it")
            }
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
    }

    @Test
    fun `duplicate normalized selectors are rejected and corrupt settings use inspect default`() = runTest {
        val root = Files.createTempDirectory("knet-protected-traffic-invalid-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            repository.upsertRule(
                ProtectedTrafficRule(
                    id = ProtectedTrafficRuleId("first"),
                    action = ProtectedTrafficAction.TUNNEL,
                    destination = ProtectedDestinationSelector.parse("API.EXAMPLE."),
                ),
            )
            repository.configuration.first { it.rules.any { rule -> rule.id.value == "first" } }
            assertFailsWith<IllegalArgumentException> {
                repository.upsertRule(
                    ProtectedTrafficRule(
                        id = ProtectedTrafficRuleId("second"),
                        action = ProtectedTrafficAction.BLOCK,
                        destination = ProtectedDestinationSelector.parse("api.example"),
                    ),
                )
            }

            repository.setDefaultAction(ProtectedTrafficAction.BLOCK)
            repository.configuration.first { it.defaultAction == ProtectedTrafficAction.BLOCK }
            database.protectedTrafficDao().upsertSettings(
                ProtectedTrafficSettingsEntity(defaultAction = "unsupported-action"),
            )
            val recovered = repository.configuration.first { it.defaultAction == ProtectedTrafficAction.INSPECT }
            assertTrue(recovered.rules.isEmpty())
            assertTrue(recovered.enabledBuiltInGroups.isEmpty())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            database.close()
            root.deleteRecursively()
        }
    }
}
