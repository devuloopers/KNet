package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** End-to-end SOCKS half-close tests: ending one direction cannot truncate the opposite direction. */
class CompanionTcpHalfCloseTest {
    @Test fun `client upload EOF preserves a large pending download`() {
        val response = ByteArray(256 * 1024) { (it % 127).toByte() }
        exchange(
            origin = { socket ->
                assertContentEquals(byteArrayOf(1, 2, 3), socket.inputStream.readBytes())
                socket.outputStream.write(response)
                socket.outputStream.flush()
                socket.shutdownOutput()
            },
            client = { socket ->
                socket.outputStream.write(byteArrayOf(1, 2, 3))
                socket.outputStream.flush()
                socket.shutdownOutput()
                assertContentEquals(response, socket.inputStream.readBytes())
            },
        )
    }

    @Test fun `server download EOF preserves an upload sent after the client observes EOF`() {
        val upload = ByteArray(256 * 1024) { (it % 113).toByte() }
        exchange(
            origin = { socket ->
                socket.outputStream.write(7)
                socket.outputStream.flush()
                socket.shutdownOutput()
                assertContentEquals(upload, socket.inputStream.readBytes())
            },
            client = { socket ->
                assertEquals(7, socket.inputStream.read())
                assertEquals(-1, socket.inputStream.read())
                socket.outputStream.write(upload)
                socket.outputStream.flush()
                socket.shutdownOutput()
            },
        )
    }

    private fun exchange(origin: (Socket) -> Unit, client: (Socket) -> Unit) {
        ServerSocket(0).use { target ->
            val worker = Executors.newSingleThreadExecutor()
            val gateway = LocalSocks5Gateway(AndroidCompanionProxyTransport(), object : AndroidSocketProtector {
                override fun protect(socket: Socket): Boolean = true
                override fun protect(socket: DatagramSocket): Boolean = true
            }, UnsupportedTrafficPolicy.REJECT, directTcpPorts = setOf(target.localPort))
            gateway.start()
            val originResult = worker.submit { target.accept().use { it.soTimeout = 5_000; origin(it) } }
            try {
                Socket("127.0.0.1", gateway.port).use { socket ->
                    socket.soTimeout = 5_000
                    socket.outputStream.write(byteArrayOf(5, 1, 0))
                    assertContentEquals(byteArrayOf(5, 0), socket.inputStream.readNBytes(2))
                    socket.outputStream.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1,
                        (target.localPort ushr 8).toByte(), target.localPort.toByte()))
                    val reply = socket.inputStream.readNBytes(10)
                    assertEquals(0, reply[1].toInt())
                    client(socket)
                }
                originResult.get(5, TimeUnit.SECONDS)
            } finally {
                gateway.close()
                target.close()
                worker.shutdownNow()
                worker.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
}
