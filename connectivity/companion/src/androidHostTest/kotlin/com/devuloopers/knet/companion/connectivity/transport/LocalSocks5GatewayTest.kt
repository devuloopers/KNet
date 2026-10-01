package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSocks5GatewayTest {
    @Test
    fun `gateway negotiates no-auth and accepts a domain CONNECT without exposing credentials`() {
        val gateway = LocalSocks5Gateway(
            transport = AndroidCompanionProxyTransport(),
            protector = AllowAllProtector,
            unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
        )
        gateway.start()
        try {
            Socket().use { client ->
                client.soTimeout = 5_000
                client.connect(InetSocketAddress("127.0.0.1", gateway.port))
                client.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertContentEquals(byteArrayOf(5, 0), client.getInputStream().readExactlyForTest(2))

                val host = "example.test".encodeToByteArray()
                client.getOutputStream().write(
                    byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, 0xBB.toByte()),
                )
                val reply = client.getInputStream().readExactlyForTest(10)
                assertEquals(0, reply[1].toInt())

                client.getOutputStream().write(byteArrayOf(0x16, 0x03, 0x03, 0))
                client.getOutputStream().flush()
                val closed = runCatching { client.getInputStream().read() }.getOrDefault(-1)
                assertEquals(-1, closed)
            }
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `gateway rejects SOCKS commands outside CONNECT and UDP associate`() {
        val gateway = LocalSocks5Gateway(
            transport = AndroidCompanionProxyTransport(),
            protector = AllowAllProtector,
            unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
        )
        gateway.start()
        try {
            Socket("127.0.0.1", gateway.port).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write(byteArrayOf(5, 1, 0))
                client.getInputStream().readExactlyForTest(2)
                client.getOutputStream().write(byteArrayOf(5, 2, 0, 1, 127, 0, 0, 1, 0, 80))
                val reply = client.getInputStream().readExactlyForTest(10)
                assertEquals(7, reply[1].toInt())
            }
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `gateway bypasses the inspecting proxy for protected DNS TCP control traffic`() {
        val target = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val protectedBeforeConnect = AtomicBoolean(false)
        val targetExecutor = Executors.newSingleThreadExecutor()
        val targetResult = targetExecutor.submit {
            target.accept().use { upstream ->
                assertContentEquals("ping".encodeToByteArray(), upstream.getInputStream().readExactlyForTest(4))
                upstream.getOutputStream().write("pong".encodeToByteArray())
                upstream.getOutputStream().flush()
            }
        }
        val gateway = LocalSocks5Gateway(
            transport = AndroidCompanionProxyTransport(),
            protector = object : AndroidSocketProtector {
                override fun protect(socket: Socket): Boolean {
                    protectedBeforeConnect.set(!socket.isConnected)
                    return true
                }

                override fun protect(socket: DatagramSocket): Boolean = true
            },
            unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
            directTcpPorts = setOf(target.localPort),
        )
        gateway.start()
        try {
            Socket("127.0.0.1", gateway.port).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertContentEquals(byteArrayOf(5, 0), client.getInputStream().readExactlyForTest(2))

                val port = target.localPort
                client.getOutputStream().write(
                    byteArrayOf(
                        5,
                        1,
                        0,
                        1,
                        127,
                        0,
                        0,
                        1,
                        (port ushr 8).toByte(),
                        port.toByte(),
                    ),
                )
                assertEquals(0, client.getInputStream().readExactlyForTest(10)[1].toInt())
                client.getOutputStream().write("ping".encodeToByteArray())
                client.getOutputStream().flush()
                assertContentEquals("pong".encodeToByteArray(), client.getInputStream().readExactlyForTest(4))
            }
            assertTrue(protectedBeforeConnect.get())
            targetResult.get(5, TimeUnit.SECONDS)
        } finally {
            gateway.close()
            target.close()
            targetExecutor.shutdownNow()
        }
    }

    @Test
    fun `enabled packet conditions forward non-DNS UDP with a stable protected flow`() {
        val echo = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        val echoExecutor = Executors.newSingleThreadExecutor()
        val sourcePorts = mutableListOf<Int>()
        val echoResult = echoExecutor.submit {
            repeat(2) {
                val bytes = ByteArray(128)
                val received = DatagramPacket(bytes, bytes.size)
                echo.receive(received)
                sourcePorts += received.port
                echo.send(DatagramPacket(received.data, received.length, received.address, received.port))
            }
        }
        val gateway = LocalSocks5Gateway(
            transport = AndroidCompanionProxyTransport(),
            protector = AllowAllProtector,
            unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
            packetConditions = PacketConditionConfiguration(enabled = true),
        )
        gateway.start()
        try {
            Socket("127.0.0.1", gateway.port).use { control ->
                control.soTimeout = 5_000
                control.getOutputStream().write(byteArrayOf(5, 1, 0))
                assertContentEquals(byteArrayOf(5, 0), control.getInputStream().readExactlyForTest(2))
                control.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
                val associateReply = control.getInputStream().readExactlyForTest(10)
                assertEquals(0, associateReply[1].toInt())
                val relayPort = ((associateReply[8].toInt() and 0xff) shl 8) or
                    (associateReply[9].toInt() and 0xff)

                DatagramSocket().use { udpClient ->
                    udpClient.soTimeout = 5_000
                    repeat(2) { index ->
                        val payload = "packet-$index".encodeToByteArray()
                        val request = byteArrayOf(
                            0, 0, 0, 1, 127, 0, 0, 1,
                            (echo.localPort ushr 8).toByte(), echo.localPort.toByte(),
                        ) + payload
                        udpClient.send(
                            DatagramPacket(request, request.size, InetSocketAddress("127.0.0.1", relayPort)),
                        )
                        val responseBytes = ByteArray(256)
                        val response = DatagramPacket(responseBytes, responseBytes.size)
                        udpClient.receive(response)
                        assertContentEquals(payload, response.data.copyOfRange(10, response.length))
                    }
                }
            }
            echoResult.get(5, TimeUnit.SECONDS)
            assertEquals(1, sourcePorts.distinct().size)
        } finally {
            gateway.close()
            echo.close()
            echoExecutor.shutdownNow()
        }
    }

    @Test
    fun `UDP destination registry evicts least recently used and idle sockets`() {
        var now = 0L
        val registry = UdpUpstreamFlowRegistry(
            maximumSize = 1,
            idleTimeoutNanos = 100L,
            nanoTime = { now },
        )
        val first = registry.getOrCreate(SocksDestination("127.0.0.1", 10_001)) {
            UdpUpstreamFlow(SocksDestination("127.0.0.1", 10_001), DatagramSocket())
        }!!.flow
        val second = registry.getOrCreate(SocksDestination("127.0.0.1", 10_002)) {
            UdpUpstreamFlow(SocksDestination("127.0.0.1", 10_002), DatagramSocket())
        }!!.flow

        assertTrue(first.socket.isClosed)
        assertEquals(1, registry.size())

        now = 100L
        val third = registry.getOrCreate(SocksDestination("127.0.0.1", 10_003)) {
            UdpUpstreamFlow(SocksDestination("127.0.0.1", 10_003), DatagramSocket())
        }!!.flow

        assertTrue(second.socket.isClosed)
        assertEquals(1, registry.size())
        registry.closeAll()
        assertTrue(third.socket.isClosed)
    }

    private object AllowAllProtector : AndroidSocketProtector {
        override fun protect(socket: Socket): Boolean = true
        override fun protect(socket: DatagramSocket): Boolean = true
    }
}

private fun java.io.InputStream.readExactlyForTest(size: Int): ByteArray {
    val bytes = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val count = read(bytes, offset, size - offset)
        check(count >= 0) { "SOCKS stream closed early." }
        offset += count
    }
    return bytes
}
