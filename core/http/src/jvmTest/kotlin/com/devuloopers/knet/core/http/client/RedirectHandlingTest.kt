package com.devuloopers.knet.core.http.client

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class RedirectHandlingTest {

    @Test
    fun testRedirectFollow() = runBlocking {
        val client = KNetApiClient()
        val result = client.execute("https://httpbin.org/redirect/1", method = "GET")

        assertNotNull(result)
        client.close()
    }
}
