package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KNetProxyHandlerTest {

    private val ca = CertificateAuthority.generate()
    private val certCache = CertificateCache()

    @Test
    fun testConnectRequestReturns200ConnectionEstablished() {
        val channel = EmbeddedChannel(KNetProxyHandler(ca, certCache))
        val connectReq = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "httpbin.org:443")

        channel.writeInbound(connectReq)

        val response = channel.readOutbound<HttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.OK, response.status())
        channel.close()
    }
}
