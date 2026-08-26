package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapPayloadCodec
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation

/**
 * Secret-bearing onboarding data; presentations must avoid logs, analytics, and persistent state.
 *
 * @property desktopDisplayName bounded name shown during companion confirmation.
 * @property controlEndpoint reachable secure endpoint shown in the desktop guidance.
 * @property expiresAtEpochMillis absolute expiry used by the presentation countdown.
 * @property deepLink canonical versioned link containing the bootstrap only.
 * @property qrPayload exact canonical content encoded into the QR image.
 */
public data class PairingOnboardingDescriptor(
    public val desktopDisplayName: String,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val expiresAtEpochMillis: Long,
    public val deepLink: String,
    public val qrPayload: String,
) {
    init {
        require(desktopDisplayName.isNotBlank()) { "Desktop display name must not be blank." }
        require(controlEndpoint.secure) { "Companion onboarding control endpoint must be secure." }
        require(expiresAtEpochMillis > 0L) { "Companion onboarding expiry must be positive." }
        require(deepLink.isNotBlank() && qrPayload.isNotBlank()) { "Companion onboarding payload must not be blank." }
    }
}

/**
 * Current, product-supplied desktop identity and reachable companion service endpoints.
 *
 * This public configuration contains certificate material only; it must never contain the KNet CA private key,
 * an invitation secret, or a durable device credential.
 *
 * @property desktopId stable desktop installation identity.
 * @property desktopDisplayName bounded name shown during companion confirmation.
 * @property rootCertificateEndpoint open Wi-Fi portal endpoint serving only the public KNet root.
 * @property controlEndpoint pinned TLS endpoint used for redemption and later control operations.
 * @property proxyEndpoint authenticated proxy data-plane endpoint advertised after pairing.
 * @property transportIdentitySha256 exact expected identity in the control endpoint certificate chain.
 * @property rootCertificateSha256 exact fingerprint of [rootCertificate].
 * @property rootCertificate defensive public root material included in the complete invitation.
 */
public data class PairingOnboardingEnvironment(
    public val desktopId: CompanionDesktopId,
    public val desktopDisplayName: String,
    public val rootCertificateEndpoint: CompanionServiceEndpoint,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val proxyEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
    public val rootCertificate: CompanionRootCertificate,
) {
    init {
        require(desktopDisplayName.isNotBlank() && desktopDisplayName.length <= 128) {
            "Desktop display name must contain 1 to 128 characters."
        }
        require(!rootCertificateEndpoint.secure) { "The public root endpoint must use open HTTP." }
    }
}

/** Resolves product-owned desktop identity, endpoint, and public certificate details at invitation creation time. */
public fun interface PairingOnboardingEnvironmentProvider {
    /** Returns the current reachable environment or throws when companion onboarding is unavailable. */
    public suspend fun load(): PairingOnboardingEnvironment
}

/** Creates a complete short-lived invitation and publishes a lightweight version-3 bootstrap QR reference. */
public class CreatePairingOnboardingUseCase(
    private val pairing: PairingCoordinator,
    private val environmentProvider: PairingOnboardingEnvironmentProvider,
    private val cryptography: PairingCryptography,
    private val onboardingStore: CompanionOnboardingStore,
    private val payloadCodec: CompanionBootstrapPayloadCodec,
) {
    /**
     * Creates one secret-bearing invitation for [scopes].
     *
     * The returned descriptor is intended for immediate in-memory presentation and must not be persisted or
     * logged. A later call creates a separate one-time invitation with its own expiry.
     */
    public suspend fun execute(
        scopes: Set<DeviceScope> = setOf(DeviceScope.PROXY_STREAM, DeviceScope.SETUP_ARTIFACT_READ),
    ): PairingOnboardingDescriptor {
        val environment = environmentProvider.load()
        val pairingInvitation: PairingInvitation = pairing.createInvitation(scopes)
        val invitation = CompanionPairingInvitation(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            desktopId = environment.desktopId,
            desktopDisplayName = environment.desktopDisplayName,
            pairing = pairingInvitation,
            controlEndpoint = environment.controlEndpoint,
            proxyEndpoint = environment.proxyEndpoint,
            transportIdentitySha256 = environment.transportIdentitySha256,
            rootCertificateSha256 = environment.rootCertificateSha256,
            rootCertificate = environment.rootCertificate,
        )
        val bootstrapSecret = CompanionBootstrapSecret(cryptography.randomToken(32))
        val bootstrap = CompanionPairingBootstrap(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            id = CompanionBootstrapId(cryptography.randomToken(18)),
            retrievalSecret = bootstrapSecret,
            expiresAtEpochMillis = pairingInvitation.expiresAtEpochMillis,
            rootCertificateEndpoint = environment.rootCertificateEndpoint,
            retrievalEndpoint = environment.controlEndpoint,
            transportIdentitySha256 = environment.transportIdentitySha256,
            rootCertificateSha256 = environment.rootCertificateSha256,
        )
        onboardingStore.put(
            PendingCompanionOnboarding(
                id = bootstrap.id,
                retrievalSecretDigest = cryptography.digest(bootstrapSecret.value),
                expiresAtEpochMillis = bootstrap.expiresAtEpochMillis,
                invitation = invitation,
            ),
        )
        val payload = payloadCodec.encode(bootstrap)
        return PairingOnboardingDescriptor(
            desktopDisplayName = environment.desktopDisplayName,
            controlEndpoint = environment.controlEndpoint,
            expiresAtEpochMillis = pairingInvitation.expiresAtEpochMillis,
            deepLink = payload,
            qrPayload = payload,
        )
    }
}
