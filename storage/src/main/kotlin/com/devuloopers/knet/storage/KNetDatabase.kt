package com.devuloopers.knet.storage

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

import com.devuloopers.knet.storage.apistudio.CollectionDao
import com.devuloopers.knet.storage.apistudio.CollectionEntity
import com.devuloopers.knet.storage.apistudio.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.SavedRequestEntity
import com.devuloopers.knet.storage.interception.HttpTransactionDao
import com.devuloopers.knet.storage.interception.HttpTransactionEntity

/**
 * Main database definition for KNet traffic inspection metadata.
 */
@Database(
    entities = [
        HttpTransactionEntity::class,
        CollectionEntity::class,
        CollectionFolderEntity::class,
        SavedRequestEntity::class
    ],
    version = 4
)
abstract class KNetDatabase : RoomDatabase() {

    /**
     * Obtains the transaction DAO interface implementation.
     */
    abstract fun httpTransactionDao(): HttpTransactionDao

    /**
     * Obtains the collections DAO interface implementation.
     */
    abstract fun collectionDao(): CollectionDao

    companion object {
        /**
         * Room Migration from v1 to v2 adding high-resolution socket timing columns.
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDnsMs INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTcpMs INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTlsMs INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingTtfbMs INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE HttpTransactionEntity ADD COLUMN timingDownloadMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Room Migration from v3 to v4 adding auth and script columns to saved_requests.
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
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

        /**
         * Creates and configures a [KNetDatabase] instance using the Bundled SQLite Driver.
         *
         * @param dbFile The SQLite database file on disk.
         * @return A thread-safe, configured [KNetDatabase] instance.
         */
        fun create(dbFile: File): KNetDatabase {
            val builder = Room.databaseBuilder<KNetDatabase>(
                name = dbFile.absolutePath,
                factory = { KNetDatabase_Impl() }
            )
            builder.setDriver(BundledSQLiteDriver())
            builder.addMigrations(MIGRATION_1_2, MIGRATION_3_4)
            builder.fallbackToDestructiveMigration(dropAllTables = true)
            return builder.build()
        }
    }
}
