package com.devuloopers.knet.engine.portal

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class MobilePortalTest {

    @Test
    fun testAppleProfileGeneratorProducesValidPlist() {
        val ca = CertificateAuthority.generate()
        val xml = AppleProfileGenerator.generateMobileConfig(ca.certificate)

        assertTrue(xml.contains("<?xml version=\"1.0\""))
        assertTrue(xml.contains("com.apple.security.root"))
        assertTrue(xml.contains("<string>KNet Root CA</string>"))
    }

    @Test
    fun testPortalHtmlRendererProducesValidHtml() {
        val html = PortalHtmlRenderer.renderSetupPage("192.168.1.100", 8080)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("192.168.1.100"))
        assertTrue(html.contains("8080"))
        assertTrue(html.contains("/knet-ca.mobileconfig"))
        assertTrue(html.contains("/knet-ca.crt"))
    }

    @Test
    fun testMobilePortalHandlerInterceptsSetupRequest() {
        val ca = CertificateAuthority.generate()
        val handler = MobilePortalHandler(ca = ca, proxyPort = 8080)
        val channel = EmbeddedChannel(handler)

        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/setup"
        )
        request.headers().set(HttpHeaderNames.HOST, "192.168.1.100:8080")

        channel.writeInbound(request)
        val response = channel.readOutbound<FullHttpResponse>()

        assertNotNull(response)
        assertEquals(HttpResponseStatus.OK, response.status())
        assertEquals("text/html; charset=UTF-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE))
    }

    @Test
    fun testMobilePortalHandlerServesAppleConfigForIosUserAgent() {
        val ca = CertificateAuthority.generate()
        val handler = MobilePortalHandler(ca = ca, proxyPort = 8080)
        val channel = EmbeddedChannel(handler)

        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/ca"
        )
        request.headers().set(HttpHeaderNames.HOST, "knet.local")
        request.headers().set(HttpHeaderNames.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")

        channel.writeInbound(request)
        val response = channel.readOutbound<FullHttpResponse>()

        assertNotNull(response)
        assertEquals(HttpResponseStatus.OK, response.status())
        assertEquals("application/x-apple-aspen-config", response.headers().get(HttpHeaderNames.CONTENT_TYPE))
    }

    @Test
    fun testLiveServerPortalEndpoints() {
        val ca = CertificateAuthority.generate()
        val cache = CertificateCache()
        val testPort = java.net.ServerSocket(0).use { it.localPort }

        KNetProxyServer.pipelineInitializers.clear()
        KNetProxyServer.pipelineInitializers.add { pipeline ->
            pipeline.addLast("mobilePortalHandler", MobilePortalHandler(ca = ca, proxyPort = testPort))
        }

        val server = KNetProxyServer(port = testPort, ca = ca, certCache = cache)
        server.start()

        try {
            // Test GET /setup
            val setupUrl = URI.create("http://127.0.0.1:$testPort/setup").toURL()
            val conn = setupUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            assertEquals(200, conn.responseCode)
            val html = conn.inputStream.bufferedReader().readText()
            assertTrue(html.contains("<!DOCTYPE html>"))

            // Test GET /knet-ca.crt
            val certUrl = URI.create("http://127.0.0.1:$testPort/knet-ca.crt").toURL()
            val certConn = certUrl.openConnection() as HttpURLConnection
            certConn.requestMethod = "GET"
            assertEquals(200, certConn.responseCode)
            assertEquals("application/x-x509-ca-cert", certConn.contentType)

            // Test GET /knet-ca.mobileconfig
            val configUrl = URI.create("http://127.0.0.1:$testPort/knet-ca.mobileconfig").toURL()
            val configConn = configUrl.openConnection() as HttpURLConnection
            configConn.requestMethod = "GET"
            assertEquals(200, configConn.responseCode)
            assertEquals("application/x-apple-aspen-config", configConn.contentType)

            // Test GET /favicon.ico
            val faviconUrl = URI.create("http://127.0.0.1:$testPort/favicon.ico").toURL()
            val faviconConn = faviconUrl.openConnection() as HttpURLConnection
            faviconConn.requestMethod = "GET"
            assertEquals(204, faviconConn.responseCode)
        } finally {
            server.stop()
        }
    }

    @Test
    fun testFaviconReturnsNoContent() {
        val ca = CertificateAuthority.generate()
        val handler = MobilePortalHandler(ca = ca, proxyPort = 8080)
        val channel = EmbeddedChannel(handler)

        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/favicon.ico"
        )
        request.headers().set(HttpHeaderNames.HOST, "127.0.0.1:8080")

        channel.writeInbound(request)
        val response = channel.readOutbound<FullHttpResponse>()

        assertNotNull(response)
        assertEquals(HttpResponseStatus.NO_CONTENT, response.status())
    }
}
