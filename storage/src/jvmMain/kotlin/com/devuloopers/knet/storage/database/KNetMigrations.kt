package com.devuloopers.knet.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Enables the new rejection fallback by default without changing saved actions, rules or historical decisions. */
internal object Migration35To36 : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE protected_traffic_settings ADD COLUMN automaticTlsFallbackEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/** Adds nullable close provenance without fabricating evidence for retained captures. */
internal object Migration34To35 : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN closureInitiator TEXT")
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN closureReason TEXT")
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN closureObservedAtEpochMillis INTEGER")
    }
}

/**
 * Retires the preset catalogue, importing only already-enabled v1 choices as ordinary visible saved exceptions.
 * Historical service names exist solely in this versioned upgrade; fresh databases have no built-in rules.
 * USER retains its higher policy precedence, even for an equivalent disabled explicit rule.
 */
internal object Migration33To34 : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE protected_traffic_rules ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER'")
        val oldIndex = "index_protected_traffic_rules_sourceApplication_destinationKind_destinationValue_destinationPrefixLength_port_transport_ipFamily"
        connection.execSQL("DROP INDEX IF EXISTS `$oldIndex`")
        connection.execSQL("CREATE UNIQUE INDEX `${oldIndex}_origin` ON protected_traffic_rules " +
            "(sourceApplication, destinationKind, destinationValue, destinationPrefixLength, port, transport, ipFamily, origin)")

        // These are the exact v1 definitions shipped before preset retirement. Never use them for runtime discovery.
        val historical = listOf(
            LegacyRule("builtin-google-play-store-v1", "google-play-billing", application = "com.android.vending"),
            LegacyRule("builtin-google-play-services-v1", "google-system-services", application = "com.google.android.gms"),
            LegacyRule("builtin-google-services-framework-v1", "google-system-services", application = "com.google.android.gsf"),
            LegacyRule("builtin-apple-storekit-agent-v1", "apple-app-store-storekit", application = "com.apple.storekitagent"),
            LegacyRule("builtin-apple-app-store-v1", "apple-app-store-storekit", application = "com.apple.AppStore"),
            LegacyRule("builtin-google-play-api-v1", "google-play-billing", host = "play.googleapis.com"),
            LegacyRule("builtin-google-play-frontend-v1", "google-play-billing", host = "play-fe.googleapis.com"),
            LegacyRule("builtin-google-android-clients-v1", "google-play-billing", host = "android.clients.google.com"),
            LegacyRule("builtin-apple-buy-itunes-v1", "apple-app-store-storekit", host = "buy.itunes.apple.com"),
            LegacyRule("builtin-apple-sandbox-itunes-v1", "apple-app-store-storekit", host = "sandbox.itunes.apple.com"),
            LegacyRule("builtin-apple-storekit-itunes-v1", "apple-app-store-storekit", host = "storekit.itunes.apple.com"),
        )
        historical.forEach { legacy ->
            connection.prepare(
                "INSERT INTO protected_traffic_rules (id, enabled, action, sourceApplication, destinationKind, " +
                    "destinationValue, destinationPrefixLength, destinationIpFamily, port, transport, ipFamily, origin) " +
                    "SELECT ?1, 1, 'TUNNEL', ?2, ?3, ?4, NULL, NULL, ?5, ?6, NULL, 'MIGRATED_PRESET' " +
                    "WHERE EXISTS (SELECT 1 FROM protected_service_groups WHERE groupId = ?7 AND enabled = 1) " +
                    "ON CONFLICT(id) DO NOTHING",
            ).use { statement ->
                statement.bindText(1, legacy.id)
                if (legacy.application == null) statement.bindNull(2) else statement.bindText(2, legacy.application)
                if (legacy.host == null) {
                    statement.bindNull(3); statement.bindNull(4); statement.bindNull(5); statement.bindNull(6)
                } else {
                    statement.bindText(3, "exact-host"); statement.bindText(4, legacy.host)
                    statement.bindLong(5, 443); statement.bindText(6, "TCP")
                }
                statement.bindText(7, legacy.group)
                statement.step()
            }
        }
        connection.execSQL("UPDATE protected_service_groups SET enabled = 0")
    }

    private data class LegacyRule(val id: String, val group: String, val application: String? = null, val host: String? = null)
}

