package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real TCP close signals are persisted once, survive reopening Room, and reach the canonical snapshot. */
class OpaqueClosureCaptureTest {
    @Test fun `device FIN is retained before its propagated server close`() = exercise(CloseCase.DEVICE)
    @Test fun `server FIN is retained before its propagated device close`() = exercise(CloseCase.SERVER)
    @Test fun `proxy shutdown identifies KNet before capture connection close listeners run`() = exercise(CloseCase.STOP)
    @Test fun `explicit active connection reset identifies KNet and keeps the listener available`() = exercise(CloseCase.RESET_CONNECTIONS)
    @Test fun `real reset is an IO error with unknown initiator rather than a guessed remote owner`() = exercise(CloseCase.RESET_SOCKET)

    private enum class CloseCase { DEVICE, SERVER, STOP, RESET_CONNECTIONS, RESET_SOCKET }

    private fun exercise(closeCase: CloseCase) = runBlocking {
        val root = Files.createTempDirectory("knet-closure-").toFile()
        val file = root.resolve("traffic.db")
        var database = DatabaseFactory.create(file)
        val bodies = FileBodyStore(root.resolve("bodies"))
        val capture = CanonicalCaptureSessionFactory(database, bodies, bodies).openStreamingProxy(8080)
        val origin = ServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        val resetAllowed = CountDownLatch(1)
        val originWork = worker.submit {
            origin.accept().use { socket ->
                socket.soTimeout = 10_000
                assertEquals("hello", socket.inputStream.readNBytes(5).decodeToString())
                socket.outputStream.apply { write("world".encodeToByteArray()); flush() }
                when (closeCase) {
                    CloseCase.SERVER -> {
                        socket.shutdownOutput()
                        assertEquals(-1, socket.inputStream.read())
                    }
                    CloseCase.DEVICE -> {
                        assertEquals(-1, socket.inputStream.read())
                        // Keep opposite EOF observations in distinct clock ticks so provenance is conclusive.
                        Thread.sleep(40)
                        socket.shutdownOutput()
                    }
                    CloseCase.RESET_SOCKET -> {
                        check(resetAllowed.await(10, TimeUnit.SECONDS))
                        socket.setSoLinger(true, 0)
                    }
                    else -> socket.inputStream.read()
                }
            }
        }
        val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort }, captureSink = capture,
            serverTlsContextProvider = ServerTlsContextProvider { _, _ ->
                java.util.concurrent.CompletableFuture.failedFuture(AssertionError("Raw close qualification must not intercept TLS"))
            },
            tlsInterceptionPolicy = TlsInterceptionPolicy { TlsInterceptionDecision(TlsInterceptionMode.TUNNEL) })
        suspend fun awaitEnd(): OpaqueFlowEntity = withTimeout(10_000) {
            database.canonicalCaptureDao().observeOpaqueFlowChangeScalar(capture.sessionId.value)
                .map { database.canonicalCaptureDao().getOpaqueFlows(capture.sessionId.value, 10).singleOrNull() }
                .filterNotNull().first { it.state != "ACTIVE" }
        }
        proxy.start()
        try {
            val ended = Socket().use { client ->
                client.connect(proxy.boundAddress()); client.soTimeout = 10_000
                client.outputStream.apply {
                    write("CONNECT 127.0.0.1:${origin.localPort} HTTP/1.1\r\nHost: localhost\r\n\r\n".encodeToByteArray()); flush()
                }
                assertTrue(readHeaders(client.inputStream).startsWith("HTTP/1.1 200"))
                client.outputStream.apply { write("hello".encodeToByteArray()); flush() }
                assertEquals("world", client.inputStream.readNBytes(5).decodeToString())
                when (closeCase) {
                    CloseCase.DEVICE -> { client.shutdownOutput(); assertEquals(-1, client.inputStream.read()) }
                    CloseCase.SERVER -> { assertEquals(-1, client.inputStream.read()); Thread.sleep(40); client.shutdownOutput() }
                    CloseCase.STOP -> proxy.stop()
                    CloseCase.RESET_CONNECTIONS -> {
                        val address = proxy.boundAddress()
                        proxy.closeActiveConnections()
                        Socket().use { it.connect(address) }
                    }
                    CloseCase.RESET_SOCKET -> resetAllowed.countDown()
                }
                awaitEnd()
            }
            originWork.get(10, TimeUnit.SECONDS)
            val expectedOwner = when (closeCase) {
                CloseCase.DEVICE -> OpaqueFlowCloseInitiator.DEVICE
                CloseCase.SERVER -> OpaqueFlowCloseInitiator.SERVER
                CloseCase.STOP, CloseCase.RESET_CONNECTIONS -> OpaqueFlowCloseInitiator.KNET
                CloseCase.RESET_SOCKET -> OpaqueFlowCloseInitiator.UNKNOWN
            }
            val expectedReason = when (closeCase) {
                CloseCase.DEVICE, CloseCase.SERVER -> OpaqueFlowCloseReason.PEER_EOF
                CloseCase.STOP -> OpaqueFlowCloseReason.PROXY_STOPPED
                CloseCase.RESET_CONNECTIONS -> OpaqueFlowCloseReason.CONNECTIONS_RESET
                CloseCase.RESET_SOCKET -> OpaqueFlowCloseReason.IO_ERROR
            }
            assertEquals(expectedOwner.name, ended.closureInitiator)
            assertEquals(expectedReason.name, ended.closureReason)
            val closure = assertNotNull(CanonicalCaptureEntityMapper.opaqueFlowSnapshot(ended).closure)
            assertEquals(expectedOwner, closure.initiator)
            assertTrue(closure.observedAtEpochMillis >= ended.startedAtEpochMillis)
            assertTrue(closure.observedAtEpochMillis <= assertNotNull(ended.completedAtEpochMillis))
            assertTrue(ended.uploadedBytes >= 5 && ended.downloadedBytes >= 5)
            if (closeCase == CloseCase.RESET_SOCKET) assertEquals("FAILED", ended.state)
            capture.close(); database.close()
            database = DatabaseFactory.create(file)
            val reopened = assertNotNull(database.canonicalCaptureDao().getOpaqueFlow(ended.id))
            assertEquals(closure, CanonicalCaptureEntityMapper.opaqueFlowSnapshot(reopened).closure)
            assertEquals(ended.version, reopened.version)
        } finally {
            resetAllowed.countDown(); proxy.stop(); origin.close(); worker.shutdownNow()
            capture.close(); database.close(); root.deleteRecursively()
        }
    }

    private fun readHeaders(input: InputStream): String {
        val text = StringBuilder()
        while (!text.endsWith("\r\n\r\n")) { val byte = input.read(); check(byte >= 0 && text.length < 8192); text.append(byte.toChar()) }
        return text.toString()
    }
}
