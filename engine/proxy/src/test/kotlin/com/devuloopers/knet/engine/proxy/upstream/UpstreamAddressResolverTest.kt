package com.devuloopers.knet.engine.proxy.upstream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpstreamAddressResolverTest {

    @Test
    fun `windows style ipv6 first results put ipv4 in the second race slot`() {
        val ipv6First = listOf(
            ipv6(1),
            ipv6(2),
            ipv6(3),
            ipv4(127, 0, 0, 1),
        )

        assertEquals(
            listOf(
                UpstreamAddressFamily.IPV6,
                UpstreamAddressFamily.IPV4,
                UpstreamAddressFamily.IPV6,
                UpstreamAddressFamily.IPV6,
            ),
            interleaveAddressFamilies(ipv6First).map { address -> address.family() },
        )
    }

    @Test
    fun `resolver removes duplicate addresses and applies the configured candidate bound`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val firstIpv6 = ipv6(1)
            val route = CoroutineUpstreamAddressResolver(
                scope = scope,
                maximumCandidates = 2,
                lookup = { arrayOf(firstIpv6, firstIpv6, ipv4(127, 0, 0, 1), ipv6(2)) },
            ).resolve("origin.test", 443).get(2, TimeUnit.SECONDS)

            assertEquals("origin.test", route.host)
            assertEquals(443, route.port)
            assertEquals(
                listOf(UpstreamAddressFamily.IPV6, UpstreamAddressFamily.IPV4),
                route.candidates.map(UpstreamAddressCandidate::family),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `route rejects an empty DNS answer`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val future = CoroutineUpstreamAddressResolver(
                scope = scope,
                maximumCandidates = 4,
                lookup = { emptyArray() },
            ).resolve("empty.test", 80)

            assertFailsWith<java.util.concurrent.ExecutionException> {
                future.get(2, TimeUnit.SECONDS)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun InetAddress.family(): UpstreamAddressFamily = when (address.size) {
        16 -> UpstreamAddressFamily.IPV6
        else -> UpstreamAddressFamily.IPV4
    }

    private fun ipv4(a: Int, b: Int, c: Int, d: Int): InetAddress = InetAddress.getByAddress(
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()),
    )

    private fun ipv6(lastByte: Int): InetAddress = InetAddress.getByAddress(
        ByteArray(16).also { bytes ->
            bytes[0] = 0x20
            bytes[1] = 0x01
            bytes[15] = lastByte.toByte()
        },
    )
}
