package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.traffic.model.InspectionFailureReason
import javax.net.ssl.SSLHandshakeException

/** Payload-free failure evidence supplied by the downstream TLS pipeline. */
data class TlsInspectionFailure(
    /** A supported alert classification, never an inferred pinning claim. */
    val reason: InspectionFailureReason,
    /** Actual supported certificate-flight observation, not the existence of a TLS context. */
    val presentation: TlsCertificatePresentation,
) {
    /** Whether the downstream certificate-bearing flight was submitted successfully to the transport. */
    val certificatePresented: Boolean get() = presentation == TlsCertificatePresentation.PRESENTED
}

/** Maps only known certificate-alert formats; ordinary handshake errors remain inconclusive. */
internal object TlsInspectionFailureClassifier {
    /** Classifies a downstream failure without retaining exception messages or certificate material. */
    fun classify(
        cause: Throwable?,
        certificateContextInstalled: Boolean,
        presentation: TlsCertificatePresentation,
    ): TlsInspectionFailure {
        val causes = generateSequence(cause) { it.cause }.take(8).toList()
        val rejection = causes.filterIsInstance<SSLHandshakeException>().any { failure ->
            failure.message in certificateAlerts
        }
        return TlsInspectionFailure(
            reason = when {
                !certificateContextInstalled -> InspectionFailureReason.CERTIFICATE_SETUP
                rejection -> InspectionFailureReason.CERTIFICATE_REJECTED
                else -> InspectionFailureReason.UNKNOWN_HANDSHAKE
            },
            presentation = if (certificateContextInstalled) presentation else TlsCertificatePresentation.NOT_PRESENTED,
        )
    }

    private val certificateAlerts = listOf("unknown_ca", "certificate_unknown", "bad_certificate").flatMap { alert ->
        // JDK 17+ prefixes the protocol alert name; older providers expose the unprefixed form.
        listOf("Received fatal alert: $alert", "($alert) Received fatal alert: $alert")
    }.toSet()
}
