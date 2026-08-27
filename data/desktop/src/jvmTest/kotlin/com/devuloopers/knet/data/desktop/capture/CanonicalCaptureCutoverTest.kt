package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.proxy.ProxyBindingConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyConnectionLimits
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyStartResult
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.proxy.ProxyTimeoutPolicy
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.contract.traffic.CapturePauseResult
import com.devuloopers.knet.application.contract.traffic.CaptureResumeResult
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.data.desktop.proxy.repository.DesktopProxyRuntimeAdapter
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Proves composition selects one persistence authority for a new proxy capture session. */
class CanonicalCaptureCutoverTest {

    /** Verifies clear swaps the active writer, deletes the old session, and keeps capture running. */
    @Test
    fun `active canonical clear rotates session before deleting history`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-rotate-clear-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val breakpointCoordinator = BreakpointCoordinator()
        val repository = DesktopProxyRuntimeAdapter(
            proxyRuntimeRepository = ProxyRuntimeRepository(
                certificateAuthority = CertificateAuthority.generate(),
                certificateCache = CertificateCache(),
                breakpointGate = breakpointCoordinator,
            ),
            canonicalCaptureSessionFactory = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore),
            breakpointCaptureAvailability = breakpointCoordinator,
        )
        val trafficRepository = DesktopTrafficMaintenanceAdapter(
            database = database,
            canonicalBodyStore = bodyStore,
        )
        try {
            assertIs<ProxyStartResult.Running>(repository.start(configuration(availableLoopbackPort())))
            val oldSessionId = assertNotNull(database.canonicalCaptureDao().observeLatestSessionId().first())

            val preparation = ClearTrafficHistoryUseCase(repository, trafficRepository).execute()

            assertEquals(CaptureClearPreparation.CANONICAL_SESSION_ROTATED, preparation)
            assertNull(database.canonicalCaptureDao().getSession(oldSessionId))
            assertEquals(1, database.canonicalCaptureDao().countActiveSessions())
            val newSessionId = assertNotNull(database.canonicalCaptureDao().observeLatestSessionId().first())
            assertTrue(newSessionId != oldSessionId)

            repository.stop(ProxyStopReason.USER_REQUEST)

            assertEquals("CLOSED", database.canonicalCaptureDao().getSession(newSessionId)?.state)
            assertEquals(0, database.canonicalCaptureDao().countActiveSessions())
        } finally {
            repository.close()
            database.close()
            root.deleteRecursively()
        }
    }

    /** Verifies capture pause/resume leaves the bound runtime unchanged and rotates only writers. */
    @Test
    fun `capture pause and resume preserve proxy runtime`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-pause-resume-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val breakpointCoordinator = BreakpointCoordinator()
        val repository = DesktopProxyRuntimeAdapter(
            proxyRuntimeRepository = ProxyRuntimeRepository(
                certificateAuthority = CertificateAuthority.generate(),
                certificateCache = CertificateCache(),
                breakpointGate = breakpointCoordinator,
            ),
            canonicalCaptureSessionFactory = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore),
            breakpointCaptureAvailability = breakpointCoordinator,
        )
        try {
            val started = assertIs<ProxyStartResult.Running>(repository.start(configuration(availableLoopbackPort())))
            val firstSession = assertIs<CaptureSessionState.Capturing>(repository.captureState.value).sessionId

            assertEquals(CapturePauseResult.PAUSED, repository.pause())
            assertIs<CaptureSessionState.Paused>(repository.captureState.value)
            val stillRunning = assertIs<com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState.Running>(
                repository.state.value,
            )
            assertEquals(started.handle.runtimeId, stillRunning.handle.runtimeId)
            withContext(Dispatchers.IO) {
                withTimeout(5_000L) {
                    while (database.canonicalCaptureDao().countActiveSessions() != 0) delay(10L)
                }
            }

            val resumed = assertIs<CaptureResumeResult.Capturing>(repository.resume())
            assertTrue(resumed.sessionId != firstSession)
            assertEquals(1, database.canonicalCaptureDao().countActiveSessions())
            val afterResume = assertIs<com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState.Running>(
                repository.state.value,
            )
            assertEquals(started.handle.runtimeId, afterResume.handle.runtimeId)
        } finally {
            repository.stop(ProxyStopReason.APPLICATION_SHUTDOWN)
            repository.close()
            database.close()
            root.deleteRecursively()
        }
    }

    /** Verifies canonical selection writes indexed metadata and opaque bounded bodies. */
    @Test
    fun `canonical selection writes queryable metadata and bounded bodies`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-cutover-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8_080, startedAtEpochMillis = 1_000L)

        try {
            val requestBody = ByteArray(70_000) { index -> (index % 251).toByte() }
            val responseBody = ByteArray(130_000) { index -> (index % 239).toByte() }
            session.recordTestProxyExchange(
                exchangeId = ExchangeId(EXCHANGE_ID),
                request = RequestHead(
                    method = HttpMethod.fromToken("POST"),
                    target = RequestTarget.Absolute(
                        scheme = HttpScheme.fromToken("https"),
                        authority = Authority("example.test", 8443),
                        pathAndQuery = "/v1/items?limit=2",
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = listOf(
                        HeaderField(HeaderName("Content-Type"), "application/octet-stream"),
                        HeaderField(HeaderName("X-Repeated"), "first"),
                        HeaderField(HeaderName("X-Repeated"), "second"),
                    ),
                ),
                requestBody = requestBody,
                response = ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(201),
                    reasonPhrase = "Created",
                    headers = listOf(HeaderField(HeaderName("Content-Type"), "application/octet-stream")),
                ),
                responseBody = responseBody,
                outcome = ExchangeTerminalOutcome.Completed,
                timings = ExchangeTimings(
                    dnsMillis = 1L,
                    connectMillis = 2L,
                    tlsMillis = 3L,
                    firstByteMillis = 4L,
                    downloadMillis = 5L,
                    totalMillis = 25L,
                ),
                startedAtEpochMillis = 1_000L,
                completedAtEpochMillis = 1_025L,
            )
            session.close()

            val exchange = assertNotNull(database.canonicalCaptureDao().getExchange(EXCHANGE_ID))
            assertEquals("COMPLETED", exchange.state)
            assertEquals(201, exchange.responseStatusCode)
            assertEquals(25L, exchange.timingTotalMillis)
            assertEquals(8443, exchange.port)
            val requestBodyId = assertNotNull(exchange.requestBodyId)
            val responseBodyId = assertNotNull(exchange.responseBodyId)
            assertEquals(requestBody.size.toLong(), database.canonicalCaptureDao().getBody(requestBodyId)?.storedBytes)
            assertEquals(responseBody.size.toLong(), database.canonicalCaptureDao().getBody(responseBodyId)?.storedBytes)
            assertEquals(
                requestBody.toList(),
                bodyStore.readBody(BodyId(requestBodyId), BodyRange(0L, requestBody.size)).copyBytes().toList(),
            )
            assertEquals("CLOSED", database.canonicalCaptureDao().getSession(exchange.sessionId)?.state)
            assertEquals("CLOSED", database.canonicalCaptureDao().getConnection(exchange.connectionId)?.state)

        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }

    /** Creates the safe loopback runtime configuration used by the cutover scenario. */
    private fun configuration(port: Int): ProxyRuntimeConfiguration = ProxyRuntimeConfiguration(
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

    /** Reserves and releases a loopback port for the proxy listener. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    private companion object {
        private const val EXCHANGE_ID = "canonical-cutover-exchange"
    }
}
