package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real SOCKS/UDP lifecycle regressions: closing an owner must stop its queued work and native resources. */
class UdpAssociationLifecycleTest {
    @Test fun `association close cancels delayed upload without creating a late upstream socket`() {
        Fixture().use { fixture ->
            fixture.send()
            await { fixture.gateway.queuedUdpDatagramCount == 1 }
            fixture.control.close()
            await { fixture.gateway.queuedUdpDatagramCount == 0 }
            fixture.target.soTimeout = 1_300
            assertFailsWith<SocketTimeoutException> { fixture.target.receive(DatagramPacket(ByteArray(32), 32)) }
            assertTrue(fixture.protectedSockets.isEmpty(), "Closed association recreated an upstream socket")
        }
    }

    @Test fun `gateway shutdown cancels all association queues and is idempotent`() {
        Fixture().use { fixture ->
            repeat(4) { fixture.send() }
            await { fixture.gateway.queuedUdpDatagramCount == 4 }
            fixture.gateway.close()
            fixture.gateway.close()
            await { fixture.gateway.queuedUdpDatagramCount == 0 }
            assertTrue(fixture.protectedSockets.all(DatagramSocket::isClosed))
            assertFailsWith<IllegalStateException> { fixture.gateway.start() }
        }
    }

    @Test fun `control close discards a delayed response and closes its protected socket`() {
        Fixture().use { fixture ->
            val worker = Executors.newSingleThreadExecutor()
            val echoed = CountDownLatch(1)
            val result = worker.submit {
                val packet = DatagramPacket(ByteArray(32), 32)
                fixture.target.receive(packet)
                fixture.target.send(DatagramPacket(packet.data, packet.length, packet.address, packet.port))
                echoed.countDown()
            }
            try {
                fixture.send()
                assertTrue(echoed.await(3, TimeUnit.SECONDS))
                result.get(3, TimeUnit.SECONDS)
                await { fixture.gateway.queuedUdpDatagramCount > 0 }
                fixture.control.close()
                await { fixture.gateway.queuedUdpDatagramCount == 0 && fixture.protectedSockets.all(DatagramSocket::isClosed) }
                fixture.client.soTimeout = 1_300
                assertFailsWith<SocketTimeoutException> { fixture.client.receive(DatagramPacket(ByteArray(64), 64)) }
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun `queue saturation cannot strand slots after owner close`() {
        Fixture(maximumQueued = 1).use { fixture ->
            repeat(30) { fixture.send() }
            await { fixture.gateway.queuedUdpDatagramCount == 1 }
            fixture.control.close()
            await { fixture.gateway.queuedUdpDatagramCount == 0 }
            assertTrue(fixture.protectedSockets.isEmpty())
        }
    }

    @Test fun `protection failure releases the queue and closes the rejected socket`() {
        Fixture(latency = 0, protectionAllowed = false).use { fixture ->
            fixture.send()
            await { fixture.protectedSockets.isNotEmpty() && fixture.gateway.queuedUdpDatagramCount == 0 }
            assertTrue(fixture.protectedSockets.all(DatagramSocket::isClosed))
        }
    }

    @Test fun `closed registry never calls a creator and repeated close is harmless`() {
        val registry = UdpUpstreamFlowRegistry(1, 100)
        registry.closeAll()
        registry.closeAll()
        assertNull(registry.getOrCreate(SocksDestination("127.0.0.1", 5000)) {
            fail("Terminal registry must not call a socket factory")
        })
        assertEquals(0, registry.size())
    }

    @Test fun `racing close and create cannot retain or resurrect destination sockets`() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val registry = UdpUpstreamFlowRegistry(1, 100)
                val sockets = CopyOnWriteArrayList<DatagramSocket>()
                val barrier = CountDownLatch(1)
                val creation = workers.submit {
                    barrier.await()
                    registry.getOrCreate(SocksDestination("127.0.0.1", 5000)) {
                        UdpUpstreamFlow(SocksDestination("127.0.0.1", 5000), DatagramSocket().also(sockets::add))
                    }
                }
                val closure = workers.submit { barrier.await(); registry.closeAll() }
                barrier.countDown()
                creation.get(3, TimeUnit.SECONDS)
                closure.get(3, TimeUnit.SECONDS)
                assertEquals(0, registry.size())
                assertTrue(sockets.all(DatagramSocket::isClosed))
            }
        } finally { workers.shutdownNow() }
    }

