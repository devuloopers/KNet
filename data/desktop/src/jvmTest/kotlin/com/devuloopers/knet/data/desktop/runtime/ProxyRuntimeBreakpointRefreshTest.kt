package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** End-to-end coverage for breakpoint rules activated after a proxy client is already connected. */
class ProxyRuntimeBreakpointRefreshTest {

    /** Verifies a newly enabled request rule applies to an existing streaming client pipeline. */
    @Test
    fun `request breakpoint added after client connects pauses the next request`() {
        verifyDynamicRuleActivation(BreakpointPhase.REQUEST)
    }

    /** Verifies a newly enabled response rule installs both request context and response aggregation. */
    @Test
    fun `response breakpoint added after client connects pauses the next response`() {
        verifyDynamicRuleActivation(BreakpointPhase.RESPONSE)
    }

    /** Runs one real loopback proxy scenario for the supplied breakpoint [phase]. */
    private fun verifyDynamicRuleActivation(phase: BreakpointPhase) = runBlocking {
        val origin = SingleResponseOrigin()
        val coordinator = BreakpointCoordinator()
        val firstConnectionAccepted = CompletableDeferred<Unit>()
        val runtime = ProxyRuntimeRepository(
            certificateAuthority = CertificateAuthority.generate(),
            certificateCache = CertificateCache(),
            breakpointGate = coordinator,
        )
        val proxyPort = availableLoopbackPort()
        val captureSink = RecordingCaptureSink {
            firstConnectionAccepted.complete(Unit)
        }
        runtime.startProxy(
            port = proxyPort,
            captureSink = captureSink,
        )

        try {
            Socket().use { activeClient ->
                activeClient.soTimeout = TEST_TIMEOUT_MILLIS
                activeClient.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort))
                withTimeout(TEST_TIMEOUT_MILLIS.toLong()) { firstConnectionAccepted.await() }

                coordinator.replaceRules(
                    listOf(
                        BreakpointRule(
                            id = "dynamic-${phase.name.lowercase()}",
                            phase = phase,
                            urlPattern = "*${origin.authority}/dynamic*",
                        )
                    )
                )

                activeClient.getOutputStream().apply {
                    write(
                        (
                            "GET http://${origin.authority}/dynamic HTTP/1.1\r\n" +
                                "Host: ${origin.authority}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray()
                    )
                    flush()
                }

                val pending = awaitPending(coordinator, phase)
                assertEquals(phase, pending.candidate.phase)
                assertEquals(
                    1,
                    captureSink.startCount(pending.candidate.exchangeId),
                    "Canonical capture must start once before the breakpoint is exposed.",
                )
                assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged))

                val response = activeClient.getInputStream().readBytes().toString(Charsets.US_ASCII)
                assertTrue(response.contains("200 OK"))
            }
        } finally {
            runtime.close()
            origin.close()
        }
    }

    /** Waits for the coordinator to publish exactly one pending breakpoint in [phase]. */
    private suspend fun awaitPending(
        coordinator: BreakpointCoordinator,
        phase: BreakpointPhase,
    ): PendingBreakpoint = withTimeout(TEST_TIMEOUT_MILLIS.toLong()) {
        coordinator.pendingBreakpoints.first { events ->
            events.any { event -> event.candidate.phase == phase }
        }.first { event -> event.candidate.phase == phase }
    }

    /** Reserves and releases a loopback listener port. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    /** Thread-safe test sink proving capture admission occurs once per breakpoint exchange. */
    private class RecordingCaptureSink(
        private val onConnectionOpened: () -> Unit,
    ) : ProxyCaptureSink {
        private val starts = ConcurrentHashMap<ExchangeId, AtomicInteger>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture {
            onConnectionOpened()
            return object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
                    streamId: com.devuloopers.knet.traffic.id.StreamId?,
                ): ProxyExchangeCapture {
                    starts.computeIfAbsent(exchangeId) { AtomicInteger() }.incrementAndGet()
                    return NoOpExchangeCapture(exchangeId)
                }

                override fun close(reason: TrafficTerminationReason?) = Unit
            }
        }

        fun startCount(exchangeId: ExchangeId): Int = starts[exchangeId]?.get() ?: 0
    }

    /** Capture handle used by the runtime test after recording exchange admission. */
    private class NoOpExchangeCapture(
        override val exchangeId: ExchangeId,
    ) : ProxyExchangeCapture {
        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            reason: TrafficTerminationReason,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

        override fun terminate(
            outcome: ExchangeTerminalOutcome,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
        ) = Unit
    }

    /** One-shot origin server used after the breakpoint decision resumes the proxy exchange. */
    private class SingleResponseOrigin : AutoCloseable {
        private val server = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        private val worker = thread(name = "knet-breakpoint-refresh-origin", isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) {
                        // Consume the complete request head before writing the deterministic response.
                    }
                    socket.getOutputStream().apply {
                        write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: 2\r\n" +
                                    "Connection: close\r\n\r\nOK"
                                ).toByteArray()
                        )
                        flush()
                    }
                }
            }
        }

        /** HTTP authority exposed by this loopback fixture. */
        val authority: String = "${KNetProxyServer.DEFAULT_BIND_HOST}:${server.localPort}"

        /** Closes the listener and waits briefly for its worker to release the accepted socket. */
        override fun close() {
            server.close()
            worker.join(TEST_TIMEOUT_MILLIS.toLong())
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS = 5_000
    }
}
