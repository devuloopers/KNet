package com.devuloopers.knet.connectivity.desktop

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.usecase.pairing.RedeemPairingOnboardingUseCase
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionCodec
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionRequest
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionControlProtocol
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrantCodec
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequest
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequestCodec
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionDiscoveryProtocol
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationCodec
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationRequest
import com.devuloopers.knet.companion.model.CompanionInvitationResponseCodec
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingCompletionCodec
import com.devuloopers.knet.companion.model.CompanionPairingGrantCodec
import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.companion.model.PacketConditionConfigurationCodec
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGateway
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class CompanionControlGatewayTest {
    @Test
    fun freshDesktopPresenceIsRestoredByAuthenticatedStartWithoutCaptureOrPairingAgain() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture()
        try {
            repeat(3) {
                assertTrue(fixture.presence.presence.value.isEmpty(), "Stored pairing cannot fabricate online presence")
                val peer = fixture.connect("Bearer device-1:${fixture.credential}")
                peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
                assertEquals("WELCOME", peer.next().kind.name)
                assertEquals(1L, peer.nextConfiguration().revision)
                eventually { fixture.presence.presence.value[RegisteredDeviceId("device-1")]?.status ==
                    com.devuloopers.knet.application.contract.device.DevicePresenceStatus.CONNECTED }
                assertEquals(setOf(RegisteredDeviceId("device-1")), fixture.presence.presence.value.keys)
                fixture.restart()
            }
        } finally { fixture.close() }
    }

    @Test
    fun ktorCertificateChallengeReturnsBodyless204WithoutLengthAndRejectsReplay(): Unit = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture()
        try {
            val credential = pairDevice(fixture.pairing, setOf(DeviceScope.SETUP_ARTIFACT_READ), "certificate-device")
            val nonce = "c".repeat(43)
            val uri = java.net.URI("https://localhost:${fixture.port}${CompanionCertificateProtocol.TRUST_CHALLENGE_PATH}")
            val request = java.net.http.HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer certificate-device:$credential")
                .header(CompanionCertificateProtocol.CHALLENGE_HEADER, nonce)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build()
            val response = fixture.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(204, response.statusCode())
            assertTrue(response.headers().firstValue("Content-Length").isEmpty)
            assertTrue(response.body().isEmpty())
            assertEquals(nonce, response.headers().firstValue(CompanionCertificateProtocol.CHALLENGE_HEADER).orElse(""))
            val replay = fixture.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(409, replay.statusCode())
        } finally { fixture.close() }
    }

    @Test
    fun ktorTlsEndpointPreservesHttpsAndRejectsUnauthenticatedUpgrade() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture()
        try {
            val uri = java.net.URI("https://localhost:${fixture.port}${CompanionControlProtocol.NETWORK_CONDITIONS_PATH}")
            val request = java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer device-1:${fixture.credential}").GET().build()
            val response = fixture.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, response.statusCode())
            assertEquals(fixture.policies.value, PacketConditionConfigurationCodec.decode(response.body()))
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertTrue(runCatching { fixture.connect("Bearer device-1:invalidcredential000") }.isFailure)
            assertTrue(runCatching { fixture.connect("Bearer device-1:${fixture.credential}", origin = "https://untrusted.test") }.isFailure)
            assertTrue(fixture.presence.presence.value.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun twoRealWebSocketsPushPoliciesAcknowledgeAndFenceReconnects() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture()
        try {
            val secondCredential = pairDevice(fixture.pairing, setOf(DeviceScope.PROXY_STREAM), "device-2")
            val first = fixture.connect("Bearer device-1:${fixture.credential}")
            val second = fixture.connect("Bearer device-2:$secondCredential")
            first.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
            second.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
            assertEquals("WELCOME", first.next().kind.name)
            assertEquals("WELCOME", second.next().kind.name)
            assertEquals(1L, first.next().revision)
            assertEquals(1L, second.next().revision)
            assertEquals(2, fixture.presence.presence.value.size)
            first.socket.sendText(com.devuloopers.knet.companion.model.CompanionSessionProtocol.encode(
                com.devuloopers.knet.companion.model.CompanionSessionMessage(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.POLICY_ACK,
                    "run", sequence = 2, revision = 1, acknowledgement = com.devuloopers.knet.companion.model.CompanionPolicyAcknowledgement.APPLIED)), true).join()
            eventually { fixture.presence.presence.value[RegisteredDeviceId("device-1")]?.policy?.revision == 1L }
            fixture.policies.value = PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000)
            assertEquals(2L, first.nextConfiguration().revision)
            assertEquals(2L, second.nextConfiguration().revision)
            val replacement = fixture.connect("Bearer device-1:${fixture.credential}")
            replacement.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
            assertEquals("WELCOME", replacement.next().kind.name)
            assertEquals(1L, replacement.nextConfiguration().revision)
            first.socket.abort()
            kotlinx.coroutines.delay(50)
            assertEquals(com.devuloopers.knet.application.contract.device.DevicePresenceStatus.CONNECTED,
                fixture.presence.presence.value[RegisteredDeviceId("device-1")]?.status)
            replacement.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.STOP, 2)
            eventually { RegisteredDeviceId("device-1") !in fixture.presence.presence.value }
            assertTrue(RegisteredDeviceId("device-2") in fixture.presence.presence.value)
            second.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.STOP, 2)
            eventually { fixture.presence.presence.value.isEmpty() }
        } finally { fixture.close() }
    }

    @Test
    fun malformedRealSessionDoesNotPoisonListenerAndRepeatedDisconnectsReleaseCapacity() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture(maximumSessions = 2)
        try {
            val invalid = fixture.connect("Bearer device-1:${fixture.credential}")
            invalid.socket.sendText("{\"kind\":\"START\",\"runId\":\"run\",\"version\":99}", true).join()
            eventually { invalid.finished }
            repeat(25) {
                val peer = fixture.connect("Bearer device-1:${fixture.credential}")
                peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
                assertEquals("WELCOME", peer.next().kind.name)
                peer.nextConfiguration()
                peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.STOP, 2)
                eventually { peer.finished }
            }
            assertTrue(fixture.presence.presence.value.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun realControlSessionsHaveBoundedLifecycleAtTenDevices() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture(maximumSessions = 12)
        try {
            fun resourceSnapshot(count: Int) {
                val memory = java.lang.management.ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                val threads = java.lang.management.ManagementFactory.getThreadMXBean().threadCount
                println("Multi-device control sample: devices=$count heapUsedBytes=$memory jvmThreads=$threads")
            }
            resourceSnapshot(0)
            val peers = (0 until 10).map { index ->
                val id = "load-device-$index"
                val credential = pairDevice(fixture.pairing, setOf(DeviceScope.PROXY_STREAM), id)
                fixture.connect("Bearer $id:$credential").also { peer ->
                    peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
                    assertEquals("WELCOME", peer.next().kind.name)
                    peer.nextConfiguration()
                    if (index in listOf(0, 1, 9)) resourceSnapshot(index + 1)
                }
            }
            assertEquals(10, fixture.presence.presence.value.size)
            peers.forEach { it.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.STOP, 2) }
            eventually { fixture.presence.presence.value.isEmpty() }
            eventually { peers.all { it.finished } }
        } finally { fixture.close() }
    }

    @Test fun futureAcknowledgementDuplicateEventBinaryAndOversizedFramesCloseOnlyTheirChannel() = kotlinx.coroutines.runBlocking {
        val fixture = ktorFixture()
        try {
            val operations: List<(TestWebSocket) -> Unit> = listOf(
                { peer -> peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.HEARTBEAT, 1) },
                { peer -> peer.socket.sendBinary(java.nio.ByteBuffer.wrap(byteArrayOf(1)), true).join() },
                { peer -> peer.socket.sendText("x".repeat(8193), true).join() },
                { peer -> peer.socket.sendText(com.devuloopers.knet.companion.model.CompanionSessionProtocol.encode(
                    com.devuloopers.knet.companion.model.CompanionSessionMessage(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.POLICY_ACK,
                        "run", sequence = 2, revision = 999, acknowledgement = com.devuloopers.knet.companion.model.CompanionPolicyAcknowledgement.APPLIED)), true).join() })
            for (operation in operations) {
                val peer = fixture.connect("Bearer device-1:${fixture.credential}")
                peer.send(com.devuloopers.knet.companion.model.CompanionSessionMessageKind.START, 1)
                assertEquals("WELCOME", peer.next().kind.name); peer.nextConfiguration()
                operation(peer)
                eventually { peer.finished }
            }
        } finally { fixture.close() }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        kotlinx.coroutines.withTimeout(5_000) { while (!predicate()) kotlinx.coroutines.delay(10) }
    }

    private suspend fun ktorFixture(maximumSessions: Int = 8): KtorFixture {
        val authority = CertificateAuthority.generate()
        val leaf = LeafCertificateGenerator.generate("localhost", authority)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, CharArray(0))
            setKeyEntry("knet-companion", leaf.keyPair.private, CharArray(0), arrayOf(leaf.certificate, authority.certificate))
        }
        val pairing = pairingCoordinator()
        val credential = pairDevice(pairing, setOf(DeviceScope.PROXY_STREAM))
        val policies = MutableStateFlow(PacketConditionConfiguration.Disabled)
        val presence = com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator({ System.nanoTime() / 1_000_000 })
        val handler = CompanionControlGateway("127.0.0.1", 0, serverFactory(authority), { authority.certificate.encoded },
            pairing, redemptionUseCase(), packetConditions = { policies.value }, nowEpochMillis = { 1_000L })
        val gateway = com.devuloopers.knet.connectivity.desktop.gateway.KtorCompanionControlGateway("127.0.0.1", 0, keyStore,
            handler, pairing, presence, policies, maximumSessions)
        gateway.start()
        val listening = kotlinx.coroutines.withTimeout(10_000) {
            gateway.state.first { it is com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayState.Listening }
        } as com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayState.Listening
        val trustStore = KeyStore.getInstance("PKCS12").apply { load(null, null); setCertificateEntry("root", authority.certificate) }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val tls = SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, SecureRandom()) }
        val client = java.net.http.HttpClient.newBuilder().sslContext(tls).connectTimeout(5.seconds.toJavaDuration()).build()
        return KtorFixture(gateway, pairing, credential, policies, presence, listening.port, client) { freshPresence ->
            com.devuloopers.knet.connectivity.desktop.gateway.KtorCompanionControlGateway("127.0.0.1", listening.port,
                keyStore, handler, pairing, freshPresence, policies, maximumSessions)
        }
    }

    private class KtorFixture(var gateway: com.devuloopers.knet.connectivity.desktop.gateway.KtorCompanionControlGateway,
        val pairing: PairingCoordinator, val credential: String, val policies: MutableStateFlow<PacketConditionConfiguration>,
        var presence: com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator,
        val port: Int, val client: java.net.http.HttpClient,
        val restartFactory: (com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator) ->
            com.devuloopers.knet.connectivity.desktop.gateway.KtorCompanionControlGateway) : AutoCloseable {
        private val peers = mutableListOf<TestWebSocket>()
        suspend fun restart() {
            gateway.close()
            peers.forEach { it.socket.abort() }; peers.clear()
            presence = com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator({ System.nanoTime() / 1_000_000 })
            gateway = restartFactory(presence)
            gateway.start()
            kotlinx.coroutines.withTimeout(10_000) {
                gateway.state.first { it is com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayState.Listening }
            }
        }
        fun connect(authorization: String, origin: String? = null): TestWebSocket {
            val listener = TestWebSocket()
            val builder = client.newWebSocketBuilder().header("Authorization", authorization)
                .connectTimeout(5.seconds.toJavaDuration())
            origin?.let { builder.header("Origin", it) }
            listener.socket = builder.buildAsync(java.net.URI("wss://localhost:$port/companion/session"), listener).get(5, java.util.concurrent.TimeUnit.SECONDS)
            peers += listener
            return listener
        }
        override fun close() { peers.forEach { it.socket.abort() }; client.shutdownNow(); gateway.close() }
    }

    private class TestWebSocket : java.net.http.WebSocket.Listener {
        lateinit var socket: java.net.http.WebSocket
        val messages = java.util.concurrent.LinkedBlockingQueue<String>(32)
        private val partial = StringBuilder()
        @Volatile var finished = false
        override fun onOpen(webSocket: java.net.http.WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: java.net.http.WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
            partial.append(data)
            check(partial.length <= 8192)
            if (last) { check(messages.offer(partial.toString())); partial.clear() }
            webSocket.request(1)
            return null
        }
        override fun onClose(webSocket: java.net.http.WebSocket, statusCode: Int, reason: String): java.util.concurrent.CompletionStage<*>? {
            finished = true; return null
        }
        override fun onError(webSocket: java.net.http.WebSocket, error: Throwable) { finished = true }
        fun send(kind: com.devuloopers.knet.companion.model.CompanionSessionMessageKind, sequence: Long) {
            socket.sendText(com.devuloopers.knet.companion.model.CompanionSessionProtocol.encode(
                com.devuloopers.knet.companion.model.CompanionSessionMessage(kind, "run", sequence)), true).join()
        }
        fun next() = com.devuloopers.knet.companion.model.CompanionSessionProtocol.decode(checkNotNull(messages.poll(5, java.util.concurrent.TimeUnit.SECONDS)))
        fun nextConfiguration(): com.devuloopers.knet.companion.model.CompanionSessionMessage {
            repeat(5) { val message = next(); if (message.kind == com.devuloopers.knet.companion.model.CompanionSessionMessageKind.CONFIGURATION) return message }
            error("No complete configuration")
        }
    }


    @Test
    fun authenticatedCompanionReadsTheCurrentBoundedPacketConditions() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val pairing = pairingCoordinator()
        val credential = pairDevice(pairing, setOf(DeviceScope.PROXY_STREAM))
        val expected = PacketConditionConfiguration(
            enabled = true,
            downloadBitsPerSecond = 100_000L,
            latencyMillis = 25L,
            lossPercent = 5,
        )
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairing,
            redeemOnboarding = redemptionUseCase(),
            packetConditions = { expected },
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val response = request(
                requireNotNull(gateway.boundPort),
                certificateAuthority.certificate,
                getRequest(
                    CompanionControlProtocol.NETWORK_CONDITIONS_PATH,
                    CompanionControlProtocol.NETWORK_CONDITIONS_RESPONSE_MEDIA_TYPE,
                    "Bearer device-1:$credential",
                ),
            )
            assertEquals(200, response.statusCode)
            assertEquals(
                CompanionControlProtocol.NETWORK_CONDITIONS_RESPONSE_MEDIA_TYPE,
                response.headers["content-type"],
            )
            assertEquals(expected, PacketConditionConfigurationCodec.decode(response.body))
        } finally {
            gateway.close()
        }
    }

    @Test
    fun endpointReconciliationRequiresCredentialAndAcceptsOnlyCanonicalOrLegacyIdentity() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val pairing = pairingCoordinator()
        val credential = pairDevice(pairing)
        val legacyId = CompanionDesktopId("knet-${"b".repeat(64)}")
        val descriptor = CompanionEndpointDescriptor(
            protocolVersion = CompanionDiscoveryProtocol.VERSION,
            desktopId = CompanionDesktopId("11111111-1111-4111-8111-111111111111"),
            acceptedLegacyIds = setOf(legacyId),
            runtimeId = CompanionDesktopRuntimeId.parse("22222222-2222-4222-8222-222222222222"),
            controlPort = 8_183,
            proxyPort = 8_182,
        )
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairing,
            redeemOnboarding = redemptionUseCase(),
            endpointDescriptor = { descriptor },
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val codec = CompanionEndpointReconciliationCodec()
            val port = requireNotNull(gateway.boundPort)
            fun reconciliationRequest(desktopId: CompanionDesktopId, authorization: String? = null): String =
                controlRequest(
                    path = CompanionControlProtocol.RECONCILE_PATH,
                    mediaType = CompanionControlProtocol.RECONCILE_REQUEST_MEDIA_TYPE,
                    body = codec.encodeRequest(CompanionEndpointReconciliationRequest(desktopId)),
                    authorization = authorization,
                )

            val unauthorized = request(port, certificateAuthority.certificate, reconciliationRequest(legacyId))
            val accepted = request(
                port,
                certificateAuthority.certificate,
                reconciliationRequest(legacyId, "Bearer device-1:$credential"),
            )
            val mismatched = request(
                port,
                certificateAuthority.certificate,
                reconciliationRequest(CompanionDesktopId("unrelated-desktop"), "Bearer device-1:$credential"),
            )

            assertEquals(401, unauthorized.statusCode)
            assertEquals(200, accepted.statusCode)
            assertEquals(descriptor, codec.decodeDescriptor(accepted.body))
            assertEquals(409, mismatched.statusCode)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun authenticatedTlsGatewayServesRootEchoesOnceAndRejectsReplay() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val pairing = pairingCoordinator()
        val credential = pairDevice(pairing)
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairing,
            redeemOnboarding = redemptionUseCase(),
            nowEpochMillis = { 1_000L },
            maximumTrackedChallenges = 1,
        )
        gateway.start()
        try {
            val port = requireNotNull(gateway.boundPort)
            val authorization = "Bearer device-1:$credential"

            val rootResponse = request(
                port,
                certificateAuthority.certificate,
                "GET ${CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH} HTTP/1.1\r\n" +
                    "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
                    "Authorization: $authorization\r\nConnection: close\r\n\r\n",
            )
            assertEquals(200, rootResponse.statusCode)
            assertContentEquals(certificateAuthority.certificate.encoded, rootResponse.body)

            val nonce = "a".repeat(43)
            val challengeRequest = "POST ${CompanionCertificateProtocol.TRUST_CHALLENGE_PATH} HTTP/1.1\r\n" +
                "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
                "Authorization: $authorization\r\n" +
                "${CompanionCertificateProtocol.CHALLENGE_HEADER}: $nonce\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n"
            val accepted = request(port, certificateAuthority.certificate, challengeRequest)
            val replayed = request(port, certificateAuthority.certificate, challengeRequest)
            val capacityReached = request(
                port,
                certificateAuthority.certificate,
                challengeRequest.replace(nonce, "b".repeat(43)),
            )

            assertEquals(204, accepted.statusCode)
            assertEquals(nonce, accepted.headers[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()])
            assertEquals(409, replayed.statusCode)
            assertEquals(429, capacityReached.statusCode)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun authenticatedTlsGatewayServesAppleProfileContainingTheExactRegisteredRoot() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val pairing = pairingCoordinator()
        val credential = pairDevice(pairing)
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairing,
            redeemOnboarding = redemptionUseCase(),
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val response = request(
                requireNotNull(gateway.boundPort),
                certificateAuthority.certificate,
                "GET ${CompanionCertificateProtocol.APPLE_PROFILE_PATH} HTTP/1.1\r\n" +
                    "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
                    "Authorization: Bearer device-1:$credential\r\nConnection: close\r\n\r\n",
            )

            assertEquals(200, response.statusCode)
            assertEquals(CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE, response.headers["content-type"])
            assertEquals(
                "attachment; filename=\"knet-ca.mobileconfig\"",
                response.headers["content-disposition"],
            )
            val profile = response.body.decodeToString()
            assertTrue("com.apple.security.root" in profile)
            assertTrue(Base64.encode(certificateAuthority.certificate.encoded) in profile)
            assertTrue("{{certificateBase64}}" !in profile)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun malformedOrUnsupportedRequestBodyFramingIsRejected() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairingCoordinator(),
            redeemOnboarding = redemptionUseCase(),
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val port = requireNotNull(gateway.boundPort)
            val requestPrefix =
                "GET ${CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH} HTTP/1.1\r\n" +
                    "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n"

            val malformedLength = request(
                port,
                certificateAuthority.certificate,
                requestPrefix + "Content-Length: invalid\r\nConnection: close\r\n\r\n",
            )
            val oversizedLength = request(
                port,
                certificateAuthority.certificate,
                requestPrefix +
                    "Content-Length: ${CompanionControlProtocol.MAXIMUM_REQUEST_BYTES + 1}\r\n" +
                    "Connection: close\r\n\r\n",
            )
            val transferEncoding = request(
                port,
                certificateAuthority.certificate,
                requestPrefix + "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n0\r\n\r\n",
            )

            assertEquals(400, malformedLength.statusCode)
            assertEquals(400, oversizedLength.statusCode)
            assertEquals(400, transferEncoding.statusCode)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun unauthenticatedCertificateDownloadIsRejectedWithoutRootBytes() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairingCoordinator(),
            redeemOnboarding = redemptionUseCase(),
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val response = request(
                requireNotNull(gateway.boundPort),
                certificateAuthority.certificate,
                "GET ${CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH} HTTP/1.1\r\n" +
                    "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
                    "Content-Length: 0\r\nConnection: close\r\n\r\n",
            )

            assertEquals(401, response.statusCode)
            assertTrue(!response.body.contentEquals(certificateAuthority.certificate.encoded))
        } finally {
            gateway.close()
        }
    }

    @Test
    fun bootstrapRedemptionReturnsCompleteInvitationOnceWithoutCredentialAuthentication() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val cryptography = TestPairingCryptography()
        val onboarding = MemoryCompanionOnboardingStore()
        val invitation = companionInvitation(certificateAuthority.certificate.encoded)
        val secret = CompanionBootstrapSecret("r".repeat(32))
        val bootstrapId = CompanionBootstrapId("bootstrap-1")
        onboarding.put(
            PendingCompanionOnboarding(
                id = bootstrapId,
                retrievalSecretDigest = cryptography.digest(secret.value),
                expiresAtEpochMillis = invitation.pairing.expiresAtEpochMillis,
                invitation = invitation,
            ),
        )
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairingCoordinator(),
            redeemOnboarding = RedeemPairingOnboardingUseCase(cryptography, onboarding) { 1_000L },
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val codec = CompanionBootstrapRedemptionCodec()
            val wrongBody = codec.encode(
                CompanionBootstrapRedemptionRequest(bootstrapId, CompanionBootstrapSecret("w".repeat(32))),
            ).decodeToString()
            val validBody = codec.encode(
                CompanionBootstrapRedemptionRequest(bootstrapId, secret),
            ).decodeToString()
            val port = requireNotNull(gateway.boundPort)

            val wrong = request(port, certificateAuthority.certificate, redemptionRequest(wrongBody))
            val accepted = request(port, certificateAuthority.certificate, redemptionRequest(validBody))
            val replayed = request(port, certificateAuthority.certificate, redemptionRequest(validBody))

            assertEquals(401, wrong.statusCode)
            assertEquals(200, accepted.statusCode)
            assertEquals(CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE, accepted.headers["content-type"])
            assertEquals(invitation, CompanionInvitationResponseCodec().decode(accepted.body))
            assertEquals(401, replayed.statusCode)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun pinnedTlsPairingAndCredentialRefreshAreOneShotAndBounded() = runTest {
        val certificateAuthority = CertificateAuthority.generate()
        val cryptography = TestPairingCryptography()
        val pairing = PairingCoordinator(MemoryTrustedDeviceStore(), cryptography, { 1_000L })
        val invitation = pairing.createInvitation(
            setOf(DeviceScope.PROXY_STREAM, DeviceScope.SETUP_ARTIFACT_READ),
        )
        val gateway = CompanionControlGateway(
            bindHost = "127.0.0.1",
            bindPort = 0,
            serverSocketFactory = serverFactory(certificateAuthority),
            rootCertificateDer = { certificateAuthority.certificate.encoded },
            pairing = pairing,
            redeemOnboarding = redemptionUseCase(),
            nowEpochMillis = { 1_000L },
        )
        gateway.start()
        try {
            val port = requireNotNull(gateway.boundPort)
            val validCompletion = PairingCompletionRequest(
                invitationId = invitation.id,
                invitationSecret = invitation.secret,
                deviceId = RegisteredDeviceId("device-1"),
                displayName = "Pixel",
                publicKeyEncoded = "public-key",
                proofSignatureEncoded = "valid-proof",
                proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            )
            val invalidProof = controlRequest(
                path = CompanionControlProtocol.PAIR_PATH,
                mediaType = CompanionControlProtocol.PAIR_REQUEST_MEDIA_TYPE,
                body = CompanionPairingCompletionCodec().encode(
                    validCompletion.copy(proofSignatureEncoded = "invalid-proof"),
                ),
            )
            val validPairing = controlRequest(
                path = CompanionControlProtocol.PAIR_PATH,
                mediaType = CompanionControlProtocol.PAIR_REQUEST_MEDIA_TYPE,
                body = CompanionPairingCompletionCodec().encode(validCompletion),
            )

            assertEquals(401, request(port, certificateAuthority.certificate, invalidProof).statusCode)
            val paired = request(port, certificateAuthority.certificate, validPairing)
            val replayedPairing = request(port, certificateAuthority.certificate, validPairing)

            assertEquals(200, paired.statusCode)
            assertEquals(CompanionControlProtocol.PAIR_RESPONSE_MEDIA_TYPE, paired.headers["content-type"])
            assertEquals(401, replayedPairing.statusCode)
            val firstGrant = CompanionPairingGrantCodec().decode(paired.body)
            val refreshBody = CompanionCredentialRefreshRequestCodec().encode(
                CompanionCredentialRefreshRequest(RegisteredDeviceId("device-1")),
            )
            val refresh = controlRequest(
                path = CompanionControlProtocol.REFRESH_PATH,
                mediaType = CompanionControlProtocol.REFRESH_REQUEST_MEDIA_TYPE,
                body = refreshBody,
                authorization = "Bearer device-1:${firstGrant.credential}",
            )

            val refreshed = request(port, certificateAuthority.certificate, refresh)
            val replayedRefresh = request(port, certificateAuthority.certificate, refresh)

            assertEquals(200, refreshed.statusCode)
            assertEquals(CompanionControlProtocol.REFRESH_RESPONSE_MEDIA_TYPE, refreshed.headers["content-type"])
            assertEquals(401, replayedRefresh.statusCode)
            val refreshedGrant = CompanionCredentialRefreshGrantCodec().decode(refreshed.body)
            assertNotEquals(firstGrant.credential, refreshedGrant.credential)
        } finally {
            gateway.close()
        }
    }

    private fun redemptionRequest(body: String): String =
        "POST ${CompanionBootstrapProtocol.REDEEM_PATH} HTTP/1.1\r\n" +
            "Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n" +
            "Content-Type: ${CompanionBootstrapProtocol.REQUEST_MEDIA_TYPE}\r\n" +
            "Content-Length: ${body.encodeToByteArray().size}\r\nConnection: close\r\n\r\n$body"

    private fun controlRequest(
        path: String,
        mediaType: String,
        body: ByteArray,
        authorization: String? = null,
    ): String = buildString {
        append("POST $path HTTP/1.1\r\n")
        append("Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n")
        append("Content-Type: $mediaType\r\n")
        authorization?.let { append("Authorization: $it\r\n") }
        append("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
        append(body.decodeToString())
    }

    private suspend fun pairDevice(
        pairing: PairingCoordinator,
        scopes: Set<DeviceScope> = setOf(DeviceScope.SETUP_ARTIFACT_READ),
        deviceId: String = "device-1",
    ): String {
        val invitation = pairing.createInvitation(scopes)
        val result = pairing.complete(
            PairingCompletionRequest(
                invitationId = invitation.id,
                invitationSecret = invitation.secret,
                deviceId = RegisteredDeviceId(deviceId),
                displayName = "Pixel",
                publicKeyEncoded = "public-key",
                proofSignatureEncoded = "valid-proof",
                proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            ),
        )
        return (result as PairingCompletionResult.Paired).issued.credential
    }

    private fun getRequest(path: String, mediaType: String, authorization: String): String = buildString {
        append("GET $path HTTP/1.1\r\n")
        append("Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n")
        append("Accept: $mediaType\r\n")
        append("Authorization: $authorization\r\n")
        append("Content-Length: 0\r\nConnection: close\r\n\r\n")
    }

    private fun pairingCoordinator(): PairingCoordinator = PairingCoordinator(
        store = MemoryTrustedDeviceStore(),
        crypto = TestPairingCryptography(),
        nowMillis = { 1_000L },
    )

    private fun redemptionUseCase(): RedeemPairingOnboardingUseCase = RedeemPairingOnboardingUseCase(
        cryptography = TestPairingCryptography(),
        onboardingStore = MemoryCompanionOnboardingStore(),
        nowEpochMillis = { 1_000L },
    )

    private fun companionInvitation(root: ByteArray): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("KNet Desktop"),
        pairing = com.devuloopers.knet.pairing.PairingInvitation(
            PairingInvitationId("pairing-1"),
            "p".repeat(32),
            2_000L,
            setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("127.0.0.1", 8_183, CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("127.0.0.1", 8_182, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(root),
    )

    private fun serverFactory(certificateAuthority: CertificateAuthority): SSLServerSocketFactory {
        val leaf = LeafCertificateGenerator.generate(CompanionCertificateProtocol.TLS_SERVER_NAME, certificateAuthority)
        val password = CharArray(0)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(
                "server",
                leaf.keyPair.private,
                password,
                arrayOf(leaf.certificate, certificateAuthority.certificate),
            )
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, SecureRandom())
        }.serverSocketFactory
    }

    private fun request(port: Int, root: X509Certificate, request: String): HttpResponse {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("root", root)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        val factory = SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, SecureRandom())
        }.socketFactory
        Socket().use { transport ->
            transport.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            val socket = factory.createSocket(
                transport,
                CompanionCertificateProtocol.TLS_SERVER_NAME,
                port,
                true,
            ) as SSLSocket
            socket.use {
                val parameters = socket.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                parameters.serverNames = listOf(SNIHostName(CompanionCertificateProtocol.TLS_SERVER_NAME))
                socket.sslParameters = parameters
                socket.startHandshake()
                socket.outputStream.write(request.encodeToByteArray())
                socket.outputStream.flush()
                return parseResponse(socket.inputStream.readBytes())
            }
        }
    }

    private fun parseResponse(bytes: ByteArray): HttpResponse {
        val separator = bytes.indexOfSequence("\r\n\r\n".encodeToByteArray())
        require(separator >= 0)
        val header = bytes.copyOfRange(0, separator).decodeToString()
        val lines = header.split("\r\n")
        val headers = lines.drop(1).associate { line ->
            line.substringBefore(':').lowercase() to line.substringAfter(':').trim()
        }
        return HttpResponse(
            statusCode = lines.first().split(' ')[1].toInt(),
            headers = headers,
            body = bytes.copyOfRange(separator + 4, bytes.size),
        )
    }

    private class MemoryTrustedDeviceStore : TrustedDeviceStore {
        private val invitations = mutableMapOf<PairingInvitationId, PendingPairingInvitation>()
        private val devices = mutableMapOf<RegisteredDeviceId, TrustedDevice>()
        private val state = MutableStateFlow<List<TrustedDevice>>(emptyList())

        override suspend fun putInvitation(invitation: PendingPairingInvitation) {
            invitations[invitation.id] = invitation
        }

        override suspend fun claimInvitation(
            id: PairingInvitationId,
            secretDigest: String,
            nowEpochMillis: Long,
        ): PendingPairingInvitation? = invitations.remove(id)?.takeIf {
            it.secretDigest == secretDigest && nowEpochMillis < it.expiresAtEpochMillis
        }

        override suspend fun putDevice(device: TrustedDevice) {
            devices[device.id] = device
            state.value = devices.values.toList()
        }

        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = devices[id]

        override suspend fun rotateCredential(
            id: RegisteredDeviceId,
            expectedCredentialDigest: String,
            newCredentialDigest: String,
            credentialExpiresAtEpochMillis: Long,
        ): Boolean {
            val device = devices[id] ?: return false
            if (device.isRevoked || device.credentialDigest != expectedCredentialDigest) return false
            devices[id] = device.copy(
                credentialDigest = newCredentialDigest,
                credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
            )
            state.value = devices.values.toList()
            return true
        }

        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean = false

        override fun observeDevices(): Flow<List<TrustedDevice>> = state
    }

    private class MemoryCompanionOnboardingStore : CompanionOnboardingStore {
        override val pendingIds = MutableStateFlow<Set<CompanionBootstrapId>>(emptySet())
        private val records = mutableMapOf<CompanionBootstrapId, PendingCompanionOnboarding>()

        override suspend fun put(pending: PendingCompanionOnboarding) {
            records[pending.id] = pending
            pendingIds.value = records.keys.toSet()
        }

        override suspend fun claim(
            id: CompanionBootstrapId,
            retrievalSecretDigest: String,
            nowEpochMillis: Long,
        ): CompanionPairingInvitation? {
            val pending = records[id] ?: return null
            if (
                nowEpochMillis >= pending.expiresAtEpochMillis ||
                retrievalSecretDigest != pending.retrievalSecretDigest
            ) return null
            records.remove(id)
            pendingIds.value = records.keys.toSet()
            return pending.invitation
        }
    }

    private class TestPairingCryptography : PairingCryptography {
        private var tokenIndex: Int = 0

        override fun randomToken(entropyBytes: Int): String {
            tokenIndex += 1
            return "token-$tokenIndex".padEnd(entropyBytes.coerceAtLeast(16), 'x')
        }
        override fun digest(value: String): String = "digest:$value"
        override fun constantTimeMatches(value: String, expectedDigest: String): Boolean = digest(value) == expectedDigest
        override fun verifyDeviceProof(
            algorithm: DeviceProofAlgorithm,
            publicKeyEncoded: String,
            message: String,
            signatureEncoded: String,
        ): Boolean = signatureEncoded == "valid-proof"
    }

    private data class HttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: ByteArray,
    )
}

private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
    for (index in 0..size - sequence.size) {
        if (sequence.indices.all { offset -> this[index + offset] == sequence[offset] }) return index
    }
    return -1
}
