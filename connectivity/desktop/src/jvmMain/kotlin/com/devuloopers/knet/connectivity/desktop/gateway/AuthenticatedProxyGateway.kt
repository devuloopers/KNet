package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.ClientIdentity
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Loopback-only standard HTTP proxy gateway. It validates `Proxy-Authorization: Bearer
 * <device-id>:<credential>`, strips that local credential, attributes the bridge socket, and then
 * copies bytes bidirectionally to the unchanged internal proxy under blocking-stream backpressure.
 */
public class AuthenticatedProxyGateway(
    private val bindPort: Int,
    private val targetProxy: () -> InetSocketAddress?,
    private val pairing: PairingCoordinator,
    private val attributions: IngressAttributionRegistration,
    private val maximumConnections: Int = 128,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val admission = Semaphore(maximumConnections)
    // One accept task plus two blocking copy tasks per admitted socket must remain schedulable.
    private val gatewayDispatcher = Dispatchers.IO.limitedParallelism(maximumConnections * 2 + 2)
    private val gatewayScope = CoroutineScope(SupervisorJob() + gatewayDispatcher)
    private val activeByDevice = ConcurrentHashMap<String, MutableSet<Socket>>()
    private val activeSockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private var listener: ServerSocket? = null

    init {
        require(bindPort in 1..65_535)
        require(maximumConnections in 1..MAXIMUM_CONFIGURABLE_CONNECTIONS)
        gatewayScope.launch {
            pairing.observeDevices().collect { devices ->
                devices.filter { it.isRevoked }.forEach { device ->
                    activeByDevice.remove(device.id.value)?.toList()?.forEach { runCatching(it::close) }
                }
            }
        }
    }

    public fun start() {
        check(!closed.get()) { "Authenticated gateway is already closed." }
        if (!running.compareAndSet(false, true)) return
        try {
            val socket = ServerSocket()
            socket.reuseAddress = false
            socket.bind(InetSocketAddress("127.0.0.1", bindPort), 64)
            listener = socket
            gatewayScope.launch { acceptLoop(socket) }
        } catch (failure: Throwable) {
            running.set(false)
            close()
            throw failure
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val downstream = try { server.accept() } catch (_: SocketException) { break }
            if (!admission.tryAcquire()) {
                downstream.respondAndClose("HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n")
                continue
            }
            activeSockets.add(downstream)
            gatewayScope.launch {
                try {
                    bridge(downstream)
                } finally {
                    runCatching(downstream::close)
                    activeSockets.remove(downstream)
                    admission.release()
                }
            }
        }
    }

    private suspend fun bridge(downstream: Socket) {
        downstream.soTimeout = HEADER_TIMEOUT_MILLIS
        val header = readHeader(downstream) ?: return downstream.respondAndClose(PROXY_AUTH_REQUIRED)
        val parsed = parseAuthorization(header) ?: return downstream.respondAndClose(PROXY_AUTH_REQUIRED)
        val authentication = pairing.authenticate(parsed.deviceId, parsed.credential, DeviceScope.PROXY_STREAM)
        val principal = (authentication as? DeviceAuthenticationResult.Authenticated)?.principal
            ?: return downstream.respondAndClose(PROXY_AUTH_REQUIRED)
        val target = targetProxy() ?: return downstream.respondAndClose(
            "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n",
        )

        Socket().use { upstream ->
            activeSockets.add(upstream)
            try {
                upstream.reuseAddress = false
                upstream.bind(InetSocketAddress("127.0.0.1", 0))
                val local = upstream.localSocketAddress as InetSocketAddress
                val attribution = IngressContext(
                    kind = IngressKind.LanPairedDevice,
                    clientIdentity = ClientIdentity(principal.deviceId.value),
                )
                if (!attributions.register(
                        TrafficEndpoint(local.address.hostAddress, local.port),
                        attribution,
                        nowMillis() + ATTRIBUTION_LIFETIME_MILLIS,
                    )
                ) return downstream.respondAndClose("HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n")
                upstream.connect(target, CONNECT_TIMEOUT_MILLIS)
                track(principal.deviceId, downstream, upstream)
                val stillAuthorized = pairing.authenticate(
                    parsed.deviceId,
                    parsed.credential,
                    DeviceScope.PROXY_STREAM,
                )
                if (stillAuthorized !is DeviceAuthenticationResult.Authenticated) {
                    return downstream.respondAndClose(PROXY_AUTH_REQUIRED)
                }
                downstream.soTimeout = 0
                upstream.getOutputStream().write(parsed.sanitizedHeader)
                upstream.getOutputStream().flush()
                coroutineScope {
                    launch { copy(downstream, upstream) }
                    launch { copy(upstream, downstream) }
                }
            } finally {
                untrack(principal.deviceId, downstream, upstream)
                activeSockets.remove(upstream)
            }
        }
    }

    private fun copy(source: Socket, destination: Socket) {
        try {
            source.getInputStream().copyTo(destination.getOutputStream(), COPY_BUFFER_BYTES)
            runCatching { destination.shutdownOutput() }
        } catch (_: Exception) {
            runCatching(destination::close)
        }
    }

    private data class AuthorizedHeader(
        val deviceId: RegisteredDeviceId,
        val credential: String,
        val sanitizedHeader: ByteArray,
    )

    private fun parseAuthorization(header: ByteArray): AuthorizedHeader? {
        val text = header.decodeToString()
        val lines = text.removeSuffix("\r\n\r\n").split("\r\n")
        if (lines.isEmpty() || lines.first().isBlank() || lines.drop(1).any { it.startsWith(' ') || it.startsWith('\t') }) return null
        val authLines = lines.drop(1).filter { it.substringBefore(':').equals("Proxy-Authorization", true) }
        if (authLines.size != 1) return null
        val value = authLines.single().substringAfter(':').trim()
        if (!value.startsWith("Bearer ", true)) return null
        val token = value.substringAfter(' ').trim()
        val device = token.substringBefore(':').takeIf { it.matches(SAFE_CREDENTIAL_TOKEN) } ?: return null
        val credential = token.substringAfter(':', "").takeIf { it.matches(SAFE_CREDENTIAL_TOKEN) } ?: return null
        val sanitized = (listOf(lines.first()) + lines.drop(1).filterNot {
            it.substringBefore(':').equals("Proxy-Authorization", true)
        }).joinToString("\r\n", postfix = "\r\n\r\n").encodeToByteArray()
        return AuthorizedHeader(RegisteredDeviceId(device), credential, sanitized)
    }

    private fun readHeader(socket: Socket): ByteArray? {
        val output = ByteArrayOutputStream()
        var matched = 0
        while (output.size() < MAX_HEADER_BYTES) {
            val next = socket.getInputStream().read()
            if (next < 0) return null
            output.write(next)
            matched = when {
                HEADER_END[matched].toInt() == next -> matched + 1
                HEADER_END[0].toInt() == next -> 1
                else -> 0
            }
            if (matched == HEADER_END.size) return output.toByteArray()
        }
        return null
    }

    private fun track(id: RegisteredDeviceId, vararg sockets: Socket) {
        activeByDevice.compute(id.value) { _, current ->
            (current ?: ConcurrentHashMap.newKeySet()).also { it.addAll(sockets) }
        }
    }

    private fun untrack(id: RegisteredDeviceId, vararg sockets: Socket) {
        activeByDevice.computeIfPresent(id.value) { _, current ->
            current.removeAll(sockets.toSet()); current.takeIf { it.isNotEmpty() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        runCatching { listener?.close() }
        listener = null
        activeSockets.forEach { socket -> runCatching(socket::close) }
        activeSockets.clear()
        activeByDevice.values.forEach { sockets ->
            sockets.forEach { socket -> runCatching(socket::close) }
        }
        activeByDevice.clear()
        gatewayScope.cancel()
    }

    private fun Socket.respondAndClose(response: String) {
        runCatching { getOutputStream().write(response.encodeToByteArray()) }
        runCatching(::close)
    }

    private companion object {
        private val HEADER_END = "\r\n\r\n".encodeToByteArray()
        private val SAFE_CREDENTIAL_TOKEN = Regex("[A-Za-z0-9._~-]{1,512}")
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val HEADER_TIMEOUT_MILLIS = 10_000
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val ATTRIBUTION_LIFETIME_MILLIS = 10_000L
        private const val MAXIMUM_CONFIGURABLE_CONNECTIONS = 512
        private const val PROXY_AUTH_REQUIRED =
            "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Bearer realm=\"KNet\"\r\nConnection: close\r\n\r\n"
    }
}
