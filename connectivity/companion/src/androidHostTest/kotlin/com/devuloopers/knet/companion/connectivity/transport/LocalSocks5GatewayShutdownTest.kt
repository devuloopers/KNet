package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Exercises real blocking SOCKS readers and observes exceptions that would reach Android's crash handler. */
class LocalSocks5GatewayShutdownTest {
    @Test
    fun `stop while UDP control is open releases queued packets and reports no uncaught exception`() {
        Fixture(PacketConditionConfiguration(enabled = true, latencyMillis = 60_000)).use { fixture ->
            val control = fixture.negotiate()
            val relayPort = fixture.request(control, command = 3)
            DatagramSocket().use { sender ->
                val packet = byteArrayOf(0, 0, 0, 1, 127, 0, 0, 1, 0x13, 0x88.toByte(), 42)
                sender.send(DatagramPacket(packet, packet.size, InetSocketAddress("127.0.0.1", relayPort)))
                await { fixture.gateway.queuedUdpDatagramCount == 1 }
                fixture.stopAndAwait()
                assertEquals(0, fixture.gateway.queuedUdpDatagramCount)
                assertEquals(-1, control.inputStream.read(), "Stopping must release the local TCP control channel")
                fixture.assertNoUncaughtExceptions()
            }
        }
    }

    @Test
    fun `stop during greeting command and initial payload reads is safe and idempotent`() {
        Fixture().use { fixture ->
            fixture.connect().outputStream.write(byteArrayOf(5)) // Incomplete authentication greeting.
            fixture.negotiate().outputStream.write(byteArrayOf(5, 1)) // Incomplete CONNECT command.
            val awaitingPayload = fixture.negotiate()
            fixture.request(awaitingPayload, command = 1)
            fixture.stopAndAwait()
            fixture.gateway.close()
            fixture.assertNoUncaughtExceptions()
        }
    }

    @Test
    fun `UDP control reset is contained and subsequent associations still work`() {
        Fixture().use { fixture ->
            // More than the connection limit catches leaked permits as well as the reset exception.
            repeat(70) {
                fixture.negotiate().apply {
                    fixture.request(this, command = 3)
                    setSoLinger(true, 0)
                    close()
                }
                fixture.awaitIdle()
            }
            val next = fixture.negotiate()
            fixture.request(next, command = 3)
            fixture.stopAndAwait()
            fixture.assertNoUncaughtExceptions()
        }
    }

    @Test
    fun `stop with an active protected TCP bridge closes upstream without crashing`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { target ->
            target.soTimeout = 3_000
            Fixture(directTcpPorts = setOf(target.localPort)).use { fixture ->
                val control = fixture.negotiate()
                fixture.request(control, command = 1, port = target.localPort)
                target.accept().use { upstream ->
                    upstream.soTimeout = 3_000
                    control.outputStream.write(42)
                    assertEquals(42, upstream.inputStream.read())
                    fixture.stopAndAwait()
                    assertEquals(-1, upstream.inputStream.read())
                    fixture.assertNoUncaughtExceptions()
                }
            }
        }
    }

    @Test
    fun `repeated gateway lifetimes close open UDP associations without late failures`() {
        repeat(10) {
            Fixture().use { fixture ->
                repeat(3) { fixture.request(fixture.negotiate(), command = 3) }
                fixture.stopAndAwait()
                fixture.assertNoUncaughtExceptions()
            }
        }
    }

    private class Fixture(
        conditions: PacketConditionConfiguration = PacketConditionConfiguration.Disabled,
        directTcpPorts: Set<Int> = emptySet(),
    ) : AutoCloseable {
        private val uncaught = CopyOnWriteArrayList<Throwable>()
        private val parent = SupervisorJob()
        private val clients = mutableListOf<Socket>()
        val gateway = LocalSocks5Gateway(
            transport = AndroidCompanionProxyTransport(),
            protector = object : AndroidSocketProtector {
                override fun protect(socket: Socket): Boolean = true
                override fun protect(socket: DatagramSocket): Boolean = true
            },
            unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
            packetConditions = conditions,
            directTcpPorts = directTcpPorts,
            executionContext = Dispatchers.IO + parent + CoroutineExceptionHandler { _, failure -> uncaught += failure },
        ).also(LocalSocks5Gateway::start)
        private val workers: Job = parent.children.single()

        fun connect(): Socket = Socket("127.0.0.1", gateway.port).apply {
            soTimeout = 3_000
            clients += this
        }

        fun negotiate(): Socket = connect().apply {
            outputStream.write(byteArrayOf(5, 1, 0))
            assertContentEquals(byteArrayOf(5, 0), inputStream.readNBytes(2))
        }

        fun request(client: Socket, command: Int, port: Int = 443): Int {
            client.outputStream.write(byteArrayOf(5, command.toByte(), 0, 1, 127, 0, 0, 1,
                (port ushr 8).toByte(), port.toByte()))
            val reply = client.inputStream.readNBytes(10)
            assertEquals(10, reply.size)
            assertEquals(0, reply[1].toInt())
            return ((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255)
        }

        fun awaitIdle() = await { workers.children.count() == 1 } // Only the accept loop remains.

        fun stopAndAwait() {
            gateway.close()
            runBlocking { withTimeout(5_000) { workers.join() } }
            assertTrue(workers.isCompleted, "Gateway workers must finish before checking for late exceptions")
        }

        fun assertNoUncaughtExceptions() {
            assertTrue(uncaught.isEmpty(), "Socket lifecycle escaped to the application crash handler: $uncaught")
        }

        override fun close() {
            try {
                stopAndAwait()
            } finally {
                clients.forEach { client -> runCatching(client::close) }
                parent.cancel()
            }
        }
    }
}

private fun await(condition: () -> Boolean) = runBlocking {
    withTimeout(5_000) { while (!condition()) delay(5) }
}
