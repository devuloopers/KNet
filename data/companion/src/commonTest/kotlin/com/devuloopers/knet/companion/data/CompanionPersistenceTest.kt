package com.devuloopers.knet.companion.data

import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class CompanionPersistenceTest {
    @Test
    fun invitationRoundTripPreservesUnicodeAndRequiredFields() {
        val codec = VersionedCompanionInvitationCodec()
        val invitation = invitation().copy(desktopDisplayName = "KNet – Development Mac")

        val result = codec.decode(codec.encode(invitation))

        assertEquals(invitation, assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Accepted>(result).invitation)
    }

    @Test
    fun duplicateOrIncompleteInvitationFailsClosed() {
        val codec = VersionedCompanionInvitationCodec()

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode("knet://pair/v1?id=a&id=b"),
        )
    }

    @Test
    fun unknownInvitationFieldsFailClosed() {
        val codec = VersionedCompanionInvitationCodec()
        val payload = codec.encode(invitation()) + "&unexpected=value"

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode(payload),
        )
    }

    @Test
    fun oversizedInvitationFailsBeforeParsing() {
        val codec = VersionedCompanionInvitationCodec()

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode("knet://pair/v1?payload=" + "a".repeat(8 * 1024)),
        )
    }

    @Test
    fun repositoryRestoresActiveRegistrationWithoutCredentialMaterial() = runTest {
        val store = MemoryRecordStore()
        val first = VersionedCompanionRegistrationRepository(store)
        first.upsert(registration(), makeActive = true)

        assertFalse(store.content.value.orEmpty().contains("issued-secret"))
        val restored = VersionedCompanionRegistrationRepository(store)
        assertEquals("desktop-1", restored.activeRegistration.value?.desktopId?.value)
        assertEquals(1, restored.registrations.value.size)
    }

    @Test
    fun deletingActiveRegistrationSelectsNextRegistration() = runTest {
        val store = MemoryRecordStore()
        val repository = VersionedCompanionRegistrationRepository(store)
        repository.upsert(registration("desktop-1", "First"), makeActive = true)
        repository.upsert(registration("desktop-2", "Second"), makeActive = false)

        assertTrue(repository.remove(CompanionDesktopId("desktop-1")) != null)
        assertEquals("desktop-2", repository.activeRegistration.value?.desktopId?.value)
        assertNull(repository.remove(CompanionDesktopId("missing")))
    }

    private class MemoryRecordStore(initial: String? = null) : CompanionRecordStore {
        private val mutableContent = MutableStateFlow(initial)
        override val content: StateFlow<String?> = mutableContent
        override suspend fun write(content: String?) {
            mutableContent.value = content
        }
    }

    private fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = 1,
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = "Development Mac",
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "s".repeat(32),
            expiresAtEpochMillis = 2_000L,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, secure = true),
        proxyEndpoint = CompanionServiceEndpoint("proxy.knet.local", 8184, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    private fun registration(
        desktopId: String = "desktop-1",
        displayName: String = "Development Mac",
    ): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId(desktopId),
        desktopDisplayName = displayName,
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, secure = true),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        credentialReference = CompanionCredentialReference("credential-$desktopId"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 2_000L,
    )
}
