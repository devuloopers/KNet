package com.devuloopers.knet.data.desktop

import com.devuloopers.knet.data.desktop.core.KNetCoreRepository
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Migration regression test ensuring core components exist in :data:desktop.
 */
class MigrationRegressionTest {

    @Test
    fun testCoreRepositoryClassExists() {
        assertNotNull(KNetCoreRepository::class)
    }
}
