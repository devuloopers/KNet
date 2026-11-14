package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.connectivity.certificate.sha256Hex
import com.devuloopers.knet.companion.connectivity.http.AndroidCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.transport.testCertificateIdentity
import com.devuloopers.knet.companion.connectivity.transport.TestCertificateIdentity
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionSessionUseCase
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionPacketPolicyUseCase
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Real OkHttp TLS and RFC6455 handshake, including logical authority, exact pairing pin and status classification. */
class AndroidCompanionSessionTransportTest {
    @Test fun `VPN owner reconnects to a restarted TLS listener using the same pinned identity`(): Unit = runBlocking {
        val first = Server()
        val access = first.access()
        val registration = companionRegistrationFixture().copy(deviceId = access.deviceId,
            controlEndpoint = access.endpoint, rootCertificate = access.root,
            rootCertificateSha256 = access.rootFingerprint, transportIdentitySha256 = access.transportFingerprint)
        val registrations = object : CompanionRegistrationRepository {
            override val activeRegistration = MutableStateFlow<CompanionRegistration?>(registration)
            override val registrations = MutableStateFlow(listOf(registration))
            override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean): Unit = error("Re-enrollment is not recovery")
            override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean = error("Selection must be retained")
            override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = error("Pairing must be retained")
            override suspend fun migrateIdentity(previousDesktopId: CompanionDesktopId, registration: CompanionRegistration, makeActive: Boolean): Boolean = error("Identity must be retained")
        }
        val credentials = object : CompanionCredentialStore {
            override suspend fun read(reference: CompanionCredentialReference) = "abcdefghijklmnop"
            override suspend fun write(reference: CompanionCredentialReference, credential: String): Unit = error("No credential rewrite")
            override suspend fun remove(reference: CompanionCredentialReference): Unit = error("No credential removal")
        }
        val owner = MaintainCompanionSessionUseCase(transport(), credentials, registrations,
            MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { error("No legacy fallback") }, registrations))
        val firstStart = first.executor.submit<CompanionSessionMessage> {
            (first.socket.accept() as SSLSocket).use { socket -> admit(socket) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch { owner.execute(registration, "same-vpn-run", PacketConditionConfiguration.Disabled,
            CompanionPacketPolicySink { PacketPolicyUpdateResult.APPLIED }) }
        var restarted: Server? = null
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            val initial = firstStart.get(10, TimeUnit.SECONDS)
            first.close()
            withTimeout(5_000) { owner.state.first { it?.phase == CompanionSessionPhase.RECONNECTING } }
            val replacement = Server(first.identity, access.endpoint.port).also { restarted = it }
            val nextStart = replacement.executor.submit<CompanionSessionMessage> {
                (replacement.socket.accept() as SSLSocket).use { socket ->
                    admit(socket).also { assertTrue(release.await(10, TimeUnit.SECONDS)) }
                }
            }
            withTimeout(10_000) { owner.state.first { it?.phase == CompanionSessionPhase.CONNECTED } }
            assertTrue(job.isActive)
            assertEquals(registration, registrations.activeRegistration.value)
            release.countDown()
            assertEquals(initial.runId, nextStart.get(5, TimeUnit.SECONDS).runId)
            assertEquals(1L, initial.sequence)
        } finally {
            release.countDown()
            job.cancelAndJoin(); scope.cancel()
            restarted?.close(); first.close()
        }
        assertNull(owner.state.value)
    }

    private fun admit(socket: SSLSocket): CompanionSessionMessage {
        socket.soTimeout = 5000
        val headers = headers(socket)
        assertEquals("Bearer device:abcdefghijklmnop", headers["authorization"])
        val accept = Base64.encode(MessageDigest.getInstance("SHA-1").digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
        socket.outputStream.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
        socket.outputStream.flush()
        val start = CompanionSessionProtocol.decode(readClientText(socket))
        assertEquals(CompanionSessionMessageKind.START, start.kind)
        writeText(socket, CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, start.runId)))
        writeText(socket, CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION,
            start.runId, revision = 1, configuration = PacketConditionConfiguration.Disabled)))
        return start
    }

    @Test fun `native pinned socket exchanges bounded messages and releases the persistent client`(): Unit = runBlocking {
        Server().use { server ->
            val task = server.executor.submit<String> {
                (server.socket.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 5000
                    val headers = headers(socket)
                    assertEquals("Bearer device:abcdefghijklmnop", headers["authorization"])
                    assertTrue(headers.getValue("host").startsWith(CompanionCertificateProtocol.TLS_SERVER_NAME))
                    val accept = Base64.encode(MessageDigest.getInstance("SHA-1").digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
                    socket.outputStream.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
                    socket.outputStream.flush()
                    val start = readClientText(socket)
                    val welcome = CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, "run"))
                    writeText(socket, welcome)
                    start
                }
            }
            val connection = withTimeout(5000) { transport().connect(server.access(), "abcdefghijklmnop") }
            try {
                connection.send(CompanionSessionMessage(CompanionSessionMessageKind.START, "run", sequence = 1))
                assertEquals(CompanionSessionMessageKind.WELCOME, withTimeout(5000) { connection.receive() }.kind)
                assertEquals(CompanionSessionMessageKind.START, CompanionSessionProtocol.decode(task.get(5, TimeUnit.SECONDS)).kind)
                val failure = assertFailsWith<CompanionSessionException> { withTimeout(5000) { connection.receive() } }
                assertEquals(CompanionSessionFailure.UNAVAILABLE, failure.reason)
            } finally { connection.close(); connection.close() }
        }
    }

    @Test fun `unauthorized and older peers are classified without exposing credentials`(): Unit = runBlocking {
        for ((status, reason) in listOf(401 to CompanionSessionFailure.UNAUTHORIZED, 403 to CompanionSessionFailure.UNAUTHORIZED,
            404 to CompanionSessionFailure.UNSUPPORTED, 426 to CompanionSessionFailure.UNSUPPORTED)) {
            Server().use { server ->
                val task = server.executor.submit {
                    (server.socket.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5000; headers(socket)
                        socket.outputStream.write("HTTP/1.1 $status Rejected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        socket.outputStream.flush()
                    }
                }
                val failure = assertFailsWith<CompanionSessionException> { withTimeout(5000) { transport().connect(server.access(), "abcdefghijklmnop") } }
                assertEquals(reason, failure.reason)
                assertFalse(failure.message.orEmpty().contains("abcdefghijklmnop"))
                task.get(5, TimeUnit.SECONDS)
            }
        }
    }

    @Test fun `mismatched local root pin never reaches authenticated upgrade`(): Unit = runBlocking {
        Server().use { server ->
            assertFails { transport().connect(server.access().copy(rootFingerprint = Sha256Fingerprint("0".repeat(64))), "abcdefghijklmnop") }
            server.socket.soTimeout = 100
            assertFailsWith<java.net.SocketTimeoutException> { server.socket.accept() }
        }
    }

    private fun transport() = KtorCompanionSessionTransport(AndroidCompanionKtorClientProvider())
    private fun headers(socket: SSLSocket): Map<String, String> {
        val reader = socket.inputStream.bufferedReader()
        assertTrue(reader.readLine().startsWith("GET /companion/session "))
        return buildMap { while (true) { val line = reader.readLine() ?: error("truncated handshake"); if (line.isEmpty()) break; put(line.substringBefore(':').lowercase(), line.substringAfter(':').trim()) } }
    }
    private fun readClientText(socket: SSLSocket): String {
        val input = socket.inputStream
        assertEquals(0x81, input.read())
        val marker = input.read(); assertTrue(marker and 128 != 0)
        var length = marker and 127
        if (length == 126) length = input.read() * 256 + input.read()
        require(length in 1..8192)
        val mask = input.readNBytes(4)
        return input.readNBytes(length).mapIndexed { index, byte -> (byte.toInt() xor mask[index % 4].toInt()).toByte() }.toByteArray().decodeToString()
    }
    private fun writeText(socket: SSLSocket, text: String) {
        val bytes = text.toByteArray(); val output = socket.outputStream
        output.write(0x81)
        if (bytes.size < 126) output.write(bytes.size) else { output.write(126); output.write(bytes.size ushr 8); output.write(bytes.size and 255) }
        output.write(bytes); output.flush()
    }
    private class Server(val identity: TestCertificateIdentity = testCertificateIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME), port: Int = 0) : AutoCloseable {
        val executor = Executors.newSingleThreadExecutor()
        val socket: SSLServerSocket
        init {
            val store = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("server", identity.leafPrivateKey, CharArray(0), arrayOf(identity.leafCertificate, identity.rootCertificate)) }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
            socket = (SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress("127.0.0.1", port), 8)
            }
        }
        fun access() = CompanionSessionAccess(CompanionServiceEndpoint("127.0.0.1", socket.localPort, CompanionEndpointScheme.HTTPS),
            RegisteredDeviceId("device"), CompanionRootCertificate(identity.rootCertificate.encoded),
            Sha256Fingerprint(identity.rootCertificate.sha256Hex()), Sha256Fingerprint(identity.rootCertificate.sha256Hex()))
        override fun close() { socket.close(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)) }
    }
}
