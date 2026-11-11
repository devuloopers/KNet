package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.storage.database.DatabaseFactory
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** Upgrade evidence for removing presets without changing previously selected policy behaviour. */
class LegacyPresetRetirementTest {
    @Test fun `enabled preset becomes visible deletable exceptions and never repopulates on restart`() = runTest {
        val root = Files.createTempDirectory("knet-retire-preset-").toFile()
        val file = root.resolve("traffic.db")
        createLegacy(file, setOf("google-play-billing"), manualEnabled = true)
        var database = DatabaseFactory.create(file)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            val configuration = repository.configuration.first { it.configurationReady }
            assertEquals(ProtectedTrafficAction.BLOCK, configuration.defaultAction)
            assertTrue(configuration.enabledBuiltInGroups.isEmpty())
            assertTrue(database.protectedTrafficDao().observeGroups().first().none { it.enabled })
            assertEquals(4, configuration.rules.count { it.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET })
            val policy = ProtectedTrafficTlsInterceptionPolicy({ repository.configuration.value })
            assertEquals(TlsInterceptionMode.INSPECT, policy.decide(TlsInterceptionRequest("play.googleapis.com", 443)).mode)
            assertEquals(TlsInterceptionMode.TUNNEL, policy.decide(TlsInterceptionRequest("play-fe.googleapis.com", 443)).mode)
            assertEquals(com.devuloopers.knet.traffic.model.ProtectionDecisionOrigin.PRESET,
                policy.decide(TlsInterceptionRequest("play-fe.googleapis.com", 443)).origin)
            assertEquals(TlsInterceptionMode.BLOCK, policy.decide(TlsInterceptionRequest("buy.itunes.apple.com", 443)).mode)

            val imported = configuration.rules.single { it.id.value == "builtin-google-play-frontend-v1" }
            repository.upsertRule(imported.copy(enabled = false))
            repository.configuration.first { state -> state.rules.any { it.id == imported.id && !it.enabled } }
            assertEquals(TlsInterceptionMode.BLOCK, policy.decide(TlsInterceptionRequest("play-fe.googleapis.com", 443)).mode)
            repository.deleteRule(imported.id)
            repository.configuration.first { state -> state.rules.none { it.id == imported.id } }
            scope.coroutineContext.job.cancelAndJoin(); database.close()
            database = DatabaseFactory.create(file)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            val restored = repository.configuration.first { it.configurationReady }
            assertTrue(restored.rules.none { it.id == imported.id })
            assertEquals(3, restored.rules.count { it.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET })
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
    }

    @Test fun `disabled explicit overlap does not suppress the retained lower priority exception`() = runTest {
        val root = Files.createTempDirectory("knet-retire-overlap-").toFile()
        val file = root.resolve("traffic.db")
        createLegacy(file, setOf("google-play-billing"), manualEnabled = false)
        val database = DatabaseFactory.create(file)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            val configuration = repository.configuration.first { it.configurationReady }
            val manual = configuration.rules.single { it.origin == ProtectedTrafficRuleOrigin.USER }
            val imported = configuration.rules.single { it.id.value == "builtin-google-play-api-v1" }
            val policy = ProtectedTrafficTlsInterceptionPolicy({ repository.configuration.value })
            val request = TlsInterceptionRequest("play.googleapis.com", 443)
            assertEquals(TlsInterceptionMode.TUNNEL, policy.decide(request).mode)
            repository.upsertRule(manual.copy(enabled = true))
            repository.configuration.first { it.rules.any { rule -> rule.id == manual.id && rule.enabled } }
            assertEquals(TlsInterceptionMode.INSPECT, policy.decide(request).mode)
            assertFailsWith<IllegalArgumentException> { repository.upsertRule(imported.copy(origin = ProtectedTrafficRuleOrigin.USER)) }
            assertEquals(TlsInterceptionMode.INSPECT, policy.decide(request).mode)
            assertEquals(5, repository.configuration.value.rules.size)
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
    }

    @Test fun `only previously enabled groups import exact historical scopes`() = runTest {
        listOf(emptySet<String>() to 0, setOf("google-play-billing") to 4,
            setOf("google-system-services") to 2, setOf("apple-app-store-storekit") to 5).forEach { (groups, expected) ->
            val root = Files.createTempDirectory("knet-retire-groups-").toFile()
            val file = root.resolve("traffic.db")
            createLegacy(file, groups, manualEnabled = null)
            val database = DatabaseFactory.create(file)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
                val configuration = repository.configuration.first { it.configurationReady }
                assertEquals(expected, configuration.rules.size)
                assertTrue(configuration.rules.all { it.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET && it.enabled })
                assertTrue(configuration.rules.all { rule ->
                    rule.sourceApplication != null && rule.destination == null && rule.port == null && rule.transport == null ||
                        rule.sourceApplication == null && rule.destination is ProtectedDestinationSelector.Exact &&
                        rule.port == 443 && rule.transport == ProtectedTransportProtocol.TCP
                })
                assertTrue(configuration.enabledBuiltInGroups.isEmpty())
            } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
        }
    }

    @Test fun `imported exceptions cannot be fabricated but deliberate edits become explicit user rules`() = runTest {
        val root = Files.createTempDirectory("knet-retire-edit-").toFile()
        val file = root.resolve("traffic.db")
        createLegacy(file, setOf("google-play-billing"), manualEnabled = null)
        val database = DatabaseFactory.create(file)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
            val imported = repository.configuration.first { it.configurationReady }.rules.first()
            assertFailsWith<IllegalArgumentException> { repository.upsertRule(imported.copy(id = ProtectedTrafficRuleId("forged"))) }
            repository.upsertRule(imported.copy(origin = ProtectedTrafficRuleOrigin.USER, action = ProtectedTrafficAction.BLOCK))
            val edited = repository.configuration.first { config -> config.rules.any { it.id == imported.id && it.origin == ProtectedTrafficRuleOrigin.USER } }
            assertEquals(ProtectedTrafficAction.BLOCK, edited.rules.single { it.id == imported.id }.action)
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); root.deleteRecursively() }
    }

    private fun createLegacy(file: File, groups: Set<String>, manualEnabled: Boolean?) {
        val schema = Json.parseToJsonElement(File("../../storage/schemas/com.devuloopers.knet.storage.database.KNetDatabase/33.json").readText())
            .jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            schema.getValue("entities").jsonArray.forEach { entityElement ->
                val entity = entityElement.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index -> connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", table)) }
            }
            schema["views"]?.jsonArray?.forEach { connection.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content) }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = 33")
            connection.execSQL("INSERT INTO protected_traffic_settings (singletonId, defaultAction, smartCompatibilityEnabled) VALUES (1, 'BLOCK', 0)")
            listOf("google-play-billing", "google-system-services", "apple-app-store-storekit").forEach { group ->
                connection.prepare("INSERT INTO protected_service_groups (groupId, enabled) VALUES (?1, ?2)").use {
                    it.bindText(1, group); it.bindLong(2, if (group in groups) 1 else 0); it.step()
                }
            }
            if (manualEnabled != null) connection.execSQL("INSERT INTO protected_traffic_rules " +
                "(id, enabled, action, sourceApplication, destinationKind, destinationValue, destinationPrefixLength, destinationIpFamily, port, transport, ipFamily) " +
                "VALUES ('manual', ${if (manualEnabled) 1 else 0}, 'INSPECT', NULL, 'exact-host', 'play.googleapis.com', NULL, NULL, 443, 'TCP', NULL)")
        }
    }
}
