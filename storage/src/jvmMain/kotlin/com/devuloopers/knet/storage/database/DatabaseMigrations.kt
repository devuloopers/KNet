package com.devuloopers.knet.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Room database migration definitions for [KNetDatabase].
 */
public object DatabaseMigrations {

    public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDnsMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTcpMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTlsMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTtfbMs INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDownloadMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    public val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN authUsername TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN authPassword TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN apiKeyName TEXT NOT NULL DEFAULT 'X-API-Key'")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN apiKeyValue TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN apiKeyLocation TEXT NOT NULL DEFAULT 'Header'")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN oauthHeaderPrefix TEXT NOT NULL DEFAULT 'Bearer'")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN awsAccessKey TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN awsSecretKey TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN awsRegion TEXT NOT NULL DEFAULT 'us-east-1'")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN awsService TEXT NOT NULL DEFAULT 's3'")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN preRequestScript TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN testScript TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE saved_requests ADD COLUMN scriptLanguage TEXT NOT NULL DEFAULT 'JAVASCRIPT'")
        }
    }
}
