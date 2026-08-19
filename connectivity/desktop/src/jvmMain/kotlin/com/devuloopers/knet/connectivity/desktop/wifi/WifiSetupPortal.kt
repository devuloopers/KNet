package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Open exact-interface setup listener for stock phones on the same local network.
 *
 * It serves only KNet-owned setup resources and remains independent from the proxy request pipeline, UI,
 * persistence, and protocol inspection.
 */
internal class WifiSetupPortal(
    private val bindHost: String,
    private val bindPort: Int,
    private val proxyEndpoint: ProxyEndpoint,
    private val certificateDer: () -> ByteArray,
    private val certificateSha256: String,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    init {
        val address = InetAddress.getByName(bindHost)
        require(!address.isAnyLocalAddress) { "Wi-Fi setup portal cannot bind a wildcard address." }
        require(!address.isLoopbackAddress) { "Wi-Fi setup portal requires a non-loopback address." }
        require(bindPort in 1..65_535)
        require(proxyEndpoint.host == bindHost) { "Wi-Fi setup and proxy endpoints must use the same address." }
        require(certificateSha256.matches(Regex("[0-9a-f]{64}")))
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        var created: HttpServer? = null
        var worker: ExecutorService? = null
        try {
            val createdWorker = Executors.newFixedThreadPool(WORKER_COUNT) { task ->
                Thread(task, "knet-wifi-setup").apply { isDaemon = true }
            }
            worker = createdWorker
            created = HttpServer.create(InetSocketAddress(bindHost, bindPort), ACCEPT_BACKLOG)
            created.executor = createdWorker
            created.createContext("/", ::handle)
            created.start()
            executor = createdWorker
            server = created
        } catch (failure: Throwable) {
            running.set(false)
            created?.stop(0)
            worker?.shutdownNow()
            executor = null
            throw failure
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") {
                return exchange.respond(405, "text/plain; charset=utf-8", "method_not_allowed".encodeToByteArray())
            }
            if (parseAuthorityHost(exchange.requestHeaders.getFirst("Host")) != bindHost.lowercase()) {
                return exchange.respond(421, "text/plain; charset=utf-8", "unknown_setup_authority".encodeToByteArray())
            }
            when (exchange.requestURI.rawPath) {
                "/", "/setup" -> serveSetupPage(exchange)
                ANDROID_CERTIFICATE_PATH, "/ca" -> serveAndroidCertificate(exchange)
                APPLE_PROFILE_PATH -> serveAppleProfile(exchange)
                PAC_PATH -> exchange.respond(
                    status = 200,
                    mediaType = "application/x-ns-proxy-autoconfig",
                    body = generatePac().encodeToByteArray(),
                    filename = "knet-proxy.pac",
                )
                "/favicon.ico" -> exchange.respond(204, "image/x-icon", ByteArray(0))
                else -> exchange.respond(404, "text/plain; charset=utf-8", "not_found".encodeToByteArray())
            }
        } catch (_: Exception) {
            runCatching {
                exchange.respond(500, "text/plain; charset=utf-8", "setup_delivery_failed".encodeToByteArray())
            }
        } finally {
            exchange.close()
        }
    }

    private fun serveSetupPage(exchange: HttpExchange) {
        val page = WifiSetupPageRenderer.render(
            WifiSetupPageModel(
                proxyHost = bindHost,
                proxyPort = proxyEndpoint.port,
                certificateSha256 = certificateSha256,
            ),
        )
        exchange.respond(200, "text/html; charset=utf-8", page.encodeToByteArray())
    }

    private fun serveAndroidCertificate(exchange: HttpExchange) {
        val certificate = availableCertificate()
            ?: return exchange.respond(503, "text/plain; charset=utf-8", "certificate_unavailable".encodeToByteArray())
        exchange.respond(
            status = 200,
            mediaType = "application/x-x509-ca-cert",
            body = certificate,
            filename = "knet-ca.crt",
        )
    }

    private fun serveAppleProfile(exchange: HttpExchange) {
        val certificate = availableCertificate()
            ?: return exchange.respond(503, "text/plain; charset=utf-8", "certificate_unavailable".encodeToByteArray())
        exchange.respond(
            status = 200,
            mediaType = "application/x-apple-aspen-config",
            body = AppleRootCertificateProfileRenderer.render(certificate).encodeToByteArray(),
            filename = "knet-ca.mobileconfig",
        )
    }

    private fun availableCertificate(): ByteArray? = certificateDer()
        .takeIf { it.isNotEmpty() && it.size <= MAXIMUM_CERTIFICATE_BYTES }

    private fun generatePac(): String {
        val host = proxyEndpoint.host
        val authority = if (':' in host && !host.startsWith('[')) "[$host]" else host
        return "function FindProxyForURL(url, host) {\n  return \"PROXY $authority:${proxyEndpoint.port}; DIRECT\";\n}\n"
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private fun HttpExchange.respond(
        status: Int,
        mediaType: String,
        body: ByteArray,
        filename: String? = null,
    ) {
        responseHeaders.set("Content-Type", mediaType)
        responseHeaders.set("Cache-Control", "no-store")
        responseHeaders.set("Pragma", "no-cache")
        responseHeaders.set("X-Content-Type-Options", "nosniff")
        responseHeaders.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        responseHeaders.set("Referrer-Policy", "no-referrer")
        filename?.let { responseHeaders.set("Content-Disposition", "attachment; filename=\"$it\"") }
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun parseAuthorityHost(authority: String?): String? {
        val value = authority?.trim()?.lowercase()?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
            ?: return null
        if (value.startsWith('[')) {
            val close = value.indexOf(']')
            if (close <= 1 || (close + 1 < value.length && value[close + 1] != ':')) return null
            return value.substring(1, close)
        }
        return when (value.count { it == ':' }) {
            0 -> value
            1 -> value.substringBeforeLast(':').takeIf(String::isNotBlank)
            else -> value
        }
    }

    private companion object {
        const val ANDROID_CERTIFICATE_PATH: String = "/knet-ca.crt"
        const val APPLE_PROFILE_PATH: String = "/knet-ca.mobileconfig"
        const val PAC_PATH: String = "/proxy.pac"
        const val WORKER_COUNT: Int = 2
        const val ACCEPT_BACKLOG: Int = 32
        const val MAXIMUM_CERTIFICATE_BYTES: Int = 1024 * 1024
    }
}
