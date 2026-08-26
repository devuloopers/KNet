package com.devuloopers.knet.companion.connectivity.fallback

import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.model.CompanionCertificateState
import kotlinx.coroutines.flow.emptyFlow

/** Fail-closed certificate capabilities used until a native platform implementation is available. */
internal class UnavailableCompanionCertificateAdapters(platformName: String) {
    private val retrievalFailure = unavailablePlatformCapability(platformName, "certificate retrieval")
    private val verificationFailure = unavailablePlatformCapability(platformName, "certificate trust verification")

    val rootCertificateSource: CompanionRootCertificateSource = CompanionRootCertificateSource { _, _ ->
        CompanionCertificateDownloadResult.Failed(retrievalFailure)
    }

    val trustVerifier: CompanionCertificateTrustVerifier = CompanionCertificateTrustVerifier { _, _, _ ->
        CompanionCertificateState.Rejected(verificationFailure)
    }

    val storeChanges: CompanionCertificateStoreChangeObserver = CompanionCertificateStoreChangeObserver {
        emptyFlow()
    }
}
