package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapPayloadCodec
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreatePairingOnboardingUseCaseTest {
    @Test
    fun `desktop creates canonical companion invitation without persisting plaintext secret`() = runTest {
        val store = RecordingTrustedDeviceStore()
        val onboardingStore = RecordingCompanionOnboardingStore()
        val cryptography = DeterministicPairingCryptography()
        val codec = CompanionBootstrapPayloadCodec()
        val useCase = CreatePairingOnboardingUseCase(
            pairing = PairingCoordinator(
                store = store,
                crypto = cryptography,
                nowMillis = { NOW_MILLIS },
            ),
            environmentProvider = PairingOnboardingEnvironmentProvider { environment() },
            cryptography = cryptography,
            onboardingStore = onboardingStore,
            payloadCodec = codec,
        )

        val descriptor = useCase.execute()
        val decoded = codec.decode(descriptor.qrPayload)

        assertTrue(descriptor.qrPayload.startsWith("knet://pair/v3?"))
        assertTrue(descriptor.qrPayload.length < 512)
        assertEquals(descriptor.qrPayload, descriptor.deepLink)
        assertEquals("192.0.2.10", decoded.retrievalEndpoint.host)
        assertEquals(8_181, decoded.rootCertificateEndpoint.port)
        assertEquals(onboardingStore.pending?.invitation?.pairing?.id, store.pendingInvitation?.id)
        assertEquals(onboardingStore.pending?.id, decoded.id)
        assertFalse(
            store.pendingInvitation?.secretDigest.orEmpty().contains(
                onboardingStore.pending?.invitation?.pairing?.secret.orEmpty(),
            ),
        )
        assertFalse(onboardingStore.pending?.retrievalSecretDigest.orEmpty().contains(decoded.retrievalSecret.value))
    }

    private class RecordingCompanionOnboardingStore : CompanionOnboardingStore {
        var pending: PendingCompanionOnboarding? = null

        override suspend fun put(pending: PendingCompanionOnboarding) {
            this.pending = pending
        }

        override suspend fun claim(
            id: CompanionBootstrapId,
            retrievalSecretDigest: String,
            nowEpochMillis: Long,
        ): CompanionPairingInvitation? = null
    }

    private fun environment(): PairingOnboardingEnvironment = PairingOnboardingEnvironment(
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = "KNet Desktop",
        rootCertificateEndpoint = CompanionServiceEndpoint("192.0.2.10", 8_181, secure = false),
        controlEndpoint = CompanionServiceEndpoint("192.0.2.10", 8_183, secure = true),
        proxyEndpoint = CompanionServiceEndpoint("192.0.2.10", 8_182, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )

    private class RecordingTrustedDeviceStore : TrustedDeviceStore {
        var pendingInvitation: PendingPairingInvitation? = null

        override suspend fun putInvitation(invitation: PendingPairingInvitation) {
            pendingInvitation = invitation
        }

        override suspend fun claimInvitation(
            id: PairingInvitationId,
            secretDigest: String,
            nowEpochMillis: Long,
        ): PendingPairingInvitation? = null

        override suspend fun putDevice(device: TrustedDevice) = Unit

        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = null

        override suspend fun rotateCredential(
            id: RegisteredDeviceId,
            expectedCredentialDigest: String,
            newCredentialDigest: String,
            credentialExpiresAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean = false

        override fun observeDevices(): Flow<List<TrustedDevice>> = emptyFlow()
    }

    private class DeterministicPairingCryptography : PairingCryptography {
        private var tokenIndex: Int = 0

        override fun randomToken(entropyBytes: Int): String {
            tokenIndex += 1
            return "token-$tokenIndex".padEnd(entropyBytes.coerceAtLeast(16), 'x')
        }

        override fun digest(value: String): String = "digest-${value.length}-${value.first()}"

        override fun constantTimeMatches(value: String, expectedDigest: String): Boolean =
            digest(value) == expectedDigest

        override fun verifyDeviceProof(
            algorithm: DeviceProofAlgorithm,
            publicKeyEncoded: String,
            message: String,
            signatureEncoded: String,
        ): Boolean = true
    }

    private companion object {
        const val NOW_MILLIS: Long = 1_000L
    }
}
