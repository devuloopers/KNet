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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class CompanionControlGatewayTest {
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

    private suspend fun pairDevice(pairing: PairingCoordinator): String {
        val invitation = pairing.createInvitation(setOf(DeviceScope.SETUP_ARTIFACT_READ))
        val result = pairing.complete(
            PairingCompletionRequest(
                invitationId = invitation.id,
                invitationSecret = invitation.secret,
                deviceId = RegisteredDeviceId("device-1"),
                displayName = "Pixel",
                publicKeyEncoded = "public-key",
                proofSignatureEncoded = "valid-proof",
                proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            ),
        )
        return (result as PairingCompletionResult.Paired).issued.credential
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
        private val records = mutableMapOf<CompanionBootstrapId, PendingCompanionOnboarding>()

        override suspend fun put(pending: PendingCompanionOnboarding) {
            records[pending.id] = pending
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
