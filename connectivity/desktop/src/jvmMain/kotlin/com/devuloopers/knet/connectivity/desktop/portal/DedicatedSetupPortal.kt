package com.devuloopers.knet.connectivity.desktop.portal

import com.devuloopers.knet.connectivity.desktop.artifact.SetupArtifactStore
import com.devuloopers.knet.connectivity.model.SetupArtifactId
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Content callbacks used by the delivery adapter without exposing certificate engines or UI. */
public data class SetupPortalContent(
    public val renderIndex: (String, Int) -> String,
    public val certificateDer: () -> ByteArray,
)

/**
 * Dedicated strict-authority setup listener. It is not installed in the proxy pipeline, so an
 * arbitrary upstream `/setup` or certificate-like path is always ordinary proxied traffic.
 */
public class DedicatedSetupPortal(
    private val bindHost: String,
    private val port: Int,
    allowedAuthorities: Set<String>,
    private val artifacts: SetupArtifactStore,
    private val content: SetupPortalContent,
) : AutoCloseable {
    private val allowedAuthorities: Set<String> = (allowedAuthorities + bindHost + "localhost" + "127.0.0.1" + "::1")
        .map(String::lowercase)
        .toSet()
    private val running = AtomicBoolean(false)
    private var server: HttpServer? = null
    private var executor: java.util.concurrent.ExecutorService? = null

    init {
        require(bindHost.isNotBlank())
        require(port in 1..65_535)
        require(this.allowedAuthorities.none(String::isBlank))
    }

    @Synchronized
    public fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            val worker = Executors.newFixedThreadPool(2) { task ->
                Thread(task, "knet-setup-portal").apply { isDaemon = true }
            }
            val created = HttpServer.create(InetSocketAddress(bindHost, port), 32)
            created.executor = worker
            created.createContext("/", ::handle)
            created.start()
            executor = worker
            server = created
        } catch (failure: Throwable) {
            running.set(false)
            executor?.shutdownNow()
            executor = null
            throw failure
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") return exchange.respond(405, "text/plain", "method_not_allowed".encodeToByteArray())
            val host = parseHost(exchange.requestHeaders.getFirst("Host"))
            if (host == null || host.lowercase() !in allowedAuthorities) {
                return exchange.respond(421, "text/plain", "unknown_setup_authority".encodeToByteArray())
            }
            when (val path = exchange.requestURI.rawPath) {
                "/", "/setup" -> exchange.respond(
                    200,
                    "text/html; charset=utf-8",
                    content.renderIndex(bindHost, port).encodeToByteArray(),
                )
                "/knet-ca.crt", "/ca" -> exchange.respond(
                    200,
                    "application/x-x509-ca-cert",
                    content.certificateDer(),
                )
                else -> {
                    val id = path.removePrefix("/artifacts/").takeIf { path.startsWith("/artifacts/") && it.isNotBlank() }
                    val artifact = id?.let { runCatching { artifacts.get(SetupArtifactId(it)) }.getOrNull() }
                    if (artifact == null) exchange.respond(404, "text/plain", "not_found".encodeToByteArray())
                    else exchange.respond(200, artifact.artifact.mediaType, artifact.copyBytes())
                }
            }
        } catch (_: Exception) {
            runCatching { exchange.respond(500, "text/plain", "setup_delivery_failed".encodeToByteArray()) }
        } finally {
            exchange.close()
        }
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
        responseHeaders.set("X-Content-Type-Options", "nosniff")
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }
}

internal fun parseHost(authority: String?): String? {
    val value = authority?.trim()?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it } ?: return null
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