/** Adds default-on Smart Compatibility without rewriting any existing actions or rules. */
internal object Migration32To33 : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE protected_traffic_settings ADD COLUMN smartCompatibilityEnabled INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN protectionOrigin TEXT")
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN inspectionFailureReason TEXT")
        connection.execSQL("ALTER TABLE opaque_flows ADD COLUMN smartEntryId TEXT")
    }
}

/** Adds the durable exchange ordinal while retaining every canonical v18 exchange. */
internal object Migration18To19 : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_traffic_exchanges` (" +
                "`captureSequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL, " +
                "`sessionId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `streamId` INTEGER, " +
                "`connectionSequence` INTEGER NOT NULL, `version` INTEGER NOT NULL, `state` TEXT NOT NULL, " +
                "`startedAtEpochMillis` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, " +
                "`method` TEXT NOT NULL, `scheme` TEXT, `host` TEXT, `port` INTEGER, " +
                "`pathAndQuery` TEXT NOT NULL, `protocol` TEXT NOT NULL, `requestHeadersEncoded` TEXT NOT NULL, " +
                "`requestBodyId` TEXT, `responseProtocol` TEXT, `responseStatusCode` INTEGER, " +
                "`responseReasonPhrase` TEXT, `responseHeadersEncoded` TEXT, `responseBodyId` TEXT, " +
                "`timingDnsMillis` INTEGER, `timingConnectMillis` INTEGER, `timingTlsMillis` INTEGER, " +
                "`timingFirstByteMillis` INTEGER, `timingDownloadMillis` INTEGER, `timingTotalMillis` INTEGER, " +
                "`terminalErrorCode` TEXT)",
        )
        connection.execSQL(
            "INSERT INTO `_new_traffic_exchanges` (" +
                "`id`, `sessionId`, `connectionId`, `streamId`, `connectionSequence`, `version`, `state`, " +
                "`startedAtEpochMillis`, `completedAtEpochMillis`, `method`, `scheme`, `host`, `port`, " +
                "`pathAndQuery`, `protocol`, `requestHeadersEncoded`, `requestBodyId`, `responseProtocol`, " +
                "`responseStatusCode`, `responseReasonPhrase`, `responseHeadersEncoded`, `responseBodyId`, " +
                "`timingDnsMillis`, `timingConnectMillis`, `timingTlsMillis`, `timingFirstByteMillis`, " +
                "`timingDownloadMillis`, `timingTotalMillis`, `terminalErrorCode`) " +
                "SELECT `id`, `sessionId`, `connectionId`, `streamId`, `connectionSequence`, `version`, `state`, " +
                "`startedAtEpochMillis`, `completedAtEpochMillis`, `method`, `scheme`, `host`, `port`, " +
                "`pathAndQuery`, `protocol`, `requestHeadersEncoded`, `requestBodyId`, `responseProtocol`, " +
                "`responseStatusCode`, `responseReasonPhrase`, `responseHeadersEncoded`, `responseBodyId`, " +
                "`timingDnsMillis`, `timingConnectMillis`, `timingTlsMillis`, `timingFirstByteMillis`, " +
                "`timingDownloadMillis`, `timingTotalMillis`, `terminalErrorCode` " +
                "FROM `traffic_exchanges` ORDER BY `startedAtEpochMillis`, `id`",
        )
        connection.execSQL("DROP TABLE `traffic_exchanges`")
        connection.execSQL("ALTER TABLE `_new_traffic_exchanges` RENAME TO `traffic_exchanges`")
        listOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_id` ON `traffic_exchanges` (`id`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_session_sequence` ON `traffic_exchanges` (`sessionId`, `captureSequence`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_session_host_sequence` ON `traffic_exchanges` (`sessionId`, `host`, `captureSequence`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_session_method_sequence` ON `traffic_exchanges` (`sessionId`, `method`, `captureSequence`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_session_status_sequence` ON `traffic_exchanges` (`sessionId`, `responseStatusCode`, `captureSequence`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_session_protocol_sequence` ON `traffic_exchanges` (`sessionId`, `protocol`, `captureSequence`)",
            "CREATE INDEX IF NOT EXISTS `index_exchange_connection_sequence` ON `traffic_exchanges` (`connectionId`, `connectionSequence`)",
        ).forEach { statement -> connection.execSQL(statement) }
    }
}

