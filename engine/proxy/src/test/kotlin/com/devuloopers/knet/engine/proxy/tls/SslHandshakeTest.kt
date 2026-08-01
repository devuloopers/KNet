package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.handler.ssl.SslContextBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test

class SslHandshakeTest {

    @Test
    fun testSslServerContextCreationForDynamicLeaf() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val leaf = cache.get("api.stripe.com", ca)

        val sslContext = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build()
        val sslHandler = sslContext.newHandler(UnpooledByteBufAllocator.DEFAULT)

        assertNotNull(sslContext)
        assertNotNull(sslHandler)
        assertNotNull(sslHandler.engine())
    }
}
