package com.devuloopers.knet.engine.proxy.performance

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.sun.management.UnixOperatingSystemMXBean
import java.io.BufferedInputStream
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Repeatable connection-churn gate; cycle count can be increased for release soak runs. */
class ProxySoakRegressionTest {

    @Test
    fun `repeated concurrent connection churn converges without descriptor growth`() {
        val cyclesPerWorker = System.getProperty("knet.proxy.soak.cycles")
            ?.toIntOrNull()
            ?.coerceIn(DEFAULT_CYCLES_PER_WORKER, MAX_CONFIGURED_CYCLES_PER_WORKER)
            ?: DEFAULT_CYCLES_PER_WORKER
        val totalRequests = SOAK_WORKERS * cyclesPerWorker
        val origin = EmptyResponseOrigin(totalRequests)
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            ca = CertificateAuthority.generate(),
            certCache = CertificateCache(),
        )
        val initialDescriptors = openFileDescriptors()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val workers = Executors.newFixedThreadPool(SOAK_WORKERS)
        val startGate = CountDownLatch(1)

        origin.start()
        proxy.start()
        val elapsedMillis = try {
            val futures = List(SOAK_WORKERS) {
                workers.submit {
                    try {
                        assertTrue(startGate.await(5L, TimeUnit.SECONDS))
                        repeat(cyclesPerWorker) {
                            requestEmptyResponse(proxy.port, origin.port)
                        }
                    } catch (failure: Throwable) {
                        failures += failure
                    }
                }
            }
            measureTimeMillis {
                startGate.countDown()
                futures.forEach { future -> future.get(SOAK_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            }
        } finally {
            workers.shutdownNow()
            proxy.stop()
            origin.close()
        }

        assertTrue(failures.isEmpty(), failures.joinToString { failure -> failure.toString() })
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        assertEquals(totalRequests.toLong(), origin.completed.get())
        assertTrue(elapsedMillis <= TimeUnit.SECONDS.toMillis(SOAK_TIMEOUT_SECONDS))
        val recoveredDescriptors = awaitDescriptorRecovery(initialDescriptors)
        assertTrue(
            recoveredDescriptors < 0L || recoveredDescriptors <= initialDescriptors + DESCRIPTOR_ALLOWANCE,
            "Descriptors did not converge after $totalRequests churn requests.",
        )
        System.out.println(
            "KNET_PROXY_SOAK workers=$SOAK_WORKERS cyclesPerWorker=$cyclesPerWorker " +
                "requests=$totalRequests elapsedMs=$elapsedMillis recoveredDescriptors=$recoveredDescriptors"
        )
    }

    /** Sends one request over a fresh downstream connection and verifies the empty response. */
    private fun requestEmptyResponse(proxyPort: Int, originPort: Int) {
        Socket().use { client ->
            client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort), 2_000)
            client.soTimeout = 5_000
            val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
            client.getOutputStream().write(
                (
                    "GET http://$authority/soak HTTP/1.1\r\n" +
                        "Host: $authority\r\nConnection: close\r\n\r\n"
                ).toByteArray()
            )
            client.getOutputStream().flush()
            val input = BufferedInputStream(client.getInputStream())
            assertTrue(readAsciiLine(input).startsWith("HTTP/1.1 204"))
            while (readAsciiLine(input).isNotEmpty()) {
                // Drain the small response head.
            }
        }
    }

    /** Origin serving one empty response for every expected churn connection. */
    private class EmptyResponseOrigin(private val expectedConnections: Int) : AutoCloseable {
        private val server = ServerSocket()
        private val running = AtomicBoolean(false)
        private val handlers = Executors.newFixedThreadPool(SOAK_WORKERS)
        private var acceptThread: Thread? = null
        val failures = ConcurrentLinkedQueue<Throwable>()
        val completed = AtomicLong(0L)
        val port: Int get() = server.localPort

        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            running.set(true)
            acceptThread = thread(name = "knet-soak-origin", isDaemon = true) {
                try {
                    repeat(expectedConnections) {
                        val connection = server.accept()
                        handlers.execute { respond(connection) }
                    }
                } catch (failure: Throwable) {
                    if (running.get()) failures += failure
                }
            }
        }

        private fun respond(connection: Socket) {
            try {
                connection.use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    while (readAsciiLine(input).isNotEmpty()) {
                        // Drain request headers.
                    }
                    socket.getOutputStream().write(
                        "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                    )
                    socket.getOutputStream().flush()
                    completed.incrementAndGet()
                }
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        override fun close() {
            running.set(false)
            server.close()
            acceptThread?.join(5_000L)
            handlers.shutdownNow()
            handlers.awaitTermination(10L, TimeUnit.SECONDS)
        }
    }

    /** Waits for process descriptors to converge after Netty shutdown. */
    private fun awaitDescriptorRecovery(initial: Long): Long {
        if (initial < 0L) return -1L
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        var current = openFileDescriptors()
        while (current > initial + DESCRIPTOR_ALLOWANCE && System.nanoTime() < deadline) {
            Thread.sleep(25L)
            current = openFileDescriptors()
        }
        return current
    }

    /** Reserves an ephemeral loopback port. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    private companion object {
        private const val SOAK_WORKERS = 20
        private const val DEFAULT_CYCLES_PER_WORKER = 50
        private const val MAX_CONFIGURED_CYCLES_PER_WORKER = 100_000
        private const val SOAK_TIMEOUT_SECONDS = 120L
        private const val DESCRIPTOR_ALLOWANCE = 32L

        /** Reads one CRLF-terminated ASCII line. */
        private fun readAsciiLine(input: BufferedInputStream): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val value = input.read()
                require(value >= 0)
                if (value == '\n'.code) break
                if (value != '\r'.code) bytes += value.toByte()
            }
            return bytes.toByteArray().toString(Charsets.US_ASCII)
        }

        /** Returns the process descriptor count where supported. */
        private fun openFileDescriptors(): Long =
            (ManagementFactory.getOperatingSystemMXBean() as? UnixOperatingSystemMXBean)
                ?.openFileDescriptorCount
                ?: -1L
    }
}
