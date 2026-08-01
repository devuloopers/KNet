package com.devuloopers.knet.core.http.routing

import java.net.ConnectException
import java.net.SocketException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRoutingStrategyTest {

    private val strategy = DefaultProxyRoutingStrategy()

    @Test
    fun testShouldAttemptProxyWithValidPort() {
        assertTrue(strategy.shouldAttemptProxy(8080))
        assertTrue(strategy.shouldAttemptProxy(8888))
    }

    @Test
    fun testShouldAttemptProxyWithInvalidPort() {
        assertFalse(strategy.shouldAttemptProxy(null))
        assertFalse(strategy.shouldAttemptProxy(0))
        assertFalse(strategy.shouldAttemptProxy(-1))
    }

    @Test
    fun testIsProxyConnectionFailureWithConnectionErrors() {
        val connectExc = ConnectException("Connection refused")
        val socketExc = SocketException("Connection reset")
        val runtimeExc = RuntimeException("failed to connect to 127.0.0.1")

        assertTrue(strategy.isProxyConnectionFailure(connectExc, 8080))
        assertTrue(strategy.isProxyConnectionFailure(socketExc, 8080))
        assertTrue(strategy.isProxyConnectionFailure(runtimeExc, 8080))
    }

    @Test
    fun testIsProxyConnectionFailureWithNonConnectionErrors() {
        val illegalArgExc = IllegalArgumentException("Invalid URL parameter")
        val nullPortExc = ConnectException("Connection refused")

        assertFalse(strategy.isProxyConnectionFailure(illegalArgExc, 8080))
        assertFalse(strategy.isProxyConnectionFailure(nullPortExc, null))
    }
}
