package com.devuloopers.knet.storage.database

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class KNetDatabaseMigrationTest {
    @Test
    fun `every retained schema generation migrates without deleting canonical sessions`() {
        listOf(13, 18, 22, 24, 27, 28, 29, 30).forEach { version ->
            val root = Files.createTempDirectory("knet-v$version-migration-").toFile()
            val databaseFile = root.resolve("traffic.db")
            try {
                createDatabaseFromExportedSchema(databaseFile, version)
                BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
                    connection.execSQL(
                        "INSERT INTO capture_sessions " +
                            "(id, startedAtEpochMillis, endedAtEpochMillis, state, version) " +
                            "VALUES ('preserved-session-$version', 1, 2, 'CLOSED', 1)",
                    )
                    if (version == 18) {
                        connection.execSQL(
                            "INSERT INTO traffic_exchanges (" +
                                "id, sessionId, connectionId, connectionSequence, version, state, " +
                                "startedAtEpochMillis, completedAtEpochMillis, method, pathAndQuery, protocol, " +
                                "requestHeadersEncoded) VALUES (" +
                                "'legacy-exchange', 'preserved-session-18', 'legacy-connection', 1, 1, " +
                                "'COMPLETED', 3, 4, 'GET', '/', 'HTTP/1.1', 'H1:0:')",
                        )
                    }
                    if (version == 22) {
                        connection.execSQL(
                            "INSERT INTO duplex_messages (" +
                                "id, sessionId, connectionId, exchangeId, sequence, direction, messageKind, " +
                                "occurredAtEpochMillis, terminal) VALUES (" +
                                "'legacy-message', 'preserved-session-22', 'legacy-connection', " +
                                "'legacy-message-exchange', 7, 'SERVER_TO_CLIENT', 'BINARY', 8, 1)",
                        )
                    }
                    if (version >= 27) {
                        connection.execSQL(
                            "INSERT INTO network_condition_profiles (" +
                                "id, name, downloadBitsPerSecond, downloadUtilizationPercent, " +
                                "uploadBitsPerSecond, uploadUtilizationPercent, latencyMillis, jitterMillis, " +
                                "virtualMtuBytes, failureKind, failureProbabilityPercent, failureSeed, " +
                                "packetLossPercent, packetDuplicationPercent, packetReorderingPercent" +
                                ") VALUES (" +
                                "'migrated-profile', 'Migrated profile', 100000, 80, NULL, 100, 25, 5, " +
                                "1200, 'NONE', NULL, NULL, 4, 2, 3)",
                        )
                        connection.execSQL(
                            "INSERT INTO network_condition_rules (" +
                                "id, normalizedHost, wildcard, port, profileId, enabled" +
                                ") VALUES ('migrated-rule', 'video.example', 1, 443, 'migrated-profile', 1)",
                        )
                        connection.execSQL(
                            "INSERT INTO network_condition_settings (" +
                                "singletonId, enabled, globalProfileId, lastQuickAddProfileId" +
                                ") VALUES (1, 1, 'migrated-profile', 'migrated-profile')",
                        )
                    }
                }

                val database = DatabaseFactory.create(databaseFile)
                try {
                    val session = runBlocking {
                        database.canonicalCaptureDao().getSession("preserved-session-$version")
                    }
                    assertEquals("preserved-session-$version", session?.id)
                    if (version == 18) {
                        val exchange = runBlocking {
                            database.canonicalCaptureDao().getExchange("legacy-exchange")
                        }
                        assertEquals("legacy-exchange", exchange?.id)
                        assertEquals(1L, exchange?.captureSequence)
                    }
                    if (version == 22) {
                        val message = runBlocking {
                            database.canonicalCaptureDao().getDuplexMessagePage(
                                exchangeId = "legacy-message-exchange",
                                afterCaptureSequence = null,
                                limit = 10,
                            ).single()
                        }
                        assertEquals("legacy-message", message.id)
                        assertEquals(7L, message.captureSequence)
                        assertEquals("WEBSOCKET", message.protocol)
                        assertEquals("COMPLETE", message.state)
                    }
                    if (version >= 27) {
                        val settings = runBlocking { database.networkConditionDao().getSettings() }
                        val profile = runBlocking { database.networkConditionDao().getProfiles().single() }
                        val rule = runBlocking { database.networkConditionDao().getRules().single() }
                        assertEquals(true, settings?.enabled)
                        assertEquals("migrated-profile", settings?.globalProfileId)
                        assertEquals("migrated-profile", settings?.lastQuickAddProfileId)
                        assertEquals(100_000L, profile.downloadBitsPerSecond)
                        assertEquals(80, profile.downloadUtilizationPercent)
                        assertEquals(1_200, profile.virtualMtuBytes)
                        assertEquals(4, profile.packetLossPercent)
                        assertEquals("video.example", rule.normalizedHost)
                        assertEquals(true, rule.wildcard)
                        assertEquals(443, rule.port)
                        assertEquals("migrated-profile", rule.profileId)
                    }
                } finally {
                    database.close()
                }
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private fun createDatabaseFromExportedSchema(databaseFile: File, version: Int) {
        val schemaFile = File(
            "schemas/com.devuloopers.knet.storage.database.KNetDatabase/$version.json",
        )
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
            database.getValue("entities").jsonArray.forEach { entityElement ->
                val entity = entityElement.jsonObject
                val tableName = entity.getValue("tableName").jsonPrimitive.content
                connection.execSQL(
                    entity.getValue("createSql").jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", tableName),
                )
                entity["indices"]?.jsonArray?.forEach { indexElement ->
                    connection.execSQL(
                        indexElement.jsonObject.getValue("createSql").jsonPrimitive.content
                            .replace("${'$'}{TABLE_NAME}", tableName),
                    )
                }
            }
            database["views"]?.jsonArray?.forEach { viewElement ->
                connection.execSQL(viewElement.jsonObject.getValue("createSql").jsonPrimitive.content)
            }
            database.getValue("setupQueries").jsonArray.forEach { query ->
                connection.execSQL(query.jsonPrimitive.content)
            }
            connection.execSQL("PRAGMA user_version = $version")
        }
    }
}
