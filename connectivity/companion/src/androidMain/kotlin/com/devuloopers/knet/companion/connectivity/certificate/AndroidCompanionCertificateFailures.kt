package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode

internal fun rejectedAndroidCertificate(message: String): CompanionCertificateState.Rejected =
    CompanionCertificateState.Rejected(
        CompanionFailure(CompanionFailureCode.CERTIFICATE_NOT_TRUSTED, message, false),
    )

internal fun androidCertificateTransportUnavailable(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Unable to reach the paired desktop securely.",
    true,
)
