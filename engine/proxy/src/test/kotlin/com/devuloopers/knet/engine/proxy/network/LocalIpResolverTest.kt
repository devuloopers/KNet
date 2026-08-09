package com.devuloopers.knet.engine.proxy.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalIpResolverTest {

    @Test
    fun `getLocalIpAddress returns valid non-empty IP string`() {
        val resolver = LocalIpResolver()
        val ip = resolver.getLocalIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotBlank())
    }

    @Test
    fun `observeLocalIpAddress emits valid IP address string`() = runTest {
        val resolver = LocalIpResolver()
        val emittedIp = resolver.observeLocalIpAddress(100L).first()
        assertNotNull(emittedIp)
        assertTrue(emittedIp.isNotBlank())
    }
}
