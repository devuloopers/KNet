package com.devuloopers.knet.storage

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Main database definition for KNet traffic inspection metadata.
 */
@Database(entities = [HttpTransactionEntity::class], version = 1)
abstract class KNetDatabase : RoomDatabase() {

    /**
     * Obtains the transaction DAO interface implementation.
     */
    abstract fun httpTransactionDao(): HttpTransactionDao

    companion object {
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
            return builder.build()
        }
    }
}
