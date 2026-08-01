package com.devuloopers.knet.core.http.migration

import com.devuloopers.knet.core.http.client.KNetApiClient
import org.junit.Assert.assertNotNull
import org.junit.Test

class MigrationRegressionTest {

    @Test
    fun testPublicApiCompatibilityAfterRelocation() {
        val client = KNetApiClient()
        assertNotNull(client)
        client.close()
    }
}
