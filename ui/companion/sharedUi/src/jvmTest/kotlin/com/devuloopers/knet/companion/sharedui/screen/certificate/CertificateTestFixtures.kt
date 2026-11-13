package com.devuloopers.knet.companion.sharedui.screen.certificate

import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope

/** Stable paired identity for certificate flow and rendered UI checks, independent of platform storage. */
internal fun certificateTestRegistration(): CompanionRegistration = CompanionRegistration(
    desktopId = CompanionDesktopId("desktop-1"),
    desktopDisplayName = CompanionDesktopDisplayName("Test desktop"),
    deviceId = RegisteredDeviceId("device-1"),
    controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
    proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
    transportIdentitySha256 = Sha256Fingerprint("b".repeat(64)),
    rootCertificateSha256 = Sha256Fingerprint("a".repeat(64)),
    rootCertificate = CompanionRootCertificate(byteArrayOf(1)),
    credentialReference = CompanionCredentialReference("credential"),
    scopes = setOf(DeviceScope.PROXY_STREAM),
    pairedAtEpochMillis = 1_000L,
    credentialExpiresAtEpochMillis = 5_000L,
)
