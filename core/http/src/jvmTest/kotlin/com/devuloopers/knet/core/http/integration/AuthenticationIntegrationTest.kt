package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.AuthType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationIntegrationTest {

    @Test
    fun testBearerAuthenticationIntegration() = runBlocking {
        val client = KNetApiClient()
        val result = client.execute(
            url = "https://httpbin.org/bearer",
            method = "GET",
            authType = AuthType.BEARER_TOKEN,
            authToken = "integration_secret_token"
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
        client.close()
    }
}
