package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end regression test for ordered HTTP/1 responses on one downstream connection. */
class HttpOneOrderingIntegrationTest {

    /** Verifies a fast second origin response cannot overtake a delayed first response. */
    @Test
    fun `pipelined responses preserve request order`() {
        val origin = ServerSocket()
        origin.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        val receivedPaths = CopyOnWriteArrayList<String>()
        val originCompleted = CountDownLatch(2)
        val originThread = thread(name = "knet-ordering-origin", isDaemon = true) {
            repeat(2) {
                val connection = origin.accept()
                thread(name = "knet-ordering-response", isDaemon = true) {
                    connection.use { socket ->
                        val requestLine = socket.getInputStream().bufferedReader().readLine()
                        val path = requestLine.split(' ')[1]
                        receivedPaths += path
                        if (path == "/first") Thread.sleep(300L)
                        val body = path.removePrefix("/").toByteArray()
                        socket.getOutputStream().write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: ${body.size}\r\n" +
                                    "Connection: keep-alive\r\n\r\n"
                            ).toByteArray()
                        )
                        socket.getOutputStream().write(body)
                        socket.getOutputStream().flush()
                    }
                    originCompleted.countDown()
                }
            }
        }

        val proxyPort = availableLoopbackPort()
        val proxy = KNetProxyServer(
            port = proxyPort,
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
        )
        proxy.start()

        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                client.soTimeout = 5_000
                val originAuthority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                val pipelinedRequests =
                    "GET http://$originAuthority/first HTTP/1.1\r\n" +
                        "Host: $originAuthority\r\nConnection: keep-alive\r\n\r\n" +
                        "GET http://$originAuthority/second HTTP/1.1\r\n" +
                        "Host: $originAuthority\r\nConnection: close\r\n\r\n"
                client.getOutputStream().write(pipelinedRequests.toByteArray())
                client.getOutputStream().flush()

                val input = BufferedInputStream(client.getInputStream())
                assertEquals("first", readHttpResponseBody(input))
                assertEquals("second", readHttpResponseBody(input))
            }

            assertTrue(originCompleted.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("/first", "/second"), receivedPaths.toList())
        } finally {
            proxy.stop()
            origin.close()
            originThread.join(1_000L)
        }
    }

    /** Reads one content-length-delimited HTTP response body. */
    private fun readHttpResponseBody(input: BufferedInputStream): String {
        val statusLine = readAsciiLine(input)
        require(statusLine.startsWith("HTTP/1.1 200")) { "Unexpected proxy response: $statusLine" }
        var contentLength = -1
        while (true) {
            val headerLine = readAsciiLine(input)
            if (headerLine.isEmpty()) break
            if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = headerLine.substringAfter(':').trim().toInt()
            }
        }
        require(contentLength >= 0) { "Response did not contain Content-Length." }
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val read = input.read(body, offset, body.size - offset)
            require(read >= 0) { "Response ended before its declared body length." }
            offset += read
        }
        return body.toString(Charsets.UTF_8)
    }

    /** Reads one CRLF-terminated ASCII protocol line. */
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

    /** Reserves and releases a loopback port for the proxy listener. */
    private fun availableLoopbackPort(): Int {
        return ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            socket.localPort
        }
    }
}
