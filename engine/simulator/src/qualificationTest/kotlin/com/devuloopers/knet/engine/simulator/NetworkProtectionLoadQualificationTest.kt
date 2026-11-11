package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionDecision
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import io.netty.buffer.PooledByteBufAllocator
import java.io.BufferedInputStream
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real-socket acceptance evidence, separate from virtual scheduler tests and physical-device qualification. */
class NetworkProtectionLoadQualificationTest {
    @Test fun `four raw tunnels share one sustained 100 kbps budget`() {
        qualifySustained(Direction.DOWNLOAD)
    }

    @Test fun `four raw uploads share one sustained 100 kbps budget through EOF and acknowledgement`() {
        qualifySustained(Direction.UPLOAD)
    }

    private fun qualifySustained(direction: Direction) {
        // 64 seconds of offered payload leaves at least 60 seconds after every flow's first byte.
        val result = transfer(bytesPerFlow = 200_000L, shaped = true, slowConsumer = false, direction = direction)
        val bitsPerSecond = result.bytes * 8.0 / (result.elapsedNanos / 1_000_000_000.0)
        record(if (direction == Direction.UPLOAD) "sustained-upload-rate" else "sustained-rate", result)
        assertTrue(bitsPerSecond in 95_000.0..105_000.0,
            "Measured aggregate payload rate was $bitsPerSecond bit/s in ${result.elapsedNanos / 1_000_000} ms")
        assertTrue(result.elapsedNanos - result.latestFirstByteNanos >= TimeUnit.SECONDS.toNanos(60),
            "The measured window must exceed a minute after all flows have started")
        assertTrue(result.latestFirstByteNanos < TimeUnit.SECONDS.toNanos(5), "A concurrent flow was starved")
    }

    @Test fun `500 MiB traverses slow parallel tunnels without whole-body retention or byte corruption`() {
        val result = transfer(bytesPerFlow = 125L * MEBIBYTE, shaped = false, slowConsumer = true)
        record("large-stream", result)
        assertEquals(500L * MEBIBYTE, result.bytes)
        assertTrue(result.maximumQueuedBytes <= 64L * MEBIBYTE)
        // Separate heap and pooled-direct budgets catch accidental whole-body aggregation without mistaking
        // the JVM's committed heap or Netty's reusable arenas for a live application payload.
        assertTrue(result.heapIncrease <= 128L * MEBIBYTE, "Heap increased by ${result.heapIncrease} bytes")
        assertTrue(result.directIncrease <= 128L * MEBIBYTE, "Direct buffers increased by ${result.directIncrease} bytes")
    }

