package com.devuloopers.knet.companion.data

import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
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
    fun bootstrapRoundTripPreservesRequiredFields() {
        val codec = VersionedCompanionInvitationCodec()
        val bootstrap = bootstrap()

        val result = codec.decode(codec.encode(bootstrap))

        assertEquals(
            bootstrap,
            assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Accepted>(result).bootstrap,
        )
    }

    @Test
    fun duplicateOrIncompleteInvitationFailsClosed() {
        val codec = VersionedCompanionInvitationCodec()

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode("knet://pair/v3?id=a&id=b"),
        )
    }

    @Test
    fun unknownInvitationFieldsFailClosed() {
        val codec = VersionedCompanionInvitationCodec()
        val payload = codec.encode(bootstrap()) + "&unexpected=value"

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode(payload),
        )
    }

    @Test
    fun oversizedInvitationFailsBeforeParsing() {
        val codec = VersionedCompanionInvitationCodec()

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode("knet://pair/v3?payload=" + "a".repeat(2 * 1024)),
        )
    }

    @Test
    fun legacyInvitationVersionFailsClosed() {
        val codec = VersionedCompanionInvitationCodec()

        assertIs<com.devuloopers.knet.companion.application.contract.InvitationDecodeResult.Rejected>(
            codec.decode("knet://pair/v1?desktopId=legacy"),
        )
    }

    private fun bootstrap(): CompanionPairingBootstrap = CompanionPairingBootstrap(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
        id = CompanionBootstrapId("bootstrap-1"),
        retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
        expiresAtEpochMillis = 2_000L,
        rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8_181, secure = false),
        retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8_183, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    @Test
    fun legacyRegistrationSchemaFailsClosed() {
        val legacy = """{"schema_version":1,"active_desktop_id":"desktop-1","registrations":[]}"""
        val repository = VersionedCompanionRegistrationRepository(MemoryRecordStore(legacy))

        assertTrue(repository.registrations.value.isEmpty())
        assertNull(repository.activeRegistration.value)
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
        assertTrue(
            restored.activeRegistration.value?.rootCertificate?.copyBytes()?.contentEquals(ROOT_CERTIFICATE_BYTES) == true,
        )
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

    @Test
    fun authenticatedLegacyIdentityMigrationIsPersistedAsOneActiveCanonicalRecord() = runTest {
        val store = MemoryRecordStore()
        val repository = VersionedCompanionRegistrationRepository(store)
        val legacy = registration("legacy-desktop", "KNet Desktop")
        val canonical = legacy.copy(
            desktopId = CompanionDesktopId("11111111-1111-4111-8111-111111111111"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.77", 8183, secure = true),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.77", 8182, secure = true),
        )
        repository.upsert(legacy, makeActive = true)

        assertTrue(repository.migrateIdentity(legacy.desktopId, canonical, makeActive = true))

        assertEquals(listOf(canonical), repository.registrations.value)
        assertEquals(canonical, repository.activeRegistration.value)
        val restored = VersionedCompanionRegistrationRepository(store)
        assertEquals(listOf(canonical), restored.registrations.value)
        assertEquals(canonical, restored.activeRegistration.value)
    }

    private class MemoryRecordStore(initial: String? = null) : CompanionRecordStore {
        private val mutableContent = MutableStateFlow(initial)
        override val content: StateFlow<String?> = mutableContent
        override suspend fun write(content: String?) {
            mutableContent.value = content
        }
    }

    private fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
        protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
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
        rootCertificate = CompanionRootCertificate(ROOT_CERTIFICATE_BYTES),
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
        rootCertificate = CompanionRootCertificate(ROOT_CERTIFICATE_BYTES),
        credentialReference = CompanionCredentialReference("credential-$desktopId"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 2_000L,
    )

    private companion object {
        val ROOT_CERTIFICATE_BYTES: ByteArray = byteArrayOf(1, 2, 3, 4)
    }
}
