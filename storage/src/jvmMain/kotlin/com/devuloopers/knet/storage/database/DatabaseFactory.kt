package com.devuloopers.knet.storage.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Concrete JVM factory responsible for constructing [KNetDatabase] instances using [BundledSQLiteDriver].
 */
public object DatabaseFactory {

    /**
     * Creates and configures a thread-safe [KNetDatabase] instance.
     *
     * @param dbFile SQLite database file on disk.
     * @return Configured [KNetDatabase] instance.
     */
    public fun create(dbFile: File): KNetDatabase {
        dbFile.parentFile?.mkdirs()

        val builder = Room.databaseBuilder<KNetDatabase>(
            name = dbFile.absolutePath,
            factory = { KNetDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.addMigrations(
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_3_4,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9
        )
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        return builder.build()
    }
}
