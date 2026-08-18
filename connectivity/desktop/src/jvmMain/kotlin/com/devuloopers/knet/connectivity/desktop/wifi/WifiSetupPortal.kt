package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Token-bound LAN setup delivery kept separate from both the proxy pipeline and loopback setup routes. */
internal class WifiSetupPortal(
    private val bindHost: String,
    private val bindPort: Int,
    private val proxyEndpoint: ProxyEndpoint,
    private val certificateDer: () -> ByteArray,
    private val invitations: WifiInvitationService,
    private val approvals: WifiClientApprovalRegistry,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var server: HttpServer? = null
    private var executor: java.util.concurrent.ExecutorService? = null

    init {
        val address = InetAddress.getByName(bindHost)
        require(!address.isAnyLocalAddress) { "Wi-Fi setup portal cannot bind a wildcard address." }
        require(!address.isLoopbackAddress) { "Wi-Fi setup portal requires a non-loopback address." }
        require(bindPort in 1..65_535)
        require(proxyEndpoint.host == bindHost) { "Wi-Fi setup and proxy endpoints must use the same address." }
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
            val route = parseRoute(exchange.requestURI.rawPath)
                ?: return exchange.respond(404, "text/plain; charset=utf-8", "not_found".encodeToByteArray())
            val sourceAddress = exchange.remoteAddress.address.hostAddress.substringBefore('%')
            invitations.claim(route.token, sourceAddress)
                ?: return exchange.respond(404, "text/plain; charset=utf-8", "not_found".encodeToByteArray())
            val pending = approvals.observe(sourceAddress)
            val approved = approvals.approvedFor(sourceAddress)

            when (route.resource) {
                null -> exchange.respond(
                    200,
                    "text/html; charset=utf-8",
                    renderIndex(route.token, pending?.confirmationCode, approved != null).encodeToByteArray(),
                )
                CERTIFICATE_RESOURCE -> {
                    if (approved == null) return exchange.respond(403, "text/plain; charset=utf-8", APPROVAL_REQUIRED)
                    val certificate = certificateDer()
                    if (certificate.isEmpty() || certificate.size > MAXIMUM_CERTIFICATE_BYTES) {
                        return exchange.respond(503, "text/plain; charset=utf-8", "certificate_unavailable".encodeToByteArray())
                    }
                    exchange.respond(200, "application/x-x509-ca-cert", certificate)
                }
                PAC_RESOURCE -> {
                    if (approved == null) return exchange.respond(403, "text/plain; charset=utf-8", APPROVAL_REQUIRED)
                    exchange.respond(
                        200,
                        "application/x-ns-proxy-autoconfig",
                        generatePac().encodeToByteArray(),
                    )
                }
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

    private fun renderIndex(token: String, confirmationCode: String?, approved: Boolean): String {
        val host = bindHost.htmlEscape()
        val status = if (approved) {
            "<p>Approved. Configure this Wi-Fi network to use proxy <strong>$host:${proxyEndpoint.port}</strong>.</p>" +
                "<p><a href=\"/invite/$token/knet-ca.crt\">Install KNet CA</a></p>" +
                "<p><a href=\"/invite/$token/proxy.pac\">Download PAC</a></p>"
        } else {
            "<p>Return to KNet Desktop and approve this phone.</p>" +
                (confirmationCode?.let { "<p>Confirmation code: <strong>$it</strong></p>" } ?: "")
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" " +
            "content=\"width=device-width,initial-scale=1\"><title>KNet Wi-Fi Setup</title></head>" +
            "<body><h1>KNet Wi-Fi Setup</h1>$status" +
            "<p>Use only on a trusted local network.</p></body></html>"
    }

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

    private fun HttpExchange.respond(status: Int, mediaType: String, body: ByteArray) {
        responseHeaders.set("Content-Type", mediaType)
        responseHeaders.set("Cache-Control", "no-store")
        responseHeaders.set("Pragma", "no-cache")
        responseHeaders.set("X-Content-Type-Options", "nosniff")
        responseHeaders.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        responseHeaders.set("Referrer-Policy", "no-referrer")
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private data class Route(val token: String, val resource: String?)

    private fun parseRoute(rawPath: String): Route? {
        val parts = rawPath.split('/')
        if (parts.size !in 3..4 || parts[0].isNotEmpty() || parts[1] != "invite") return null
        val token = parts[2].takeIf { it.matches(SAFE_TOKEN) } ?: return null
        val resource = parts.getOrNull(3)?.takeIf(String::isNotEmpty)
        return Route(token, resource)
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

    private fun String.htmlEscape(): String = buildString(length) {
        this@htmlEscape.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                }
            )
        }
    }

    private companion object {
        val SAFE_TOKEN: Regex = Regex("[A-Za-z0-9_-]{43}")
        val APPROVAL_REQUIRED: ByteArray = "approval_required".encodeToByteArray()
        const val CERTIFICATE_RESOURCE: String = "knet-ca.crt"
        const val PAC_RESOURCE: String = "proxy.pac"
        const val WORKER_COUNT: Int = 2
        const val ACCEPT_BACKLOG: Int = 32
        const val MAXIMUM_CERTIFICATE_BYTES: Int = 1024 * 1024
    }
}
