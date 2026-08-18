package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiSharingMetrics
import com.devuloopers.knet.traffic.model.ClientIdentity
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Exact-interface standard HTTP proxy gateway for approved stock phones.
 *
 * It performs admission and stream bridging only. HTTP parsing, TLS interception, capture, breakpoints,
 * persistence, and protocol inspection remain owned by the loopback proxy and its existing boundaries.
 */
internal class WifiLanProxyGateway(
    private val bindHost: String,
    private val bindPort: Int,
    private val targetProxy: () -> InetSocketAddress?,
    private val approvals: WifiClientApprovalRegistry,
    private val attributions: IngressAttributionRegistration,
    private val maximumConnections: Int = DEFAULT_MAXIMUM_CONNECTIONS,
    private val maximumConnectionsPerClient: Int = DEFAULT_MAXIMUM_CONNECTIONS_PER_CLIENT,
    private val nowMillis: () -> Long,
    private val onMetricsChanged: (WifiSharingMetrics) -> Unit = {},
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val globalAdmission = Semaphore(maximumConnections)
    private val clientAdmission = ConcurrentHashMap<WifiClientId, Semaphore>()
    private val activeByClient = ConcurrentHashMap<WifiClientId, MutableSet<Socket>>()
    private val activeSockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val activeConnections = AtomicLong(0L)
    private val acceptedConnections = AtomicLong(0L)
    private val rejectedConnections = AtomicLong(0L)
    private val dispatcher = Dispatchers.IO.limitedParallelism(maximumConnections * 2 + 2)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var listener: ServerSocket? = null

    init {
        val address = InetAddress.getByName(bindHost)
        require(!address.isAnyLocalAddress) { "Wi-Fi gateway cannot bind a wildcard address." }
        require(!address.isLoopbackAddress) { "Wi-Fi gateway requires a non-loopback address." }
        require(bindPort in 1..65_535)
        require(maximumConnections in 1..MAXIMUM_CONFIGURABLE_CONNECTIONS)
        require(maximumConnectionsPerClient in 1..maximumConnections)
    }

    fun start() {
        check(!closed.get()) { "Wi-Fi gateway is already closed." }
        if (!running.compareAndSet(false, true)) return
        try {
            val socket = ServerSocket()
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(bindHost, bindPort), ACCEPT_BACKLOG)
            listener = socket
            scope.launch { acceptLoop(socket) }
        } catch (failure: Throwable) {
            running.set(false)
            close()
            throw failure
        }
    }

    fun metrics(): WifiSharingMetrics = WifiSharingMetrics(
        activeConnections = activeConnections.get().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        acceptedConnections = acceptedConnections.get(),
        rejectedConnections = rejectedConnections.get(),
    )

    fun revoke(clientId: WifiClientId) {
        activeByClient.remove(clientId)?.toList()?.forEach { socket -> runCatching(socket::close) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val downstream = try {
                server.accept()
            } catch (_: SocketException) {
                break
            }
            if (!globalAdmission.tryAcquire()) {
                scope.launch { reject(downstream, SERVICE_UNAVAILABLE) }
                continue
            }
            scope.launch { admitAndBridge(downstream) }
        }
    }

    private suspend fun admitAndBridge(downstream: Socket) {
        val sourceAddress = downstream.inetAddress.hostAddress
        val approved = approvals.approvedFor(sourceAddress)
        if (approved == null) {
            globalAdmission.release()
            reject(downstream, APPROVAL_REQUIRED)
            return
        }
        val perClient = clientAdmission.computeIfAbsent(approved.id) {
            Semaphore(maximumConnectionsPerClient)
        }
        if (!perClient.tryAcquire()) {
            globalAdmission.release()
            reject(downstream, TOO_MANY_CONNECTIONS)
            return
        }

        activeConnections.incrementAndGet()
        acceptedConnections.incrementAndGet()
        track(approved.id, downstream)
        publishMetrics()
        try {
            bridge(downstream, approved.id)
        } finally {
            untrack(approved.id, downstream)
            runCatching(downstream::close)
            activeConnections.updateAndGet { current -> (current - 1L).coerceAtLeast(0L) }
            perClient.release()
            if (perClient.availablePermits() == maximumConnectionsPerClient) {
                clientAdmission.remove(approved.id, perClient)
            }
            globalAdmission.release()
            publishMetrics()
        }
    }

    private suspend fun bridge(downstream: Socket, clientId: WifiClientId) {
        val target = targetProxy()?.takeIf { it.address?.isLoopbackAddress == true }
            ?: return reject(downstream, INTERNAL_PROXY_UNAVAILABLE)
        Socket().use { upstream ->
            activeSockets.add(upstream)
            track(clientId, upstream)
            try {
                upstream.reuseAddress = false
                upstream.bind(InetSocketAddress(LOOPBACK_HOST, 0))
                val local = upstream.localSocketAddress as InetSocketAddress
                val registered = attributions.register(
                    downstream = TrafficEndpoint(local.address.hostAddress, local.port),
                    context = IngressContext(
                        kind = IngressKind.WifiApprovedDevice,
                        clientIdentity = ClientIdentity(clientId.value),
                    ),
                    expiresAtEpochMillis = nowMillis() + ATTRIBUTION_LIFETIME_MILLIS,
                )
                if (!registered) return reject(downstream, INTERNAL_PROXY_UNAVAILABLE)
                upstream.connect(target, CONNECT_TIMEOUT_MILLIS)
                val stillApproved = approvals.approvedFor(downstream.inetAddress.hostAddress)
                if (stillApproved?.id != clientId) return reject(downstream, APPROVAL_REQUIRED)
                coroutineScope {
                    launch { copy(downstream, upstream) }
                    launch { copy(upstream, downstream) }
                }
            } finally {
                untrack(clientId, upstream)
                activeSockets.remove(upstream)
            }
        }
    }

    private fun copy(source: Socket, destination: Socket) {
        try {
            source.getInputStream().copyTo(destination.getOutputStream(), COPY_BUFFER_BYTES)
            runCatching(destination::shutdownOutput)
        } catch (_: Exception) {
            runCatching(destination::close)
        }
    }

    private fun track(clientId: WifiClientId, socket: Socket) {
        activeSockets.add(socket)
        activeByClient.compute(clientId) { _, current ->
            (current ?: ConcurrentHashMap.newKeySet()).also { it.add(socket) }
        }
    }

    private fun untrack(clientId: WifiClientId, socket: Socket) {
        activeSockets.remove(socket)
        activeByClient.computeIfPresent(clientId) { _, current ->
            current.remove(socket)
            current.takeIf { it.isNotEmpty() }
        }
    }

    private fun reject(socket: Socket, response: String) {
        rejectedConnections.incrementAndGet()
        publishMetrics()
        drainInitialHeader(socket)
        runCatching {
            socket.getOutputStream().write(response.encodeToByteArray())
            socket.getOutputStream().flush()
            socket.shutdownOutput()
        }
        runCatching(socket::close)
    }

    /** Drains only the bounded initial proxy header so a normal denial is not converted into TCP reset. */
    private fun drainInitialHeader(socket: Socket) {
        runCatching {
            socket.soTimeout = REJECTION_DRAIN_TIMEOUT_MILLIS
            var matched = 0
            repeat(MAXIMUM_REJECTION_DRAIN_BYTES) {
                val next = socket.getInputStream().read()
                if (next < 0) return@runCatching
                matched = when {
                    HEADER_END[matched].toInt() == next -> matched + 1
                    HEADER_END[0].toInt() == next -> 1
                    else -> 0
                }
                if (matched == HEADER_END.size) return@runCatching
            }
        }
    }

    private fun publishMetrics() {
        onMetricsChanged(metrics())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        runCatching { listener?.close() }
        listener = null
        activeSockets.toList().forEach { socket -> runCatching(socket::close) }
        activeSockets.clear()
        activeByClient.clear()
        clientAdmission.clear()
        scope.cancel()
    }

    private companion object {
        const val LOOPBACK_HOST: String = "127.0.0.1"
        val HEADER_END: ByteArray = "\r\n\r\n".encodeToByteArray()
        const val ACCEPT_BACKLOG: Int = 64
        const val COPY_BUFFER_BYTES: Int = 64 * 1_024
        const val MAXIMUM_REJECTION_DRAIN_BYTES: Int = 64 * 1_024
        const val REJECTION_DRAIN_TIMEOUT_MILLIS: Int = 250
        const val CONNECT_TIMEOUT_MILLIS: Int = 5_000
        const val ATTRIBUTION_LIFETIME_MILLIS: Long = 10_000L
        const val DEFAULT_MAXIMUM_CONNECTIONS: Int = 256
        const val DEFAULT_MAXIMUM_CONNECTIONS_PER_CLIENT: Int = 64
        const val MAXIMUM_CONFIGURABLE_CONNECTIONS: Int = 1_024
        const val APPROVAL_REQUIRED: String =
            "HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
        const val TOO_MANY_CONNECTIONS: String =
            "HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
        const val SERVICE_UNAVAILABLE: String =
            "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
        const val INTERNAL_PROXY_UNAVAILABLE: String = SERVICE_UNAVAILABLE
    }
}
