package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InMemoryCompanionOnboardingStoreTest {
    @Test
    fun matchingSecretDigestClaimsExactlyOnce() = runTest {
        val store = InMemoryCompanionOnboardingStore(nowEpochMillis = { 1_000L })
        store.put(pending("bootstrap-1", expiresAt = 2_000L))

        assertEquals(invitation(), store.claim(CompanionBootstrapId("bootstrap-1"), "digest", 1_000L))
        assertNull(store.claim(CompanionBootstrapId("bootstrap-1"), "digest", 1_000L))
    }

    @Test
    fun wrongSecretDoesNotConsumeButExpiryDoes() = runTest {
        val store = InMemoryCompanionOnboardingStore(nowEpochMillis = { 1_000L })
        store.put(pending("bootstrap-1", expiresAt = 2_000L))

        assertNull(store.claim(CompanionBootstrapId("bootstrap-1"), "wrong", 1_000L))
        assertEquals(invitation(), store.claim(CompanionBootstrapId("bootstrap-1"), "digest", 1_500L))

        store.put(pending("bootstrap-2", expiresAt = 2_000L))
        assertNull(store.claim(CompanionBootstrapId("bootstrap-2"), "digest", 2_000L))
    }

    @Test
    fun boundedStoreRejectsNewLiveRecordAtCapacity() = runTest {
        val store = InMemoryCompanionOnboardingStore(maximumRecords = 1, nowEpochMillis = { 1_000L })
        store.put(pending("bootstrap-1", expiresAt = 2_000L))

        assertFailsWith<IllegalStateException> {
            store.put(pending("bootstrap-2", expiresAt = 2_000L))
        }
    }

    private fun pending(id: String, expiresAt: Long): PendingCompanionOnboarding = PendingCompanionOnboarding(
        id = CompanionBootstrapId(id),
        retrievalSecretDigest = "digest",
        expiresAtEpochMillis = expiresAt,
        invitation = invitation(expiresAt),
    )

    private fun invitation(expiresAt: Long = 2_000L): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = "KNet Desktop",
        pairing = PairingInvitation(
            PairingInvitationId("pairing-1"),
            "p".repeat(32),
            expiresAt,
            setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_183, true),
        proxyEndpoint = CompanionServiceEndpoint("192.0.2.1", 8_182, true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    )
}
