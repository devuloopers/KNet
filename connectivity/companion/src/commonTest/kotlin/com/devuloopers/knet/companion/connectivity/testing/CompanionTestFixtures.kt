package com.devuloopers.knet.companion.connectivity.testing

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope

internal fun companionRegistrationFixture(
    transportIdentitySha256: String = "a".repeat(64),
    rootCertificateSha256: String = "b".repeat(64),
    rootCertificateBytes: ByteArray = byteArrayOf(1, 2, 3),
    scopes: Set<DeviceScope> = setOf(DeviceScope.PROXY_STREAM),
): CompanionRegistration = CompanionRegistration(
    desktopId = CompanionDesktopId("desktop-1"),
    desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
    deviceId = RegisteredDeviceId("device-1"),
    controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
    proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
    transportIdentitySha256 = Sha256Fingerprint(transportIdentitySha256),
    rootCertificateSha256 = Sha256Fingerprint(rootCertificateSha256),
    rootCertificate = CompanionRootCertificate(rootCertificateBytes),
    credentialReference = CompanionCredentialReference("credential-reference"),
    scopes = scopes,
    pairedAtEpochMillis = 1_000L,
    credentialExpiresAtEpochMillis = 2_000L,
)

internal fun companionInspectionConfigurationFixture(): CompanionInspectionConfiguration =
    CompanionInspectionConfiguration(
        registration = companionRegistrationFixture(),
        mode = CompanionInspectionMode.DEVICE_VPN,
        unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
        fullHttpsInspection = false,
    )
