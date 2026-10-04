package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCaptureMetadata
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionDecision
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.RequestHead
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ProtectedTunnelNetworkConditionIntegrationTest {
    @Test
    fun `protected raw tunnel shares the 100 kbps download budget`() {
        val payload = ByteArray(12_500) { index -> (index % 127).toByte() }
        val origin = ServerSocket(0)
        val originWorker = Thread {
            origin.accept().use { socket ->
                check(socket.getInputStream().read() == 1)
                socket.getOutputStream().apply {
                    write(payload)
                    flush()
                    socket.shutdownOutput()
                }
                socket.soTimeout = 5_000
                while (socket.getInputStream().read() >= 0) {
                    // Keep the read half alive until the proxy/client completes the opposite direction.
                }
            }
        }.apply { isDaemon = true; start() }
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val capture = RecordingOpaqueCapture()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = ServerTlsContextProvider { _, _ ->
                CompletableFuture.failedFuture(AssertionError("Tunnel must not request a certificate."))
            },
            pipelineInitializers = listOf { pipeline ->
                pipeline.addLast("testNetworkConditions", NetworkConditionChannelHandler(engine))
            },
            captureSink = ProxyCaptureSink { capture },
            tlsInterceptionPolicy = TlsInterceptionPolicy {
                TlsInterceptionDecision(TlsInterceptionMode.TUNNEL)
            },
        )
        proxy.start()

        try {
            Socket().use { socket ->
                socket.connect(proxy.boundAddress())
                socket.soTimeout = 10_000
                val endpoint = "127.0.0.1:${origin.localPort}"
                val input = BufferedInputStream(socket.getInputStream())
                val elapsed = measureTimeMillis {
                    socket.getOutputStream().apply {
                        write("CONNECT $endpoint HTTP/1.1\r\nHost: $endpoint\r\n\r\n".encodeToByteArray())
                        flush()
                    }
                    assertTrue(readHeader(input).startsWith("HTTP/1.1 200 Connection Established"))
                    socket.getOutputStream().apply {
                        write(1)
                        flush()
                    }
                    assertContentEquals(payload, input.readNBytes(payload.size))
                }

                assertTrue(elapsed >= 750L, "12.5 KB arrived too quickly for a 100 kbps budget: ${elapsed}ms")
                engine.refreshTelemetry()
                assertTrue(engine.snapshot.value.downloadedBytes >= payload.size)
                val evidence = capture.metadata?.appliedNetworkCondition
                assertTrue(evidence != null)
                assertTrue(evidence.profileId == NetworkConditionBuiltIns.STREAMING_100_KBPS.id.value)
                assertTrue(evidence.source == AppliedNetworkConditionSource.GLOBAL)
            }
        } finally {
            proxy.stop()
            origin.close()
            originWorker.join(5_000L)
        }
    }

    private class RecordingOpaqueCapture : ProxyConnectionCapture, ProxyOpaqueFlowCapture {
        @Volatile
        var metadata: ProxyOpaqueFlowCaptureMetadata? = null

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: TrafficOrigin,
            streamId: StreamId?,
        ): ProxyExchangeCapture? = null

        override fun startOpaqueFlow(metadata: ProxyOpaqueFlowCaptureMetadata): ProxyOpaqueFlowCapture {
            this.metadata = metadata
            return this
        }

        override fun observeBytes(
            direction: TrafficDirection,
            byteCount: Int,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) = Unit

        override fun close(reason: TrafficTerminationReason?) = Unit
    }

    private fun readHeader(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        var tail = ""
        while (!tail.endsWith("\r\n\r\n")) {
            val value = input.read()
            check(value >= 0) { "Proxy closed before returning a CONNECT response." }
            bytes += value.toByte()
            tail = (tail + value.toChar()).takeLast(4)
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress("127.0.0.1", 0))
        socket.localPort
    }
}
