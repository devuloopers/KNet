package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class RedirectHandlingTest {

    @Test
    fun testRedirectFollow() = runBlocking {
        val client = KNetApiClient()
        val result = client.executeDetailed("https://httpbin.org/redirect/1", method = HttpMethod.GET)

        assertNotNull(result)
        client.close()
    }
}