    @Test fun `live master disable releases queued UDP and retains forwarding for later packets`() {
        Fixture(latency = 60_000).use { fixture ->
            fixture.send()
            await { fixture.gateway.queuedUdpDatagramCount == 1 }
            fixture.gateway.updatePacketConditions(PacketConditionConfiguration.Disabled)
            fixture.target.soTimeout = 1_000
            val first = DatagramPacket(ByteArray(32), 32)
            fixture.target.receive(first)
            assertEquals(42, first.data[0].toInt())
            fixture.send()
            fixture.target.receive(DatagramPacket(ByteArray(32), 32))
            await { fixture.gateway.queuedUdpDatagramCount == 0 }
            assertEquals(1, fixture.protectedSockets.size, "Live disable must preserve the stable upstream socket")
        }
    }

    @Test fun `live offline clears delayed work and recovery forwards a new packet`() {
        Fixture(latency = 60_000).use { fixture ->
            fixture.send()
            await { fixture.gateway.queuedUdpDatagramCount == 1 }
            fixture.gateway.updatePacketConditions(PacketConditionConfiguration(enabled = true, lossPercent = 100))
            await { fixture.gateway.queuedUdpDatagramCount == 0 }
            assertTrue(fixture.protectedSockets.isEmpty())
            fixture.gateway.updatePacketConditions(PacketConditionConfiguration.Disabled)
            fixture.send()
            fixture.target.soTimeout = 1_000
            fixture.target.receive(DatagramPacket(ByteArray(32), 32))
        }
    }

    private class Fixture(latency: Long = 1_000, maximumQueued: Int = 16, protectionAllowed: Boolean = true) : AutoCloseable {
        val protectedSockets = CopyOnWriteArrayList<DatagramSocket>()
        val target = DatagramSocket(InetSocketAddress("127.0.0.1", 0)).apply { soTimeout = 4_000 }
        val client = DatagramSocket()
        val gateway = LocalSocks5Gateway(
            AndroidCompanionProxyTransport(),
            object : AndroidSocketProtector {
                override fun protect(socket: Socket): Boolean = true
                override fun protect(socket: DatagramSocket): Boolean {
                    protectedSockets += socket
                    return protectionAllowed
                }
            },
            UnsupportedTrafficPolicy.REJECT,
            PacketConditionConfiguration(enabled = true, latencyMillis = latency, maximumQueuedDatagrams = maximumQueued),
        ).also(LocalSocks5Gateway::start)
        val control = Socket("127.0.0.1", gateway.port).apply { soTimeout = 3_000 }
        private val relayPort: Int

        init {
            control.outputStream.write(byteArrayOf(5, 1, 0))
            assertContentEquals(byteArrayOf(5, 0), control.inputStream.readNBytes(2))
            control.outputStream.write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
            val reply = control.inputStream.readNBytes(10)
            assertEquals(0, reply[1].toInt())
            relayPort = ((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255)
        }

        fun send() {
            val request = byteArrayOf(0, 0, 0, 1, 127, 0, 0, 1,
                (target.localPort ushr 8).toByte(), target.localPort.toByte(), 42)
            client.send(DatagramPacket(request, request.size, InetSocketAddress("127.0.0.1", relayPort)))
        }

        override fun close() {
            control.close()
            gateway.close()
            client.close()
            target.close()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition()) {
            if (System.nanoTime() >= deadline) fail("Lifecycle did not reach its terminal state")
            Thread.sleep(5)
        }
    }
}
