package com.devuloopers.knet.data.desktop.proxy.repository

import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.proxy.ProxyBindingConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyConnectionLimits
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyTimeoutPolicy
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Tests the production streaming capture path and safe exposure policy. */
class ProxyEngineStreamingCaptureTest {

    /** Verifies one real proxy stream owns one completed canonical exchange. */
    @Test
    fun `real proxy stream produces completed canonical exchange`() = runTest {
        val fixture = createFixture()
        val origin = ServerSocket()
        origin.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        val originThread = thread(name = "knet-canonical-stream-origin", isDaemon = true) {
            origin.accept().use { connection ->
                val reader = connection.getInputStream().bufferedReader()
                while (reader.readLine().isNotEmpty()) {
                    // Drain the complete request head.
                }
                connection.getOutputStream().write(
                    "HTTP/1.1 201 Created\r\nContent-Length: 4\r\nConnection: close\r\n\r\nbody".toByteArray()
                )
                connection.getOutputStream().flush()
            }
        }
        try {
            val proxyPort = availableLoopbackPort()
            assertIs<ProxyStartResult.Running>(fixture.repository.start(loopbackConfiguration(proxyPort)))
            Socket().use { client ->
                client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "GET http://$authority/stream HTTP/1.1\r\n" +
                            "Host: $authority\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray()
                )
                client.getOutputStream().flush()
                while (client.getInputStream().read() >= 0) {
                    // Drain the complete response so terminal capture is observed.
                }
            }
            fixture.repository.stop(ProxyStopReason.USER_REQUEST)

            val sessionId = fixture.database.canonicalCaptureDao().observeLatestSessionId().first()
            val stored = assertNotNull(fixture.database.canonicalCaptureDao().getNewestExchangePage(
                sessionId = requireNotNull(sessionId),
                cursorTimestamp = null,
                cursorId = null,
                searchPattern = null,
                filterMethods = 0,
                methods = emptyList(),
                filterStatuses = 0,
                statuses = emptyList(),
                filterSchemes = 0,
                schemes = emptyList(),
                filterProtocols = 0,
                protocols = emptyList(),
                limit = 1,
            ).singleOrNull())
            assertFalse(stored.id.isBlank())
            assertEquals(KNetProxyServer.DEFAULT_BIND_HOST, stored.host)
            assertEquals(origin.localPort, stored.port)
            assertEquals(201, stored.responseStatusCode)
            assertEquals("COMPLETED", stored.state)
        } finally {
            origin.close()
            originThread.join(1_000L)
            fixture.close()
        }
    }

    /** Verifies storage clear does not close an already-connected downstream proxy socket. */
    @Test
    fun `traffic clear preserves client socket and captures its next exchange`() = runTest {
        val fixture = createFixture()
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val originThread = thread(name = "knet-clear-keepalive-origin", isDaemon = true) {
            repeat(2) {
                origin.accept().use { connection ->
                    val reader = connection.getInputStream().bufferedReader()
                    while (reader.readLine().isNotEmpty()) {
                        // Drain one request head; the proxy opens one upstream channel per exchange.
                    }
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: keep-alive\r\n\r\nok".toByteArray())
                        flush()
                    }
                }
            }
        }
        try {
            val proxyPort = availableLoopbackPort()
            assertIs<ProxyStartResult.Running>(fixture.repository.start(loopbackConfiguration(proxyPort)))
            Socket().use { client ->
                client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                client.soTimeout = 5_000
                val responses = client.getInputStream().bufferedReader()
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"

                writeProxyRequest(client, authority, "/before-clear", close = false)
                assertEquals("HTTP/1.1 200 OK", readResponse(responses))

                ClearTrafficHistoryUseCase(
                    captureSessionControl = fixture.repository,
                    trafficMaintenance = DesktopTrafficMaintenanceAdapter(fixture.database, fixture.bodyStore),
                ).execute()

                writeProxyRequest(client, authority, "/after-clear", close = true)
                assertEquals("HTTP/1.1 200 OK", readResponse(responses))
            }
            fixture.repository.stop(ProxyStopReason.USER_REQUEST)

            val sessionId = assertNotNull(fixture.database.canonicalCaptureDao().observeLatestSessionId().first())
            val stored = fixture.database.canonicalCaptureDao().getNewestExchangePage(
                sessionId = sessionId,
                cursorTimestamp = null,
                cursorId = null,
                searchPattern = null,
                filterMethods = 0,
                methods = emptyList(),
                filterStatuses = 0,
                statuses = emptyList(),
                filterSchemes = 0,
                schemes = emptyList(),
                filterProtocols = 0,
                protocols = emptyList(),
                limit = 10,
            )
            assertEquals(listOf("/after-clear"), stored.map { it.pathAndQuery })
        } finally {
            origin.close()
            originThread.join(1_000L)
            fixture.close()
        }
    }

    /** Verifies LAN exposure is rejected before a listener or capture session is created. */
    @Test
    fun `unauthenticated lan binding is rejected before listener startup`() = runTest {
        val fixture = createFixture()
        try {
            val result = fixture.repository.start(
                ProxyRuntimeConfiguration(
                    bindings = listOf(
                        ProxyBindingConfiguration(
                            host = "0.0.0.0",
                            port = 8080,
                            scope = ProxyEndpointScope.LAN,
                        )
                    ),
                    verifyUpstreamTls = true,
                    timeouts = ProxyTimeoutPolicy(1_000L, 1_000L, 1_000L, 1_000L, 1_000L),
                    connectionLimits = ProxyConnectionLimits(10, 5, 10),
                )
            )

            assertIs<ProxyStartResult.Failed>(result)
            assertFalse(fixture.runtime.isRunning())
            assertEquals(0, fixture.database.canonicalCaptureDao().countActiveSessions())
        } finally {
            fixture.close()
        }
    }

    /** Creates an isolated Room, body store, certificate, runtime, and canonical writer fixture. */
    private fun createFixture(): Fixture {
        val root = Files.createTempDirectory("knet-canonical-callback-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val runtime = ProxyRuntimeRepository(
            certificateAuthority = CertificateAuthority.generate(),
            certificateCache = CertificateCache(),
        )
        val repository = DesktopProxyRuntimeAdapter(
            proxyRuntimeRepository = runtime,
            canonicalCaptureSessionFactory = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore),
            breakpointCaptureAvailability = BreakpointCoordinator(),
        )
        return Fixture(root, database, bodyStore, runtime, repository)
    }

    /** Writes one absolute-form HTTP request through an already-open proxy connection. */
    private fun writeProxyRequest(client: Socket, authority: String, path: String, close: Boolean) {
        client.getOutputStream().apply {
            write(
                (
                    "GET http://$authority$path HTTP/1.1\r\n" +
                        "Host: $authority\r\n" +
                        "Connection: ${if (close) "close" else "keep-alive"}\r\n\r\n"
                ).toByteArray()
            )
            flush()
        }
    }

    /** Reads one fixed-length response while retaining reader buffering for the next response. */
    private fun readResponse(reader: java.io.BufferedReader): String {
        val status = reader.readLine()
        var contentLength = 0
        while (true) {
            val line = reader.readLine()
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toInt()
            }
        }
        repeat(contentLength) { check(reader.read() >= 0) }
        return status
    }

    /** Creates a safe loopback runtime configuration. */
    private fun loopbackConfiguration(port: Int): ProxyRuntimeConfiguration = ProxyRuntimeConfiguration(
        bindings = listOf(
            ProxyBindingConfiguration(
                host = KNetProxyServer.DEFAULT_BIND_HOST,
                port = port,
                scope = ProxyEndpointScope.LOOPBACK,
            )
        ),
        verifyUpstreamTls = true,
        timeouts = ProxyTimeoutPolicy(5_000L, 5_000L, 5_000L, 5_000L, 5_000L),
        connectionLimits = ProxyConnectionLimits(32, 16, 32),
    )

    /** Reserves and releases a loopback port. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    /** Isolated resources used by one callback scenario. */
    private data class Fixture(
        val root: java.io.File,
        val database: com.devuloopers.knet.storage.database.KNetDatabase,
        val bodyStore: FileBodyStore,
        val runtime: ProxyRuntimeRepository,
        val repository: DesktopProxyRuntimeAdapter,
    ) {
        /** Closes runtime/storage and deletes test files. */
        fun close() {
            repository.close()
            database.close()
            root.deleteRecursively()
        }
    }
}
