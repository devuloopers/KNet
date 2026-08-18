package com.devuloopers.knet.storage.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Concrete JVM factory responsible for constructing [KNetDatabase] instances using [BundledSQLiteDriver].
 */
object DatabaseFactory {

    /**
     * Creates and configures a thread-safe [KNetDatabase] instance.
     *
     * @param dbFile SQLite database file on disk.
     * @return Configured [KNetDatabase] instance.
     */
    fun create(dbFile: File): KNetDatabase {
        dbFile.parentFile?.mkdirs()

        val builder = Room.databaseBuilder<KNetDatabase>(
            name = dbFile.absolutePath,
            factory = { KNetDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        return builder.build()
    }
}
