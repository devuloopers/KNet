package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.ssl.JdkSslContext
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import javax.net.ssl.SSLEngineResult.HandshakeStatus

/** Bounded evidence about the downstream certificate flight, independent of context selection. */
enum class TlsCertificatePresentation {
    /** The supported provider has not completed a certificate-bearing outbound flight. */
    NOT_PRESENTED,
    /** The certificate-bearing server flight drained from the engine and its transport write succeeded. */
    PRESENTED,
    /** The provider cannot establish presentation using the supported observation mechanism. */
    UNKNOWN,
}

/** Event-loop-confined certificate-flight state. No certificates, keys or encrypted payloads are retained. */
internal class DownstreamTlsEvidence(private val resumption: TlsResumptionOffer = TlsResumptionOffer.UNKNOWN) {
    private var supportedProvider = false

    /** Current transport evidence; unavailable providers cannot become eligible through a generic write. */
    var presentation: TlsCertificatePresentation = TlsCertificatePresentation.UNKNOWN
        private set

    /** Enables the audited OpenJDK JSSE handshake-session contract, not arbitrary SSL providers. */
    fun contextInstalled(context: SslContext) {
        supportedProvider = resumption == TlsResumptionOffer.NONE && context is JdkSslContext && context.context().provider.name == "SunJSSE"
        presentation = if (supportedProvider) TlsCertificatePresentation.NOT_PRESENTED else TlsCertificatePresentation.UNKNOWN
    }

    /** Whether this completed engine wrap ends a certificate-authenticated server flight. */
    fun completesCertificateFlight(handler: SslHandler): Boolean {
        if (!supportedProvider || presentation == TlsCertificatePresentation.PRESENTED) return false
        val engine = handler.engine()
        if (engine.useClientMode || engine.isOutboundDone || engine.handshakeStatus != HandshakeStatus.NEED_UNWRAP) return false
        val session = runCatching { engine.handshakeSession }.getOrNull() ?: return false
        return session.protocol in setOf("TLSv1.2", "TLSv1.3") &&
            !runCatching { session.localCertificates }.getOrNull().isNullOrEmpty()
    }

    /** Called only after the corresponding complete-flight transport write succeeds. */
    fun flightWritten() {
        if (supportedProvider) presentation = TlsCertificatePresentation.PRESENTED
    }
}

/**
 * Observes encrypted writes before SslHandler, without copying or decoding their contents.
 *
 * OpenJDK JSSE installs local certificate metadata while producing its Certificate message. Waiting for
 * NEED_UNWRAP also requires its outbound flight to have drained; successful ChannelPromise completion then proves
 * submission to the downstream transport. A context, partial flight, alert-only write, or failed write is insufficient.
 * This establishes transmission, not receipt, certificate trust, pinning or application success.
 */
internal class TlsCertificateFlightObserver(private val evidence: DownstreamTlsEvidence) : ChannelOutboundHandlerAdapter() {
    override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
        val handler = context.pipeline().get(SslHandler::class.java)
        if (message is ByteBuf && message.isReadable && handler != null && evidence.completesCertificateFlight(handler)) {
            val observed = promise.unvoid()
            observed.addListener { result -> if (result.isSuccess) evidence.flightWritten() }
            context.write(message, observed)
        } else {
            context.write(message, promise)
        }
    }
}
