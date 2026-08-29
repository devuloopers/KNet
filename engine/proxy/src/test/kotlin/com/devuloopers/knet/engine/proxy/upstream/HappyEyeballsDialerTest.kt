package com.devuloopers.knet.engine.proxy.upstream

import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import io.netty.channel.nio.NioEventLoopGroup
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HappyEyeballsDialerTest {

    @Test
    fun `unreachable ipv6 candidate falls back to reachable ipv4 candidate`() {
        val ipv4Server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val group = NioEventLoopGroup(1)
        try {
            val connected = HappyEyeballsDialer(testPolicy()).connect(
                eventLoop = group.next(),
                route = route(
                    port = ipv4Server.localPort,
                    addresses = listOf("::1", "127.0.0.1"),
                ),
            ).get(3, TimeUnit.SECONDS)

            assertEquals(UpstreamAddressFamily.IPV4, connected.candidate.family)
            assertTrue(connected.channel.isActive)
            connected.channel.close().syncUninterruptibly()
        } finally {
            ipv4Server.close()
            group.shutdownGracefully().syncUninterruptibly()
        }
    }

    @Test
    fun `all candidate failure retains both address families in one bounded error`() {
        val unusedPort = ServerSocket(0).use(ServerSocket::getLocalPort)
        val group = NioEventLoopGroup(1)
        try {
            val failure = assertFailsWith<ExecutionException> {
                HappyEyeballsDialer(testPolicy()).connect(
                    eventLoop = group.next(),
                    route = route(
                        port = unusedPort,
                        addresses = listOf("::1", "127.0.0.1"),
                    ),
                ).get(3, TimeUnit.SECONDS)
            }.cause as UpstreamConnectException

            assertEquals(
                setOf(UpstreamAddressFamily.IPV6, UpstreamAddressFamily.IPV4),
                failure.failures.map(UpstreamCandidateFailure::family).toSet(),
            )
            assertTrue(failure.message.orEmpty().contains("All addresses failed"))
        } finally {
            group.shutdownGracefully().syncUninterruptibly()
        }
    }

    private fun route(port: Int, addresses: List<String>): ResolvedUpstreamRoute = ResolvedUpstreamRoute(
        host = "origin.test",
        port = port,
        candidates = addresses.map { value ->
            val address = InetAddress.getByName(value)
            UpstreamAddressCandidate(
                socketAddress = InetSocketAddress(address, port),
                family = if (address.address.size == 16) {
                    UpstreamAddressFamily.IPV6
                } else {
                    UpstreamAddressFamily.IPV4
                },
            )
        },
    )

    private fun testPolicy(): KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(
        connectTimeoutMillis = 1_000L,
        happyEyeballsDelayMillis = 25L,
    )
}
