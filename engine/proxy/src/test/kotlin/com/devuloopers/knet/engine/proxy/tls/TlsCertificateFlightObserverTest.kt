package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.util.ReferenceCountUtil
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises actual JSSE server flights with independently controlled transport completion. */
class TlsCertificateFlightObserverTest {
    @Test fun `TLS 12 certificate flight requires a successful transport completion`() = exercise("TLSv1.2", true)
    @Test fun `TLS 13 certificate flight requires a successful transport completion`() = exercise("TLSv1.3", true)
    @Test fun `failed TLS 12 flight cannot establish presentation`() = exercise("TLSv1.2", false)
    @Test fun `failed TLS 13 flight cannot establish presentation`() = exercise("TLSv1.3", false)
    @Test fun `offered session resumption never turns retained certificates into presentation evidence`() =
        exercise("TLSv1.3", true, TlsResumptionOffer.OFFERED)
    @Test fun `incomplete ClientHello evidence cannot establish presentation from encrypted output`() =
        exercise("TLSv1.2", true, TlsResumptionOffer.UNKNOWN)

    private fun exercise(protocol: String, writeSucceeds: Boolean, resumption: TlsResumptionOffer = TlsResumptionOffer.NONE) {
        val serverContext = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate)
            .sslProvider(SslProvider.JDK).protocols(protocol).build()
        val evidence = DownstreamTlsEvidence(resumption).also { it.contextInstalled(serverContext) }
        val transport = HeldTransport()
        val server = EmbeddedChannel(transport, TlsCertificateFlightObserver(evidence), serverContext.newHandler(io.netty.buffer.UnpooledByteBufAllocator.DEFAULT))
        val clientContext = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).protocols(protocol).build()
        val client = EmbeddedChannel(clientContext.newHandler(io.netty.buffer.UnpooledByteBufAllocator.DEFAULT, "flight.example.test", 443))
        try {
            client.runPendingTasks()
            val hello = client.readOutbound<ByteBuf>()
            assertTrue(hello != null && hello.isReadable)
            server.writeInbound(hello)
            server.runPendingTasks()
            assertTrue(transport.pending.isNotEmpty(), "JSSE must produce its server certificate flight")
            assertEquals(if (resumption == TlsResumptionOffer.NONE) TlsCertificatePresentation.NOT_PRESENTED else TlsCertificatePresentation.UNKNOWN, evidence.presentation,
                "Producing or buffering a certificate flight does not prove transmission")
            transport.complete(writeSucceeds)
            server.runPendingTasks()
            assertEquals(if (resumption != TlsResumptionOffer.NONE) TlsCertificatePresentation.UNKNOWN else if (writeSucceeds) TlsCertificatePresentation.PRESENTED else TlsCertificatePresentation.NOT_PRESENTED,
                evidence.presentation)
        } finally {
            transport.complete(false)
            runCatching { server.finishAndReleaseAll() }
            runCatching { client.finishAndReleaseAll() }
            transport.complete(false)
        }
    }

    private class HeldTransport : ChannelOutboundHandlerAdapter() {
        val pending = mutableListOf<Pair<Any, ChannelPromise>>()
        private var holding = true

        override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
            if (holding) pending += message to promise else {
                ReferenceCountUtil.release(message)
                promise.tryFailure(IOException("Test transport closed"))
            }
        }

        fun complete(success: Boolean) {
            holding = false
            val writes = pending.toList()
            pending.clear()
            writes.forEach { (message, promise) ->
                ReferenceCountUtil.release(message)
                if (success) promise.trySuccess() else promise.tryFailure(IOException("Test transport write failed"))
            }
        }
    }

    private companion object {
        val leaf by lazy { CertificateCache().get("flight.example.test", CertificateAuthority.generate()) }
    }
}
