package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationIntegrationTest {

    @Test
    fun testBearerAuthenticationIntegration() = runBlocking {
        val client = KNetApiClient()
        val result = client.executeDetailed(
            url = "https://httpbin.org/bearer",
            method = HttpMethod.GET,
            auth = ApiRequestAuth.Bearer("integration_secret_token")
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
        client.close()
    }
}
