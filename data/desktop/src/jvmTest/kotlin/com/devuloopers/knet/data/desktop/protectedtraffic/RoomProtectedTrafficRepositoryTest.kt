package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.ProtectedDestinationSelector
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficBuiltIns
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRule
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficRuleId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity
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

/** Restart, validation, group-toggle, and corrupt-state coverage for the Room repository. */
class RoomProtectedTrafficRepositoryTest {
    @Test
    fun `rules defaults and compatibility groups survive restart`() = runTest {
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
            repository.setBuiltInGroupEnabled(ProtectedTrafficBuiltIns.GooglePlayBillingGroupId, true)
            repository.configuration.first { state ->
                state.defaultAction == ProtectedTrafficAction.BLOCK &&
                    rule in state.rules &&
                    ProtectedTrafficBuiltIns.GooglePlayBillingGroupId in state.enabledBuiltInGroups
            }
        } finally {
            firstScope.cancel()
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(databaseFile)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(restartedDatabase.protectedTrafficDao(), restartedScope)
            val restored = repository.configuration.first { state ->
                state.defaultAction == ProtectedTrafficAction.BLOCK && rule in state.rules
            }
            assertTrue(ProtectedTrafficBuiltIns.GooglePlayBillingGroupId in restored.enabledBuiltInGroups)
            assertTrue(ProtectedTrafficBuiltIns.rules.all { it in restored.rules })

            repository.deleteRule(rule.id)
            assertFalse(repository.configuration.first { rule !in it.rules }.rules.contains(rule))
        } finally {
            restartedScope.cancel()
            restartedDatabase.close()
            root.deleteRecursively()
        }
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
            assertEquals(ProtectedTrafficBuiltIns.rules, recovered.rules)
            assertTrue(recovered.enabledBuiltInGroups.isEmpty())
        } finally {
            scope.cancel()
            database.close()
            root.deleteRecursively()
        }
    }
}
