package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.certificate.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.ssl.*
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlin.test.*

/** Genuine resumed JSSE sessions may expose old local certificates but must never count as new certificate flights. */
class TlsResumedCertificateFlightTest {
    @Test fun `resumed TLS 12 does not fabricate certificate presentation`() = exercise("TLSv1.2")
    @Test fun `resumed TLS 13 does not fabricate certificate presentation`() = exercise("TLSv1.3")

    private fun exercise(protocol: String) {
        val leaf = CertificateCache().get("resume.example.test", CertificateAuthority.generate())
        val serverContext = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).sslProvider(SslProvider.JDK).protocols(protocol).build()
        val clientContext = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).protocols(protocol)
            .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val channels = mutableListOf<EmbeddedChannel>()
        fun pair(offer: TlsResumptionOffer): Triple<EmbeddedChannel, EmbeddedChannel, DownstreamTlsEvidence> {
            val evidence = DownstreamTlsEvidence(offer).also { it.contextInstalled(serverContext) }
            val server = EmbeddedChannel(TlsCertificateFlightObserver(evidence), serverContext.newHandler(UnpooledByteBufAllocator.DEFAULT))
            val client = EmbeddedChannel(clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT, "resume.example.test", 443))
            channels += server; channels += client
            return Triple(server, client, evidence)
        }
        try {
            val (firstServer, firstClient, first) = pair(TlsResumptionOffer.NONE)
            pump(firstServer, firstClient)
            assertTrue(firstServer.pipeline().get(SslHandler::class.java).handshakeFuture().isSuccess)
            assertTrue(firstClient.pipeline().get(SslHandler::class.java).handshakeFuture().isSuccess)
            assertEquals(TlsCertificatePresentation.PRESENTED, first.presentation)
            val (server, client, resumed) = pair(TlsResumptionOffer.OFFERED)
            client.runPendingTasks()
            val hello = assertNotNull(client.readOutbound<ByteBuf>())
            val offer = TlsClientHelloMetadataParser.parse(hello.slice(9, hello.getUnsignedMedium(6))).resumptionOffer
            assertEquals(TlsResumptionOffer.OFFERED, offer, "The fixture must genuinely offer the cached session")
            server.writeInbound(hello)
            pump(server, client)
            assertTrue(server.pipeline().get(SslHandler::class.java).handshakeFuture().isSuccess)
            assertTrue(client.pipeline().get(SslHandler::class.java).handshakeFuture().isSuccess)
            assertEquals(TlsCertificatePresentation.UNKNOWN, resumed.presentation)
        } finally { channels.forEach { runCatching { it.finishAndReleaseAll() } } }
    }

    private fun pump(server: EmbeddedChannel, client: EmbeddedChannel) {
        repeat(100) {
            server.runPendingTasks(); client.runPendingTasks()
            var progress = false
            while (true) {
                val message = client.readOutbound<ByteBuf>() ?: break
                server.writeInbound(message); progress = true
            }
            while (true) {
                val message = server.readOutbound<ByteBuf>() ?: break
                client.writeInbound(message); progress = true
            }
            if (!progress && server.pipeline().get(SslHandler::class.java).handshakeFuture().isDone &&
                client.pipeline().get(SslHandler::class.java).handshakeFuture().isDone) return
        }
        error("TLS fixture did not settle")
    }
}
