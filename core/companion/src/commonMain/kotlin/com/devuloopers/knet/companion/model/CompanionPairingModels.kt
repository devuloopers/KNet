package com.devuloopers.knet.companion.model

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation

/**
 * Small secret-bearing bootstrap reference intended for QR and deep-link transport.
 *
 * The complete certificate and pairing configuration are deliberately absent. A client first downloads the
 * public KNet root from [rootCertificateEndpoint], authenticates it with [rootCertificateSha256], and then uses
 * platform PKIX trust to redeem [retrievalSecret] at [retrievalEndpoint]. [transportIdentitySha256] remains an
 * additional exact peer-chain identity check after TLS negotiation.
 */
public data class CompanionPairingBootstrap(
    public val protocolVersion: Int,
    public val id: CompanionBootstrapId,
    public val retrievalSecret: CompanionBootstrapSecret,
    public val expiresAtEpochMillis: Long,
    public val rootCertificateEndpoint: CompanionServiceEndpoint,
    public val retrievalEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
) {
    init {
        require(protocolVersion == CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION) {
            "Unsupported companion bootstrap protocol version."
        }
        require(expiresAtEpochMillis > 0L) { "Companion bootstrap expiry must be positive." }
        require(rootCertificateEndpoint.scheme == CompanionEndpointScheme.HTTP) {
            "Companion bootstrap root endpoint must use open HTTP."
        }
        require(retrievalEndpoint.scheme == CompanionEndpointScheme.HTTPS) {
            "Companion bootstrap retrieval endpoint must be secure."
        }
    }
}

/** Secret-bearing, short-lived invitation decoded only in memory. */
public data class CompanionPairingInvitation(
    public val protocolVersion: Int,
    public val desktopId: CompanionDesktopId,
    public val desktopDisplayName: CompanionDesktopDisplayName,
    public val pairing: PairingInvitation,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val proxyEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
    public val rootCertificate: CompanionRootCertificate,
) {
    init {
        require(protocolVersion == CURRENT_PROTOCOL_VERSION) { "Unsupported companion protocol version." }
        require(controlEndpoint.scheme == CompanionEndpointScheme.HTTPS) {
            "Companion control endpoint must be secure."
        }
        require(proxyEndpoint.scheme == CompanionEndpointScheme.HTTPS) {
            "Companion proxy endpoint must be secure."
        }
    }

    public companion object {
        /** Current companion invitation and registration protocol version. */
        public const val CURRENT_PROTOCOL_VERSION: Int = 3
    }
}

/** Shared transport constants for lightweight invitation retrieval. */
public object CompanionBootstrapProtocol {
    public const val ROOT_CERTIFICATE_PATH: String = "/knet-ca.crt"
    public const val ROOT_CERTIFICATE_MEDIA_TYPE: String = "application/x-x509-ca-cert"
    public const val REDEEM_PATH: String = "/companion/v3/invitations/redeem"
    public const val REQUEST_MEDIA_TYPE: String = "application/vnd.knet.companion-bootstrap-request"
    public const val RESPONSE_MEDIA_TYPE: String = "application/vnd.knet.companion-invitation"
    public const val MAXIMUM_REQUEST_BYTES: Int = 2 * 1024
    public const val MAXIMUM_RESPONSE_BYTES: Int = 32 * 1024
}

/** Non-secret durable registration; credential material is referenced but never embedded. */
public data class CompanionRegistration(
    public val desktopId: CompanionDesktopId,
    public val desktopDisplayName: CompanionDesktopDisplayName,
    public val deviceId: RegisteredDeviceId,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val proxyEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
    public val rootCertificate: CompanionRootCertificate,
    public val credentialReference: CompanionCredentialReference,
    public val scopes: Set<DeviceScope>,
    public val pairedAtEpochMillis: Long,
    public val credentialExpiresAtEpochMillis: Long,
) {
    init {
        require(controlEndpoint.scheme == CompanionEndpointScheme.HTTPS &&
            proxyEndpoint.scheme == CompanionEndpointScheme.HTTPS
        ) { "Companion registrations require secure endpoints." }
        require(scopes.isNotEmpty()) { "A companion registration must grant at least one scope." }
        require(pairedAtEpochMillis >= 0L)
        require(credentialExpiresAtEpochMillis > pairedAtEpochMillis)
    }
}

/** Public device identity with a platform-protected signing-key handle. */
public data class CompanionDeviceIdentity(
    public val deviceId: RegisteredDeviceId,
    public val proofAlgorithm: DeviceProofAlgorithm,
    public val publicKeyEncoded: String,
    public val privateKeyReference: String,
) {
    init {
        require(publicKeyEncoded.length in 1..16_384 && publicKeyEncoded.none(Char::isControlCharacter))
        require(privateKeyReference.length in 1..256 && privateKeyReference == privateKeyReference.trim())
        require(privateKeyReference.none(Char::isControlCharacter))
    }
}

private fun Char.isControlCharacter(): Boolean = code in 0..31 || code == 127
