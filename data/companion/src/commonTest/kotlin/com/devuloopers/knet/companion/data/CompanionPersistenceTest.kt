package com.devuloopers.knet.companion.data

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
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
        rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8_181, scheme = CompanionEndpointScheme.HTTP),
        retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8_183, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    @Test
    fun legacyRegistrationSchemaFailsClosed() {
        val legacy = """{"schema_version":1,"active_desktop_id":"desktop-1","registrations":[]}"""
        val repository = VersionedCompanionStateRepository(MemoryRecordStore(legacy))

        assertTrue(repository.registrations.value.isEmpty())
        assertNull(repository.activeRegistration.value)
    }

    @Test
    fun repositoryRestoresActiveRegistrationWithoutCredentialMaterial() = runTest {
        val store = MemoryRecordStore()
        val first = VersionedCompanionStateRepository(store)
        first.upsert(registration(), makeActive = true)

        assertFalse(store.content.value.orEmpty().contains("issued-secret"))
        val restored = VersionedCompanionStateRepository(store)
        assertEquals("desktop-1", restored.activeRegistration.value?.desktopId?.value)
        assertEquals(1, restored.registrations.value.size)
        assertTrue(
            restored.activeRegistration.value?.rootCertificate?.copyBytes()?.contentEquals(ROOT_CERTIFICATE_BYTES) == true,
        )
    }

    @Test
    fun certificateEnrollmentRestoresForTheSameDesktopRoot() = runTest {
        val store = MemoryRecordStore()
        val registration = registration()
        val first = VersionedCompanionStateRepository(store)
        first.upsert(registration, makeActive = true)
        assertTrue(
            first.complete(
                CompanionCertificateEnrollment(
                    desktopId = registration.desktopId,
                    rootCertificateSha256 = registration.rootCertificateSha256,
                    completedAtEpochMillis = 1_500L,
                ),
            ),
        )

        val restored = VersionedCompanionStateRepository(store)

        assertEquals(1, restored.enrollments.value.size)
        assertTrue(restored.enrollments.value.single().matches(registration))
    }

    @Test
    fun rotatingDesktopRootInvalidatesPreviousCertificateEnrollment() = runTest {
        val repository = VersionedCompanionStateRepository(MemoryRecordStore())
        val registration = registration()
        repository.upsert(registration, makeActive = true)
        assertTrue(
            repository.complete(
                CompanionCertificateEnrollment(
                    registration.desktopId,
                    registration.rootCertificateSha256,
                    1_500L,
                ),
            ),
        )

        repository.upsert(
            registration.copy(rootCertificateSha256 = Sha256Fingerprint("c".repeat(64))),
            makeActive = true,
        )

        assertTrue(repository.enrollments.value.isEmpty())
    }

    @Test
    fun removingRegistrationAlsoRemovesItsCertificateEnrollment() = runTest {
        val repository = VersionedCompanionStateRepository(MemoryRecordStore())
        val registration = registration()
        repository.upsert(registration, makeActive = true)
        repository.complete(
            CompanionCertificateEnrollment(
                registration.desktopId,
                registration.rootCertificateSha256,
                1_500L,
            ),
        )

        repository.remove(registration.desktopId)

        assertTrue(repository.enrollments.value.isEmpty())
    }

    @Test
    fun deletingActiveRegistrationSelectsNextRegistration() = runTest {
        val store = MemoryRecordStore()
        val repository = VersionedCompanionStateRepository(store)
        repository.upsert(registration("desktop-1", "First"), makeActive = true)
        repository.upsert(registration("desktop-2", "Second"), makeActive = false)

        assertTrue(repository.remove(CompanionDesktopId("desktop-1")) != null)
        assertEquals("desktop-2", repository.activeRegistration.value?.desktopId?.value)
        assertNull(repository.remove(CompanionDesktopId("missing")))
    }

    @Test
    fun authenticatedLegacyIdentityMigrationIsPersistedAsOneActiveCanonicalRecord() = runTest {
        val store = MemoryRecordStore()
        val repository = VersionedCompanionStateRepository(store)
        val legacy = registration("legacy-desktop", "KNet Desktop")
        val canonical = legacy.copy(
            desktopId = CompanionDesktopId("11111111-1111-4111-8111-111111111111"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.77", 8183, scheme = CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.77", 8182, scheme = CompanionEndpointScheme.HTTPS),
        )
        repository.upsert(legacy, makeActive = true)

        assertTrue(repository.migrateIdentity(legacy.desktopId, canonical, makeActive = true))

        assertEquals(listOf(canonical), repository.registrations.value)
        assertEquals(canonical, repository.activeRegistration.value)
        val restored = VersionedCompanionStateRepository(store)
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
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        pairing = PairingInvitation(
            id = PairingInvitationId("invitation-1"),
            secret = "s".repeat(32),
            expiresAtEpochMillis = 2_000L,
            scopes = setOf(DeviceScope.PROXY_STREAM),
        ),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("proxy.knet.local", 8184, scheme = CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(ROOT_CERTIFICATE_BYTES),
    )

    private fun registration(
        desktopId: String = "desktop-1",
        displayName: String = "Development Mac",
    ): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId(desktopId),
        desktopDisplayName = CompanionDesktopDisplayName(displayName),
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, scheme = CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, scheme = CompanionEndpointScheme.HTTPS),
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
