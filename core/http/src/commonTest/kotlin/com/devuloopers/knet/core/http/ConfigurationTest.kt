package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurationTest {

    @Test
    fun testHttpClientConfigurationDefaults() {
        val config = HttpClientConfiguration()

        assertEquals(com.devuloopers.knet.domain.clientNetwork.model.NetworkTimeouts.DEFAULT_TIMEOUT_MS, config.timeoutMillis)
        assertEquals(com.devuloopers.knet.domain.clientNetwork.model.NetworkTimeouts.DEFAULT_TIMEOUT_MS, config.connectTimeoutMillis)
        assertEquals(0, config.retryCount)
        assertTrue(config.followRedirects)
        assertTrue(config.verifySsl)
        assertTrue(config.useCookies)
    }

    @Test
    fun testHttpClientConfigurationCustomValues() {
        val config = HttpClientConfiguration(
            timeoutMillis = 60_000L,
            retryCount = 5,
            followRedirects = false,
            useCookies = false
        )

        assertEquals(60_000L, config.timeoutMillis)
        assertEquals(5, config.retryCount)
        assertEquals(false, config.followRedirects)
        assertEquals(false, config.useCookies)
    }
}
