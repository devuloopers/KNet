package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
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
