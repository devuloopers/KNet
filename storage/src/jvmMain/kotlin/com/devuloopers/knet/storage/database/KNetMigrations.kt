package com.devuloopers.knet.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
