package com.devuloopers.knet.storage

import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * JVM regression test ensuring core storage components exist.
 */
class MigrationRegressionTest {

    @Test
    fun testDatabaseClassesExist() {
        assertNotNull(KNetDatabase::class)
        assertNotNull(DatabaseFactory::class)
    }
}
