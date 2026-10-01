package com.devuloopers.knet.companion.connectivity.transport

import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AndroidFlowOwnerResolverTest {
    @Test
    fun `unique package becomes authenticated flow metadata`() {
        val resolver = PlatformAndroidFlowOwnerResolver(
            sdkInt = { 35 },
            ownerUid = { 10_123 },
            packagesForUid = { arrayOf("com.example.streaming") },
        )

        val result = assertIs<AndroidFlowOwnerResolution.Verified>(resolver.resolve(flow()))

        assertEquals("com.example.streaming", result.metadata.verifiedSourceApplicationId)
    }

    @Test
    fun `shared UID remains unavailable rather than choosing a package`() {
        val resolver = PlatformAndroidFlowOwnerResolver(
            sdkInt = { 35 },
            ownerUid = { 10_123 },
            packagesForUid = { arrayOf("com.example.one", "com.example.two") },
        )

        val result = assertIs<AndroidFlowOwnerResolution.Unavailable>(resolver.resolve(flow()))

        assertEquals(AndroidFlowAttributionUnavailableReason.SHARED_UID_AMBIGUOUS, result.reason)
    }

    private fun flow(): AndroidOriginalFlowTuple = AndroidOriginalFlowTuple(
        transport = AndroidFlowTransport.TCP,
        source = InetSocketAddress(InetAddress.getByName("10.0.0.2"), 42_000),
        destination = InetSocketAddress(InetAddress.getByName("203.0.113.10"), 443),
    )
}
