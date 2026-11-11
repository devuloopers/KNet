package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.traffic.model.InspectionFailureReason
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.ssl.SniCompletionEvent
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import javax.net.ssl.SSLHandshakeException
import kotlin.test.*

/** Closed alert mapping and exactly-once downstream observation tests. */
class TlsInspectionFailureClassifierTest {
    @Test fun `only supported received certificate alerts with actual presentation are eligible`() {
        listOf("unknown_ca", "certificate_unknown", "bad_certificate").forEach { alert ->
            val cause = SSLHandshakeException("Received fatal alert: $alert")
            assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED, TlsInspectionFailureClassifier.classify(cause, true, TlsCertificatePresentation.PRESENTED).reason)
            assertFalse(TlsInspectionFailureClassifier.classify(cause, false, TlsCertificatePresentation.PRESENTED).certificatePresented)
            assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED, TlsInspectionFailureClassifier.classify(SSLHandshakeException("($alert) Received fatal alert: $alert"), true, TlsCertificatePresentation.PRESENTED).reason)
            listOf(TlsCertificatePresentation.UNKNOWN, TlsCertificatePresentation.NOT_PRESENTED).forEach { evidence ->
                assertFalse(TlsInspectionFailureClassifier.classify(cause, true, evidence).certificatePresented)
            }
        }
        listOf("Received fatal alert: handshake_failure", "Received fatal alert: protocol_version", "certificate pinning probably failed", "timeout").forEach {
            assertEquals(InspectionFailureReason.UNKNOWN_HANDSHAKE,
                TlsInspectionFailureClassifier.classify(SSLHandshakeException(it), true, TlsCertificatePresentation.PRESENTED).reason)
        }
    }

    @Test fun `SNI context and duplicate failure events alone cannot prove certificate transmission`() {
        val observed = mutableListOf<TlsInspectionFailure>()
        val channel = EmbeddedChannel(TlsInspectionOutcomeHandler { observed += it })
        try {
            channel.pipeline().fireUserEventTriggered(SniCompletionEvent("example.com"))
            repeat(2) { channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent(SSLHandshakeException("Received fatal alert: unknown_ca"))) }
            assertEquals(1, observed.size)
            assertFalse(observed.single().certificatePresented)
            assertEquals(TlsCertificatePresentation.UNKNOWN, observed.single().presentation)
        } finally { channel.finishAndReleaseAll() }
    }
}
