package com.devuloopers.knet.connectivity.desktop

import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionProxyProtocol
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.TrustedDevice
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PairingGatewayEndToEndTest {
    @Test
    fun `authenticated CONNECT preserves destination IP and following TLS ClientHello bytes`() = runBlocking {
        val pairing = PairingCoordinator(InMemoryTrustedDeviceStore(), JvmPairingCrypto(), System::currentTimeMillis)
        val issued = pair(pairing)
        val internalProxy = ServerSocket(0)
        val tls = testTlsIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val observedHeader = CompletableFuture<String>()
        val observedTlsPrefix = CompletableFuture<ByteArray>()
        val tlsPrefix = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x04, 0x01, 0x02, 0x03)
        val upstreamWorker = Thread {
            internalProxy.accept().use { socket ->
                socket.soTimeout = 5_000
                observedHeader.complete(readHeader(socket))
                socket.outputStream.apply {
                    write("HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray())
                    flush()
                }
                val received = socket.inputStream.readNBytes(tlsPrefix.size)
                observedTlsPrefix.complete(received)
                socket.outputStream.apply {
                    write(received)
                    flush()
                }
            }
        }.apply { isDaemon = true; start() }
        val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = tls.serverSocketFactory,
            targetProxy = { InetSocketAddress("127.0.0.1", internalProxy.localPort) },
            pairing = pairing,
            attributions = IngressAttributionRegistry(),
        )
        try {
            gateway.start()
            tlsSocket(requireNotNull(gateway.boundPort), tls).use { client ->
                client.soTimeout = 5_000
                client.outputStream.apply {
                    write(
                        ("CONNECT 184.28.108.10:443 HTTP/1.1\r\n" +
                            "Host: 184.28.108.10:443\r\n" +
                            "Proxy-Authorization: Bearer ${issued.device.id.value}:${issued.credential}\r\n\r\n")
                            .encodeToByteArray(),
                    )
                    flush()
                }
                assertTrue(readHeader(client).contains("200 Connection Established"))
                client.outputStream.apply {
                    write(tlsPrefix)
                    flush()
                }
                assertContentEquals(tlsPrefix, client.inputStream.readNBytes(tlsPrefix.size))
            }

            val header = observedHeader.get(5L, TimeUnit.SECONDS)
            assertTrue(header.startsWith("CONNECT 184.28.108.10:443 HTTP/1.1"))
            assertFalse(header.contains("Proxy-Authorization", ignoreCase = true))
            assertContentEquals(tlsPrefix, observedTlsPrefix.get(5L, TimeUnit.SECONDS))
        } finally {
            gateway.close()
            internalProxy.close()
            upstreamWorker.join(5_000L)
        }
    }

    @Test
    fun `paired standard proxy stream is attributed and revocation denies reconnect`() = runBlocking {
        var now = 1_000L
        val crypto = JvmPairingCrypto()
        val store = InMemoryTrustedDeviceStore()
        val pairing = PairingCoordinator(store, crypto, { now })
        val issued = pair(pairing)
        val internalProxy = ServerSocket(0)
        val tls = testTlsIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val attributions = IngressAttributionRegistry(nowMillis = { now })
        val observed = CompletableFuture<com.devuloopers.knet.traffic.model.IngressContext?>()
        val upstreamWorker = Thread {
            internalProxy.accept().use { socket ->
                val remote = socket.remoteSocketAddress as InetSocketAddress
                observed.complete(attributions.claim(TrafficEndpoint(remote.address.hostAddress, remote.port)))
                val header = readHeader(socket)
                assertFalse(header.contains("Proxy-Authorization", ignoreCase = true))
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".encodeToByteArray(),
                )
            }
        }.apply { isDaemon = true; start() }
        val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = tls.serverSocketFactory,
            targetProxy = { InetSocketAddress("127.0.0.1", internalProxy.localPort) },
            pairing = pairing,
            attributions = attributions,
            nowMillis = { now },
        )
        try {
            gateway.start()
            val gatewayPort = requireNotNull(gateway.boundPort)
            val response = request(gatewayPort, tls, issued.device.id.value, issued.credential)
            assertTrue(response.contains("200 OK"))
            val ingress = observed.get(5L, TimeUnit.SECONDS)
            assertIs<IngressKind.LanPairedDevice>(ingress?.kind)
            assertEquals(issued.device.id.value, ingress?.clientIdentity?.value)

            assertTrue(pairing.revoke(issued.device.id))
            val denied = request(gatewayPort, tls, issued.device.id.value, issued.credential)
            assertTrue(denied.contains("407 Proxy Authentication Required"))
        } finally {
            gateway.close()
            internalProxy.close()
            upstreamWorker.join(5_000L)
        }
    }

    @Test
    fun `revocation terminates an already active paired stream`() = runBlocking {
        val pairing = PairingCoordinator(InMemoryTrustedDeviceStore(), JvmPairingCrypto(), System::currentTimeMillis)
        val issued = pair(pairing)
        val internalProxy = ServerSocket(0)
        val tls = testTlsIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val requestArrived = CountDownLatch(1)
        val upstreamClosed = CompletableFuture<Boolean>()
        val upstreamWorker = Thread {
            internalProxy.accept().use { socket ->
                socket.soTimeout = 5_000
                readHeader(socket)
                requestArrived.countDown()
                upstreamClosed.complete(runCatching { socket.getInputStream().read() < 0 }.getOrDefault(true))
            }
        }.apply { isDaemon = true; start() }
        val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = tls.serverSocketFactory,
            targetProxy = { InetSocketAddress("127.0.0.1", internalProxy.localPort) },
            pairing = pairing,
            attributions = IngressAttributionRegistry(),
        )
        var client: Socket? = null
        try {
            gateway.start()
            val gatewayPort = requireNotNull(gateway.boundPort)
            val connectedClient = tlsSocket(gatewayPort, tls).also { it.soTimeout = 5_000 }
            client = connectedClient
            connectedClient.getOutputStream().write(authorizedHeader(issued.device.id.value, issued.credential))
            connectedClient.getOutputStream().flush()
            assertTrue(requestArrived.await(5L, TimeUnit.SECONDS))

            assertTrue(pairing.revoke(issued.device.id))

            assertTrue(awaitClosed(connectedClient), "Revocation did not terminate the downstream stream.")
            assertTrue(upstreamClosed.get(5L, TimeUnit.SECONDS), "Revocation did not terminate the proxy bridge.")
        } finally {
            client?.let { socket -> runCatching(socket::close) }
            gateway.close()
            internalProxy.close()
            upstreamWorker.join(5_000L)
        }
    }

    @Test
    fun `gateway rejects duplicate credentials and enforces connection admission`() = runBlocking {
        val pairing = PairingCoordinator(InMemoryTrustedDeviceStore(), JvmPairingCrypto(), System::currentTimeMillis)
        val issued = pair(pairing)
        val internalProxy = ServerSocket(0)
        val tls = testTlsIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val firstArrived = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val upstreamWorker = Thread {
            internalProxy.accept().use { socket ->
                readHeader(socket)
                firstArrived.countDown()
                releaseFirst.await(5L, TimeUnit.SECONDS)
            }
        }.apply { isDaemon = true; start() }
        val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = tls.serverSocketFactory,
            targetProxy = { InetSocketAddress("127.0.0.1", internalProxy.localPort) },
            pairing = pairing,
            attributions = IngressAttributionRegistry(),
            maximumConnections = 1,
        )
        var first: Socket? = null
        try {
            gateway.start()
            val gatewayPort = requireNotNull(gateway.boundPort)
            val connectedFirst = tlsSocket(gatewayPort, tls)
            first = connectedFirst
            connectedFirst.getOutputStream().write(authorizedHeader(issued.device.id.value, issued.credential))
            connectedFirst.getOutputStream().flush()
            assertTrue(firstArrived.await(5L, TimeUnit.SECONDS))

            val rejection = runCatching {
                rawRequest(gatewayPort, tls, authorizedHeader(issued.device.id.value, issued.credential))
            }
            assertTrue(
                rejection.getOrNull()?.contains("503") == true || rejection.isFailure,
                "A connection above the configured admission limit was not rejected.",
            )
        } finally {
            releaseFirst.countDown()
            first?.close()
            gateway.close()
            internalProxy.close()
            upstreamWorker.join(5_000L)
        }
    }

    @Test
    fun `readiness probe authenticates and reports proxy availability without opening an upstream stream`() = runBlocking {
        val pairing = PairingCoordinator(InMemoryTrustedDeviceStore(), JvmPairingCrypto(), System::currentTimeMillis)
        val issued = pair(pairing)
        val tls = testTlsIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        var target: InetSocketAddress? = null
        val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = tls.serverSocketFactory,
            targetProxy = { target },
            pairing = pairing,
            attributions = IngressAttributionRegistry(),
        )
        try {
            gateway.start()
            val port = requireNotNull(gateway.boundPort)
            val unavailable = rawRequest(port, tls, readinessHeader(issued.device.id.value, issued.credential))
            assertTrue(unavailable.contains("503 Service Unavailable"))

            target = InetSocketAddress("127.0.0.1", 65_000)
            val ready = rawRequest(port, tls, readinessHeader(issued.device.id.value, issued.credential))
            assertTrue(ready.contains("204 No Content"))
        } finally {
            gateway.close()
        }
    }

    private suspend fun pair(pairing: PairingCoordinator): com.devuloopers.knet.pairing.IssuedDeviceCredential {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val invitation = pairing.createInvitation(setOf(DeviceScope.PROXY_STREAM))
        val unsigned = PairingCompletionRequest(
            invitation.id,
            invitation.secret,
            RegisteredDeviceId("device-e2e"),
            "E2E device",
            Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded),
            "pending",
        )
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(unsigned.proofMessage().encodeToByteArray())
            sign()
        }
        val request = unsigned.copy(
            proofSignatureEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature),
        )
        return assertIs<PairingCompletionResult.Paired>(pairing.complete(request)).issued
    }

    private fun request(port: Int, tls: TestTlsIdentity, deviceId: String, credential: String): String =
        rawRequest(port, tls, authorizedHeader(deviceId, credential))

    private fun authorizedHeader(deviceId: String, credential: String): ByteArray =
        ("GET http://example.test/ HTTP/1.1\r\n" +
            "Host: example.test\r\n" +
            "Proxy-Authorization: Bearer $deviceId:$credential\r\n" +
            "Connection: close\r\n\r\n").encodeToByteArray()

    private fun readinessHeader(deviceId: String, credential: String): ByteArray =
        ("GET ${CompanionProxyProtocol.READINESS_PATH} HTTP/1.1\r\n" +
            "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
            "Proxy-Authorization: Bearer $deviceId:$credential\r\n" +
            "Connection: close\r\n\r\n").encodeToByteArray()

    private fun rawRequest(port: Int, tls: TestTlsIdentity, header: ByteArray): String =
        tlsSocket(port, tls).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(header)
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readText()
        }

    private fun tlsSocket(port: Int, tls: TestTlsIdentity): SSLSocket {
        val transport = Socket()
        transport.connect(InetSocketAddress("127.0.0.1", port), 5_000)
        val socket = tls.clientSocketFactory.createSocket(
            transport,
            CompanionCertificateProtocol.TLS_SERVER_NAME,
            port,
            true,
        ) as SSLSocket
        socket.sslParameters = socket.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
            serverNames = listOf(SNIHostName(CompanionCertificateProtocol.TLS_SERVER_NAME))
        }
        socket.startHandshake()
        return socket
    }

    private fun awaitClosed(socket: Socket): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        while (System.nanoTime() < deadline) {
            val closed = runCatching { socket.getInputStream().read() < 0 }.getOrDefault(true)
            if (closed) return true
        }
        return false
    }

    private fun readHeader(socket: Socket): String {
        val bytes = ArrayList<Byte>()
        var tail = ""
        while (!tail.endsWith("\r\n\r\n")) {
            val value = socket.getInputStream().read()
            if (value < 0) break
            bytes += value.toByte()
            tail = (tail + value.toChar()).takeLast(4)
        }
        return bytes.toByteArray().decodeToString()
    }

    private class InMemoryTrustedDeviceStore : TrustedDeviceStore {
        private val invitations = mutableMapOf<PairingInvitationId, PendingPairingInvitation>()
        private val devices = mutableMapOf<RegisteredDeviceId, TrustedDevice>()
        private val devicesFlow = MutableStateFlow<List<TrustedDevice>>(emptyList())

        override suspend fun putInvitation(invitation: PendingPairingInvitation) {
            invitations[invitation.id] = invitation
        }

        override suspend fun claimInvitation(
            id: PairingInvitationId,
            secretDigest: String,
            nowEpochMillis: Long,
        ): PendingPairingInvitation? {
            val invitation = invitations[id] ?: return null
            if (invitation.secretDigest != secretDigest || invitation.expiresAtEpochMillis <= nowEpochMillis) return null
            invitations.remove(id)
            return invitation
        }

        override suspend fun putDevice(device: TrustedDevice) {
            devices[device.id] = device
            publish()
        }

        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = devices[id]

        override suspend fun rotateCredential(
            id: RegisteredDeviceId,
            expectedCredentialDigest: String,
            newCredentialDigest: String,
            credentialExpiresAtEpochMillis: Long,
        ): Boolean {
            val current = devices[id] ?: return false
            if (current.isRevoked || current.credentialDigest != expectedCredentialDigest) return false
            devices[id] = current.copy(
                credentialDigest = newCredentialDigest,
                credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
            )
            publish()
            return true
        }

        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean {
            val current = devices[id] ?: return false
            if (current.isRevoked) return true
            devices[id] = current.copy(
                registeredDevice = current.registeredDevice.copy(revokedAtEpochMillis = revokedAtEpochMillis),
            )
            publish()
            return true
        }

        override fun observeDevices(): Flow<List<TrustedDevice>> = devicesFlow

        private fun publish() {
            devicesFlow.value = devices.values.toList()
        }
    }

}
