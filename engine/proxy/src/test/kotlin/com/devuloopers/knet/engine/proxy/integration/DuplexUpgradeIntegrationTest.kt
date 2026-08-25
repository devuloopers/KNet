package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end verification for protocol-neutral HTTP Upgrade ownership handoff. */
class DuplexUpgradeIntegrationTest {
    /** Verifies arbitrary duplex bytes remain transparent after a 101 response. */
    @Test
    fun `switching response hands both directions to raw relay`() {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val received = CompletableFuture<ByteArray>()
        val originThread = thread(name = "knet-duplex-origin", isDaemon = true) {
            origin.accept().use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                while (readAsciiLine(input).isNotEmpty()) {
                    // Consume the complete request head before switching protocols.
                }
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 101 Switching Protocols\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Upgrade: websocket\r\n\r\n"
                        ).encodeToByteArray(),
                    )
                    flush()
                }
                val payload = input.readExact(PAYLOAD.size)
                received.complete(payload)
                socket.getOutputStream().apply {
                    write(payload)
                    flush()
                }
            }
        }
        val proxyPort = availableLoopbackPort()
        val proxy = KNetProxyServer(
            port = proxyPort,
            serverTlsContextProvider = TestServerTlsContextProvider(
                CertificateAuthority.generate(),
                CertificateCache(),
            ),
        )
        proxy.start()

        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().apply {
                    write(
                        (
                            "GET http://$authority/socket HTTP/1.1\r\n" +
                                "Host: $authority\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Upgrade: websocket\r\n" +
                                "Sec-WebSocket-Version: 13\r\n" +
                                "Sec-WebSocket-Key: dGVzdC1rZXk=\r\n\r\n"
                        ).encodeToByteArray(),
                    )
                    flush()
                }
                val input = BufferedInputStream(client.getInputStream())
                assertEquals("HTTP/1.1 101 Switching Protocols", readAsciiLine(input))
                while (readAsciiLine(input).isNotEmpty()) {
                    // Consume the complete switching response before sending raw bytes.
                }

                client.getOutputStream().apply {
                    write(PAYLOAD)
                    flush()
                }
                assertContentEquals(PAYLOAD, input.readExact(PAYLOAD.size))
            }
            assertContentEquals(PAYLOAD, received.get(5, TimeUnit.SECONDS))
        } finally {
            proxy.stop()
            origin.close()
            originThread.join(1_000L)
        }
    }

    /** Verifies an unrelated or incomplete 101 cannot silently acquire the raw relay pipeline. */
    @Test
    fun `switching response must confirm the requested upgrade token`() {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val originThread = thread(name = "knet-invalid-duplex-origin", isDaemon = true) {
            origin.accept().use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                while (readAsciiLine(input).isNotEmpty()) {
                    // Consume the request head before returning a mismatched switch response.
                }
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 101 Switching Protocols\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Upgrade: unrelated-protocol\r\n\r\n"
                            ).encodeToByteArray(),
                    )
                    flush()
                }
            }
        }
        val proxyPort = availableLoopbackPort()
        val proxy = KNetProxyServer(
            port = proxyPort,
            serverTlsContextProvider = TestServerTlsContextProvider(
                CertificateAuthority.generate(),
                CertificateCache(),
            ),
        )
        proxy.start()

        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().apply {
                    write(
                        (
                            "GET http://$authority/socket HTTP/1.1\r\n" +
                                "Host: $authority\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Upgrade: websocket\r\n\r\n"
                            ).encodeToByteArray(),
                    )
                    flush()
                }

                assertTrue(readAsciiLine(BufferedInputStream(client.getInputStream())).startsWith("HTTP/1.1 502"))
            }
        } finally {
            proxy.stop()
            origin.close()
            originThread.join(1_000L)
        }
    }

    private fun BufferedInputStream.readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            require(read >= 0) { "Connection ended before the requested duplex bytes arrived." }
            offset += read
        }
        return bytes
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val value = input.read()
            require(value >= 0) { "Connection ended before a complete HTTP line." }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    private companion object {
        val PAYLOAD: ByteArray = byteArrayOf(0x01, 0x7f, 0x00, 0x55, 0x2a)
    }
}
