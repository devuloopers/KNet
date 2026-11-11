package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.tls.*
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real CONNECT upload + half-close fixture with shaping delays greater than the transport idle deadline. */
class ProtectedTunnelUploadEofIntegrationTest {
    @Test fun `slow upload EOF cannot overtake queued payload or truncate the reverse response`() {
        val payload = ByteArray(12_500) { (it % 127).toByte() }
        val profile = NetworkConditionProfile(NetworkConditionProfileId("slow-upload"), "Slow upload",
            upload = NetworkDirectionCondition(bitsPerSecond = 100_000), latencyMillis = 2_000)
        val configuration = NetworkConditionConfiguration(enabled = true, globalProfileId = profile.id, customProfiles = listOf(profile))
        val engine = NetworkConditionEngine({ configuration })
        ServerSocket(0).use { origin ->
            val worker = Executors.newSingleThreadExecutor()
            val result = worker.submit {
                origin.accept().use { socket ->
                    socket.soTimeout = 15_000
                    val request = socket.inputStream.readBytes()
                    assertContentEquals(payload, request)
                    socket.outputStream.write(request)
                    socket.outputStream.flush()
                    socket.shutdownOutput()
                }
            }
            val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort },
                serverTlsContextProvider = ServerTlsContextProvider { _, _ -> CompletableFuture.failedFuture(AssertionError("Raw tunnel requested a certificate")) },
                runtimePolicy = KNetProxyRuntimePolicy(readIdleTimeoutMillis = 1_000, writeIdleTimeoutMillis = 1_000),
                pipelineInitializers = listOf { it.addLast("uploadConditions", NetworkConditionChannelHandler(engine)) },
                tlsInterceptionPolicy = TlsInterceptionPolicy { TlsInterceptionDecision(TlsInterceptionMode.TUNNEL) })
            proxy.start()
            try {
                Socket().use { socket ->
                    socket.soTimeout = 20_000
                    socket.connect(proxy.boundAddress())
                    val authority = "127.0.0.1:${origin.localPort}"
                    socket.outputStream.write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n".encodeToByteArray())
                    socket.outputStream.flush()
                    val input = BufferedInputStream(socket.inputStream)
                    var header = ""
                    while (!header.endsWith("\r\n\r\n")) {
                        val value = input.read(); check(value >= 0); header += value.toChar(); check(header.length < 8192)
                    }
                    assertTrue(header.startsWith("HTTP/1.1 200"))
                    socket.outputStream.write(payload)
                    socket.outputStream.flush()
                    socket.shutdownOutput()
                    assertContentEquals(payload, input.readBytes())
                }
                result.get(5, TimeUnit.SECONDS)
            } finally {
                proxy.stop(); origin.close(); worker.shutdownNow()
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }
}
