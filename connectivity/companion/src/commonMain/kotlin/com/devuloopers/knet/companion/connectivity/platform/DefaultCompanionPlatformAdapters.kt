package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource

/** Internal immutable adapter bundle with one idempotent platform cleanup operation. */
internal class DefaultCompanionPlatformAdapters(
    override val networkObserver: CompanionNetworkObserver,
    override val invitationResolver: CompanionInvitationResolver,
    override val controlTransport: CompanionControlTransport,
    override val rootCertificateSource: CompanionRootCertificateSource,
    override val trustVerifier: CompanionCertificateTrustVerifier,
    override val certificateStoreChanges: CompanionCertificateStoreChangeObserver,
    override val inspectionController: CompanionInspectionController,
    private val closePlatform: () -> Unit,
) : CompanionPlatformAdapters {
    private var closed: Boolean = false

    override fun close() {
        if (closed) return
        closed = true
        closePlatform()
    }
}
