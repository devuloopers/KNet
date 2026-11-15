package com.devuloopers.knet.storage.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards the actual bundled native payload for every desktop architecture shipped by the release matrix. */
class BundledSqliteDistributionTest {
    @Test
    fun `bundled driver contains all five desktop native libraries`() {
        val loader = BundledSQLiteDriver::class.java.classLoader
        for (resource in listOf(
            "natives/linux_arm64/libsqliteJni.so",
            "natives/linux_x64/libsqliteJni.so",
            "natives/osx_arm64/libsqliteJni.dylib",
            "natives/osx_x64/libsqliteJni.dylib",
            "natives/windows_x64/sqliteJni.dll",
        )) assertNotNull(loader.getResource(resource), "Missing release database driver: $resource")
    }

    @Test
    fun `current host can load the native driver and execute statements`() {
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.prepare("SELECT 42").use { statement ->
                assertTrue(statement.step())
                assertEquals(42L, statement.getLong(0))
            }
        }
    }
}
