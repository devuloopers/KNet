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
    version = 3
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
            builder.addMigrations(MIGRATION_1_2)
            builder.fallbackToDestructiveMigration(dropAllTables = true)
            return builder.build()
        }
    }
}
