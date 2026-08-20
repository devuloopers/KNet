package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.handler.KNetStreamingProxyHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HttpsConnectTest {

    private val ca = CertificateAuthority.generate()
    private val certCache = CertificateCache()
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun closeScope() {
        proxyScope.cancel()
    }

    @Test
    fun testHttpsConnectHandshakeInitiation() {
        val channel = EmbeddedChannel(KNetStreamingProxyHandler(ca, certCache, proxyScope))
        val connectReq = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "api.github.com:443")

        channel.writeInbound(connectReq)

        val res = channel.readOutbound<HttpResponse>()
        assertNotNull(res)
        assertEquals(HttpResponseStatus.OK, res.status())
        channel.close()
    }
}