/** Rebuilds legacy duplex rows into the explicit v23 lifecycle without fabricating payload bytes. */
internal object Migration22To23 : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_duplex_messages` (" +
                "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, " +
                "`exchangeId` TEXT NOT NULL, `streamId` INTEGER, `captureSequence` INTEGER NOT NULL, " +
                "`messageSequence` INTEGER NOT NULL, `direction` TEXT NOT NULL, `protocol` TEXT NOT NULL, " +
                "`messageKind` TEXT NOT NULL, `occurredAtEpochMillis` INTEGER NOT NULL, `declaredBytes` INTEGER, " +
                "`observedBytes` INTEGER NOT NULL, `compressed` INTEGER NOT NULL, `compressionEncoding` TEXT, " +
                "`bodyId` TEXT, `state` TEXT NOT NULL, `errorCode` TEXT, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "INSERT INTO `_new_duplex_messages` (" +
                "`id`, `sessionId`, `connectionId`, `exchangeId`, `streamId`, `captureSequence`, " +
                "`messageSequence`, `direction`, `protocol`, `messageKind`, `occurredAtEpochMillis`, " +
                "`declaredBytes`, `observedBytes`, `compressed`, `compressionEncoding`, `bodyId`, `state`, `errorCode`) " +
                "SELECT `id`, `sessionId`, `connectionId`, `exchangeId`, `streamId`, `sequence`, `sequence`, " +
                "`direction`, 'WEBSOCKET', `messageKind`, `occurredAtEpochMillis`, NULL, 0, 0, NULL, `bodyId`, " +
                "CASE WHEN `terminal` != 0 THEN 'COMPLETE' ELSE 'IN_PROGRESS' END, NULL " +
                "FROM `duplex_messages` WHERE `exchangeId` IS NOT NULL",
        )
        connection.execSQL("DROP TABLE `duplex_messages`")
        connection.execSQL("ALTER TABLE `_new_duplex_messages` RENAME TO `duplex_messages`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_duplex_connection_sequence` " +
                "ON `duplex_messages` (`connectionId`, `captureSequence`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_duplex_exchange_direction_message_sequence` " +
                "ON `duplex_messages` (`exchangeId`, `direction`, `messageSequence`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_duplex_session_occurred` " +
                "ON `duplex_messages` (`sessionId`, `occurredAtEpochMillis`)",
        )
    }
}
/** Adds source admission lookup without rewriting retained connection identity or traffic records. */
internal object Migration36To37 : Migration(36, 37) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_connection_source_identity ON traffic_connections (ingressKind, clientIdentity, id)")
    }
}

/** Adds reference lookups and a durable, indexed cleanup retry deadline without rewriting capture data. */
internal object Migration37To38 : Migration(37, 38) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_request_body ON traffic_exchanges(requestBodyId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_response_body ON traffic_exchanges(responseBodyId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_duplex_body ON duplex_messages(bodyId)")
        connection.execSQL("ALTER TABLE deletion_outbox ADD COLUMN nextAttemptAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_deletion_due_id ON deletion_outbox(nextAttemptAtEpochMillis, id)")
    }
}