    private fun transfer(bytesPerFlow: Long, shaped: Boolean, slowConsumer: Boolean, direction: Direction = Direction.DOWNLOAD): Result {
        val origin = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val profile = NetworkConditionProfile(NetworkConditionProfileId("load-direction"), "Load direction",
            download = NetworkDirectionCondition(if (shaped && direction == Direction.DOWNLOAD) 100_000 else null),
            upload = NetworkDirectionCondition(if (shaped && direction == Direction.UPLOAD) 100_000 else null))
        val configuration = NetworkConditionConfiguration(enabled = true, globalProfileId = profile.id, customProfiles = listOf(profile))
        val engine = NetworkConditionEngine({ configuration })
        val sockets = CopyOnWriteArrayList<Socket>()
        val originWorkers = Executors.newFixedThreadPool(FLOWS + 1)
        val clients = Executors.newFixedThreadPool(FLOWS)
        val sampler = Executors.newSingleThreadScheduledExecutor()
        val heapBase = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val directBase = PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory()
        val heapPeak = AtomicLong(heapBase)
        val directPeak = AtomicLong(directBase)
        val queuePeak = AtomicLong()
        val latestFirst = AtomicLong()
        val proxy = KNetProxyServer(
            port = ServerSocket(0).use { it.localPort },
            serverTlsContextProvider = ServerTlsContextProvider { _, _ -> CompletableFuture.failedFuture(AssertionError("Opaque tunnel requested MITM identity")) },
            pipelineInitializers = listOf { it.addLast("qualificationConditions", NetworkConditionChannelHandler(engine)) },
            tlsInterceptionPolicy = TlsInterceptionPolicy { TlsInterceptionDecision(TlsInterceptionMode.TUNNEL) },
        )
        val originResults = CopyOnWriteArrayList<java.util.concurrent.Future<ByteArray>>()
        proxy.start()
        sampler.scheduleAtFixedRate({
            engine.refreshTelemetry()
            queuePeak.accumulateAndGet(engine.snapshot.value.queuedBytes, ::maxOf)
            heapPeak.accumulateAndGet(ManagementFactory.getMemoryMXBean().heapMemoryUsage.used, ::maxOf)
            directPeak.accumulateAndGet(PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory(), ::maxOf)
        }, 0, 10, TimeUnit.MILLISECONDS)
        val started = System.nanoTime()
        val accepted = originWorkers.submit {
            repeat(FLOWS) {
                val socket = origin.accept().also(sockets::add)
                originResults += originWorkers.submit(Callable {
                    socket.use { peer ->
                        peer.soTimeout = 90_000
                        check(peer.inputStream.read() == 1)
                        val digest = MessageDigest.getInstance("SHA-256")
                        if (direction == Direction.UPLOAD) {
                            val buffer = ByteArray(16 * 1024)
                            var received = 0L
                            while (received < bytesPerFlow) {
                                val count = peer.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), bytesPerFlow - received).toInt())
                                check(count > 0) { "Upload truncated after $received of $bytesPerFlow bytes" }
                                if (received == 0L) latestFirst.accumulateAndGet(System.nanoTime() - started, ::maxOf)
                                received += count; digest.update(buffer, 0, count)
                            }
                            check(peer.inputStream.read() == -1) { "Upload EOF overtaken or payload duplicated" }
                            val hash = digest.digest()
                            peer.outputStream.write(hash); peer.outputStream.flush(); peer.shutdownOutput()
                            return@Callable hash
                        }
                        val buffer = ByteArray(16 * 1024) { index -> (index % 127).toByte() }
                        var remaining = bytesPerFlow
                        while (remaining > 0) {
                            val count = minOf(remaining, buffer.size.toLong()).toInt()
                            peer.outputStream.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            remaining -= count
                        }
                        peer.outputStream.flush()
                        peer.shutdownOutput()
                        while (peer.inputStream.read() >= 0) { /* Retain the opposite half until delivery completes. */ }
                        digest.digest()
                    }
                })
            }
        }
        try {
            val results = clients.invokeAll((0 until FLOWS).map { Callable {
                val socket = Socket().also(sockets::add)
                socket.use { client ->
                    client.soTimeout = 90_000
                    client.connect(proxy.boundAddress())
                    val authority = "127.0.0.1:${origin.localPort}"
                    client.outputStream.write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n".encodeToByteArray())
                    client.outputStream.flush()
                    val input = BufferedInputStream(client.inputStream)
                    var header = ""
                    while (!header.endsWith("\r\n\r\n")) {
                        val byte = input.read(); check(byte >= 0)
                        header += byte.toChar(); check(header.length < 8192)
                    }
                    check(header.startsWith("HTTP/1.1 200"))
                    client.outputStream.write(1); client.outputStream.flush()
                    val digest = MessageDigest.getInstance("SHA-256")
                    if (direction == Direction.UPLOAD) {
                        val buffer = ByteArray(16 * 1024) { index -> (index % 127).toByte() }
                        var remaining = bytesPerFlow
                        while (remaining > 0) {
                            val count = minOf(remaining, buffer.size.toLong()).toInt()
                            client.outputStream.write(buffer, 0, count); digest.update(buffer, 0, count)
                            remaining -= count
                        }
                        client.outputStream.flush(); client.shutdownOutput()
                        val hash = digest.digest()
                        assertContentEquals(hash, input.readNBytes(hash.size))
                        return@Callable bytesPerFlow to hash
                    }
                    val buffer = ByteArray(16 * 1024)
                    var received = 0L
                    var chunks = 0
                    while (received < bytesPerFlow) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), bytesPerFlow - received).toInt())
                        check(count > 0) { "Opaque relay ended after $received of $bytesPerFlow bytes" }
                        if (received == 0L) latestFirst.accumulateAndGet(System.nanoTime() - started, ::maxOf)
                        received += count; digest.update(buffer, 0, count)
                        if (slowConsumer && ++chunks % 4 == 0) Thread.sleep(1)
                    }
                    client.shutdownOutput()
                    received to digest.digest()
                }
            } }, 110, TimeUnit.SECONDS)
            val elapsed = System.nanoTime() - started
            assertTrue(results.none { it.isCancelled }, "Streaming qualification exceeded its timeout")
            val received = results.map { it.get() }
            accepted.get(5, TimeUnit.SECONDS)
            val expectedDigests = originResults.map { it.get(5, TimeUnit.SECONDS) }
            received.forEach { (_, digest) -> assertContentEquals(expectedDigests.first(), digest) }
            engine.refreshTelemetry()
            val releasedBytes = when (direction) {
                Direction.DOWNLOAD -> engine.snapshot.value.downloadedBytes
                Direction.UPLOAD -> engine.snapshot.value.uploadedBytes
            }
            assertTrue(releasedBytes >= bytesPerFlow * FLOWS, "Released $direction telemetry omitted delivered payload")
            return Result(received.sumOf { it.first }, elapsed, latestFirst.get(), queuePeak.get(),
                (heapPeak.get() - heapBase).coerceAtLeast(0), (directPeak.get() - directBase).coerceAtLeast(0))
        } finally {
            sockets.forEach { runCatching(it::close) }
            origin.close()
            proxy.stop()
            sampler.shutdownNow(); clients.shutdownNow(); originWorkers.shutdownNow()
            assertTrue(clients.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(originWorkers.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS))
            engine.refreshTelemetry()
            assertEquals(0, engine.snapshot.value.activeFlows)
            assertEquals(0L, engine.snapshot.value.queuedBytes)
        }
    }

    private data class Result(val bytes: Long, val elapsedNanos: Long, val latestFirstByteNanos: Long,
        val maximumQueuedBytes: Long, val heapIncrease: Long, val directIncrease: Long)

    private enum class Direction { UPLOAD, DOWNLOAD }

    private fun record(name: String, result: Result) {
        val directory = java.io.File(System.getProperty("knet.qualification.metricsDirectory", "build/reports/network-protection-load"))
        directory.mkdirs()
        directory.resolve("$name.tsv").writeText(
            "metric\tvalue\nbytes\t${result.bytes}\nelapsedNanos\t${result.elapsedNanos}\n" +
                "bitsPerSecond\t${result.bytes * 8.0 / (result.elapsedNanos / 1_000_000_000.0)}\n" +
                "latestFirstByteNanos\t${result.latestFirstByteNanos}\nmaximumQueuedBytes\t${result.maximumQueuedBytes}\n" +
                "heapIncrease\t${result.heapIncrease}\ndirectIncrease\t${result.directIncrease}\n",
        )
    }

    private companion object {
        const val FLOWS = 4
        const val MEBIBYTE = 1024L * 1024L
    }
}
