package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery

/**
 * Complete portable view of the connectivity capabilities supplied by one native companion platform.
 *
 * Implementations own their callback registrations and native resources until [close] is called. Every capability
 * remains expressed through application contracts, so common consumers never receive a native platform handle.
 */
public interface CompanionPlatformAdapters : AutoCloseable {
    /** Observes whether the platform currently has a usable network route. */
    public val networkObserver: CompanionNetworkObserver

    /** Resolves the currently paired desktop after local address changes. */
    public val desktopDiscovery: CompanionDesktopDiscovery

    /** Retrieves and authenticates a complete invitation from a lightweight scanned bootstrap. */
    public val invitationResolver: CompanionInvitationResolver

    /** Executes pairing and credential-rotation requests through authenticated pinned TLS. */
    public val controlTransport: CompanionControlTransport

    /** Retrieves the authenticated KNet root certificate advertised by the paired desktop. */
    public val rootCertificateSource: CompanionRootCertificateSource

    /** Proves whether the paired KNet root is trusted by the native platform. */
    public val trustVerifier: CompanionCertificateTrustVerifier

    /** Emits native certificate-store changes as re-verification triggers only. */
    public val certificateStoreChanges: CompanionCertificateStoreChangeObserver

    /** Controls the platform packet-inspection lifecycle. */
    public val inspectionController: CompanionInspectionController

    /** Releases every native callback and resource owned by this adapter bundle; repeated calls must be safe. */
    public override fun close()
}
