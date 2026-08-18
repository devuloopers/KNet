package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end HTTP/1 streaming coverage for provisional responses, trailers, and half-close. */
class HttpOneStreamingSemanticsIntegrationTest {

    @Test
    fun `one hundred continue does not terminate the exchange before the final response`() {
        withOriginAndProxy { origin, proxy ->
            val originCompleted = AtomicBoolean(false)
            val originThread = thread(name = "knet-continue-origin", isDaemon = true) {
                origin.accept().use { connection ->
                    val input = BufferedInputStream(connection.getInputStream())
                    var contentLength = -1
                    while (true) {
                        val line = readAsciiLine(input)
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(':').trim().toInt()
                        }
                    }
                    connection.getOutputStream().write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray())
                    connection.getOutputStream().flush()
                    assertEquals("payload", readExactly(input, contentLength).toString(Charsets.UTF_8))
                    connection.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray()
                    )
                    connection.getOutputStream().flush()
                    originCompleted.set(true)
                }
            }

            Socket().use { client ->
                client.connect(proxy.boundAddress())
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "POST http://$authority/continue HTTP/1.1\r\n" +
                            "Host: $authority\r\nContent-Length: 7\r\n" +
                            "Expect: 100-continue\r\nConnection: close\r\n\r\n"
                    ).toByteArray()
                )
                client.getOutputStream().flush()
                val input = BufferedInputStream(client.getInputStream())
                assertTrue(readAsciiLine(input).startsWith("HTTP/1.1 100"))
                drainHeaders(input)
                client.getOutputStream().write("payload".toByteArray())
                client.getOutputStream().flush()
                assertTrue(readAsciiLine(input).startsWith("HTTP/1.1 200"))
                drainHeaders(input)
                assertEquals("ok", readExactly(input, 2).toString(Charsets.UTF_8))
            }

            originThread.join(1_000L)
            assertTrue(originCompleted.get())
        }
    }

    @Test
    fun `chunked request and response trailers cross the streaming proxy unchanged`() {
        withOriginAndProxy { origin, proxy ->
            val requestTrailerSeen = AtomicBoolean(false)
            val originThread = thread(name = "knet-trailer-origin", isDaemon = true) {
                origin.accept().use { connection ->
                    val input = BufferedInputStream(connection.getInputStream())
                    drainHeaders(input)
                    assertEquals("test", readChunkedBody(input) { name, value ->
                        if (name.equals("X-Request-Trailer", ignoreCase = true) && value == "present") {
                            requestTrailerSeen.set(true)
                        }
                    })
                    connection.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
                                "2\r\nok\r\n0\r\nX-Response-Trailer: present\r\n\r\n"
                        ).toByteArray()
                    )
                    connection.getOutputStream().flush()
                }
            }

            val responseTrailerSeen = AtomicBoolean(false)
            Socket().use { client ->
                client.connect(proxy.boundAddress())
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "POST http://$authority/trailers HTTP/1.1\r\n" +
                            "Host: $authority\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
                            "4\r\ntest\r\n0\r\nX-Request-Trailer: present\r\n\r\n"
                    ).toByteArray()
                )
                client.getOutputStream().flush()
                val input = BufferedInputStream(client.getInputStream())
                assertTrue(readAsciiLine(input).startsWith("HTTP/1.1 200"))
                drainHeaders(input)
                assertEquals("ok", readChunkedBody(input) { name, value ->
                    if (name.equals("X-Response-Trailer", ignoreCase = true) && value == "present") {
                        responseTrailerSeen.set(true)
                    }
                })
            }

            originThread.join(1_000L)
            assertTrue(requestTrailerSeen.get())
            assertTrue(responseTrailerSeen.get())
        }
    }

    @Test
    fun `downstream output half close still allows the final response`() {
        withOriginAndProxy { origin, proxy ->
            val originThread = thread(name = "knet-half-close-origin", isDaemon = true) {
                origin.accept().use { connection ->
                    val input = BufferedInputStream(connection.getInputStream())
                    drainHeaders(input)
                    connection.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\ndone".toByteArray()
                    )
                    connection.getOutputStream().flush()
                }
            }

            Socket().use { client ->
                client.connect(proxy.boundAddress())
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "GET http://$authority/half-close HTTP/1.1\r\n" +
                            "Host: $authority\r\nConnection: close\r\n\r\n"
                    ).toByteArray()
                )
                client.getOutputStream().flush()
                client.shutdownOutput()
                val input = BufferedInputStream(client.getInputStream())
                assertTrue(readAsciiLine(input).startsWith("HTTP/1.1 200"))
                drainHeaders(input)
                assertEquals("done", readExactly(input, 4).toString(Charsets.UTF_8))
            }
            originThread.join(1_000L)
        }
    }

    /** Runs a test with isolated ephemeral origin and proxy listeners. */
    private fun withOriginAndProxy(block: (ServerSocket, KNetProxyServer) -> Unit) {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            ca = CertificateAuthority.generate(),
            certCache = CertificateCache(),
        )
        proxy.start()
        try {
            block(origin, proxy)
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    /** Reads a chunked body and reports trailing header fields. */
    private fun readChunkedBody(
        input: BufferedInputStream,
        onTrailer: (String, String) -> Unit,
    ): String {
        val body = StringBuilder()
        while (true) {
            val size = readAsciiLine(input).substringBefore(';').toInt(16)
            if (size == 0) {
                while (true) {
                    val trailer = readAsciiLine(input)
                    if (trailer.isEmpty()) return body.toString()
                    onTrailer(trailer.substringBefore(':'), trailer.substringAfter(':').trim())
                }
            }
            body.append(readExactly(input, size).toString(Charsets.UTF_8))
            assertEquals("", readAsciiLine(input))
        }
    }

    /** Drains request or response headers through the terminating empty line. */
    private fun drainHeaders(input: BufferedInputStream) {
        while (true) {
            if (readAsciiLine(input).isEmpty()) return
        }
    }

    /** Reads exactly the requested number of bytes. */
    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        require(size >= 0)
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            require(read >= 0) { "Stream ended after $offset of $size bytes." }
            offset += read
        }
        return bytes
    }

    /** Reads one CRLF-terminated ASCII line. */
    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            require(value >= 0) { "Stream ended before a complete HTTP line." }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    /** Reserves an ephemeral loopback port for the proxy. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }
}
