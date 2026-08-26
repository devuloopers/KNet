package com.devuloopers.knet.companion.model

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompanionModelsTest {
    @Test
    fun registrationNeverContainsCredentialValue() {
        val registration = registration()

        assertEquals("credential-reference", registration.credentialReference.value)
        assertEquals(setOf(DeviceScope.PROXY_STREAM), registration.scopes)
    }

    @Test
    fun fingerprintsRejectUppercaseAndWrongLength() {
        assertFailsWith<IllegalArgumentException> { Sha256Fingerprint("A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { Sha256Fingerprint("a".repeat(63)) }
    }

    @Test
    fun invitationRequiresSecureControlAndProxyEndpoints() {
        assertFailsWith<IllegalArgumentException> {
            invitation().copy(controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, secure = false))
        }
        assertFailsWith<IllegalArgumentException> {
            invitation().copy(proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, secure = false))
        }
    }

    @Test
    fun endpointRejectsUrlSyntaxWhitespaceAndControlCharacters() {
        listOf(
            "https://desktop.local",
            "desktop.local/path",
            "desktop.local?query",
            "desktop local",
            "desktop.local\n",
        ).forEach { unsafeHost ->
            assertFailsWith<IllegalArgumentException> {
                CompanionServiceEndpoint(unsafeHost, 8183, secure = true)
            }
        }
    }

    @Test
    fun identifierLengthsAreBounded() {
        assertFailsWith<IllegalArgumentException> { CompanionDesktopId("d".repeat(129)) }
        assertFailsWith<IllegalArgumentException> { CompanionCredentialReference("r".repeat(513)) }
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
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    )

    private fun registration(): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = "Development Mac",
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, secure = true),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, secure = true),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        credentialReference = CompanionCredentialReference("credential-reference"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 2_000L,
    )
}
