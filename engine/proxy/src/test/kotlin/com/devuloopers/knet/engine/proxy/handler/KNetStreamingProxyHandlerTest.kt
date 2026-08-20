package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
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

class KNetStreamingProxyHandlerTest {
    private val certificateAuthority = CertificateAuthority.generate()
    private val certificateCache = CertificateCache()
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun closeScope() {
        proxyScope.cancel()
    }

    @Test
    fun `valid CONNECT returns connection established`() {
        val channel = EmbeddedChannel(
            KNetStreamingProxyHandler(certificateAuthority, certificateCache, proxyScope),
        )
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "httpbin.org:443")

        channel.writeInbound(request)

        val response = channel.readOutbound<HttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.OK, response.status())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `invalid CONNECT authority returns bad request`() {
        val channel = EmbeddedChannel(
            KNetStreamingProxyHandler(certificateAuthority, certificateCache, proxyScope),
        )
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.CONNECT,
            "example.com:70000",
        )

        channel.writeInbound(request)

        val response = channel.readOutbound<HttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status())
        channel.finishAndReleaseAll()
    }
}
