package com.devuloopers.knet.engine.portal

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import java.net.InetAddress
import java.net.NetworkInterface

private const val TAG = "MobilePortalHandler"

/**
 * Netty channel inbound handler that intercepts requests targeting KNet's mobile setup portal.
 *
 * Serves the responsive setup web portal page (`/setup`), X.509 DER CA certificate (`/knet-ca.crt`),
 * Apple Configuration Profile (`/knet-ca.mobileconfig`), and favicon requests directly from KNet's proxy engine port.
 * Also acts as a guard against infinite self-proxy loops by intercepting and resolving all requests targeting
 * KNet's own host IP address locally.
 *
 * @property ca The active [CertificateAuthority] managing KNet's Root CA certificate.
 * @property proxyPort The port KNet Netty server is bound to (default: 8080).
 */
class MobilePortalHandler(
    private val ca: CertificateAuthority,
    private val proxyPort: Int = 8080
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        val hostHeader = request.headers().get(HttpHeaderNames.HOST) ?: ""

        if (isPortalRequest(uri, hostHeader)) {
            KNetLogger.info(TAG) { "[PORTAL MATCH] Intercepted mobile setup portal request: $uri (Host: $hostHeader)" }
            handlePortalRoute(context, request)
        } else {
            // Forward non-portal requests down the Netty pipeline
            request.retain()
            context.fireChannelRead(request)
        }
    }

    /**
     * Evaluates whether an incoming HTTP request targets KNet's mobile onboarding portal or local host address.
     */
    private fun isPortalRequest(uri: String, hostHeader: String): Boolean {
        val cleanHost = hostHeader.substringBefore(":")
        val isLocalHostName = cleanHost.equals("knet.local", ignoreCase = true) ||
                cleanHost == "127.0.0.1" ||
                cleanHost == "localhost" ||
                isLocalMachineIp(cleanHost)

        return isLocalHostName ||
                uri == "/setup" ||
                uri == "/knet-ca.crt" ||
                uri == "/knet-ca.mobileconfig" ||
                uri == "/ca" ||
                uri == "/favicon.ico"
    }

    /**
     * Routes and constructs full HTTP responses for mobile portal endpoints.
     */
    private fun handlePortalRoute(context: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        val userAgent = request.headers().get(HttpHeaderNames.USER_AGENT) ?: ""

        when {
            uri == "/favicon.ico" -> {
                serveFavicon(context)
            }
            uri == "/knet-ca.mobileconfig" || (uri == "/ca" && isAppleDevice(userAgent)) -> {
                serveMobileConfig(context)
            }
            uri == "/knet-ca.crt" || uri == "/ca" -> {
                serveCaCertificate(context)
            }
            uri == "/setup" || uri == "/" || isPortalRequest(uri, request.headers().get(HttpHeaderNames.HOST) ?: "") -> {
                serveSetupPage(context, request)
            }
            else -> {
                serveNotFound(context)
            }
        }
    }

    /**
     * Determines whether the requesting client is an iOS/Apple device based on `User-Agent`.
     */
    private fun isAppleDevice(userAgent: String): Boolean {
        val ua = userAgent.lowercase()
        return ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod") || ua.contains("cfnetwork")
    }

    /**
     * Serves `/favicon.ico` with `204 No Content` to satisfy browser icon fetches cleanly.
     */
    private fun serveFavicon(context: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.NO_CONTENT
        )
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400")
        context.writeAndFlush(response)
    }

    /**
     * Serves the HTML onboarding setup page.
     */
    private fun serveSetupPage(context: ChannelHandlerContext, request: FullHttpRequest) {
        val hostHeader = request.headers().get(HttpHeaderNames.HOST)
        val localIp = resolveLocalIp(hostHeader)
        val htmlContent = PortalHtmlRenderer.renderSetupPage(localIp, proxyPort)
        val bodyBytes = htmlContent.toByteArray(Charsets.UTF_8)

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(bodyBytes)
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBytes.size)

        context.writeAndFlush(response)
    }

    /**
     * Serves KNet's Root CA certificate in DER format (`application/x-x509-ca-cert`).
     */
    private fun serveCaCertificate(context: ChannelHandlerContext) {
        val certBytes = ca.certificate.encoded

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(certBytes)
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-x509-ca-cert")
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"knet-ca.crt\"")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, certBytes.size)

        context.writeAndFlush(response)
    }

    /**
     * Serves Apple `.mobileconfig` Property List XML (`application/x-apple-aspen-config`).
     */
    private fun serveMobileConfig(context: ChannelHandlerContext) {
        val xmlContent = AppleProfileGenerator.generateMobileConfig(ca.certificate)
        val xmlBytes = xmlContent.toByteArray(Charsets.UTF_8)

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(xmlBytes)
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-apple-aspen-config")
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"knet-ca.mobileconfig\"")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, xmlBytes.size)

        context.writeAndFlush(response)
    }

    /**
     * Serves `404 Not Found` for unhandled paths targeting KNet's own host address.
     */
    private fun serveNotFound(context: ChannelHandlerContext) {
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.NOT_FOUND
        )
        context.writeAndFlush(response)
    }

    /**
     * Checks if the given host string corresponds to a local machine network interface IP.
     */
    private fun isLocalMachineIp(host: String): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement().hostAddress == host) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves the active LAN IP address of the host machine.
     * Uses incoming Host header if valid IP, or scans active non-loopback network interfaces.
     */
    private fun resolveLocalIp(hostHeader: String? = null): String {
        if (!hostHeader.isNullOrEmpty()) {
            val cleanHost = hostHeader.substringBefore(":")
            if (cleanHost != "localhost" && cleanHost != "127.0.0.1" && cleanHost != "knet.local") {
                return cleanHost
            }
        }
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
