package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Process-local SOCKS5 ingress used by the Android TUN translator.
 *
 * TCP is always carried through the authenticated desktop gateway. DNS is the sole UDP control dependency allowed
 * to leave directly, on a socket protected from the VPN. Other UDP is rejected unless explicit bypass is selected.
 */
internal class LocalSocks5Gateway(
    private val transport: AndroidCompanionProxyTransport,
    private val protector: AndroidSocketProtector,
    private val unsupportedTrafficPolicy: UnsupportedTrafficPolicy,
    private val directTcpPorts: Set<Int> = DIRECT_DNS_TCP_PORTS,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val activeFlows = Semaphore(MAXIMUM_ACTIVE_FLOWS)
    private val gatewayScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(MAXIMUM_ACTIVE_FLOWS * 2 + 2),
    )
    private val resourcesLock = Any()
    private val resources: MutableSet<AutoCloseable> = mutableSetOf()
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = checkNotNull(serverSocket).localPort

    fun start() {
        check(running.compareAndSet(false, true)) { "SOCKS5 gateway is already running." }
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(ipv4Loopback(), 0), ACCEPT_BACKLOG)
        }
        serverSocket = server
        gatewayScope.launch { acceptLoop(server) }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        val snapshot = synchronized(resourcesLock) {
            resources.toList().also { resources.clear() }
        }
        snapshot.forEach { resource -> runCatching(resource::close) }
        gatewayScope.cancel()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                continue
            }
            if (!activeFlows.tryAcquire()) {
                runCatching(client::close)
                continue
            }
            track(client)
            gatewayScope.launch {
                try {
                    handle(client)
                } finally {
                    untrack(client)
                    runCatching(client::close)
                    activeFlows.release()
                }
            }
        }
    }

    private fun handle(client: Socket) {
        client.tcpNoDelay = true
        client.soTimeout = NEGOTIATION_TIMEOUT_MILLIS
        val input = client.getInputStream()
        val output = client.getOutputStream()
        if (!negotiateAuthentication(input, output)) return
        val request = readRequest(input) ?: return
        when (request.command) {
            SOCKS_COMMAND_CONNECT -> handleConnect(client, input, output, request.destination)
            SOCKS_COMMAND_UDP_ASSOCIATE -> handleUdpAssociation(client, input, output)
            else -> writeReply(output, SOCKS_REPLY_COMMAND_NOT_SUPPORTED)
        }
    }

    private fun handleConnect(
        client: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        destination: SocksDestination,
    ) {
        if (destination.port in directTcpPorts) {
            handleProtectedDirectTcp(client, clientInput, clientOutput, destination)
            return
        }
        writeReply(clientOutput, SOCKS_REPLY_SUCCEEDED)
        client.soTimeout = INITIAL_PAYLOAD_TIMEOUT_MILLIS
        val initial = readInitialPayload(clientInput) ?: return
        client.soTimeout = 0
        val stream = if (initial.isHttpOneHeader) {
            val normalized = normalizeHttpProxyRequest(
                header = initial.bytes,
                destinationHost = destination.host,
                destinationPort = destination.port,
            ) ?: return
            transport.openHttpForward(normalized, protector)
        } else {
            transport.openConnectTunnel(destination.host, destination.port, protector)?.also { gateway ->
                gateway.output.write(initial.bytes)
                gateway.output.flush()
            }
        } ?: return
        track(stream)
        bridge(client, clientInput, clientOutput, stream)
    }

    /**
     * Carries DNS control connections outside the VPN and desktop HTTP proxy.
     *
     * Android may use both classic DNS-over-TCP (53) and Private DNS/DoT (853). These streams are not HTTP and
     * therefore cannot pass through KNet's inspecting HTTP proxy. Protecting the socket prevents VPN recursion while
     * keeping name resolution available for the application HTTP(S) flows that KNet does inspect.
     */
    private fun handleProtectedDirectTcp(
        client: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        destination: SocksDestination,
    ) {
        val upstream = Socket()
        track(upstream)
        var connected = false
        try {
            upstream.tcpNoDelay = true
            if (!protector.protect(upstream)) {
                writeReply(clientOutput, SOCKS_REPLY_GENERAL_FAILURE)
                return
            }
            upstream.connect(
                InetSocketAddress(destination.host, destination.port),
                DIRECT_CONNECT_TIMEOUT_MILLIS,
            )
            connected = true
            writeReply(clientOutput, SOCKS_REPLY_SUCCEEDED)
            client.soTimeout = 0
            bridgeDirect(client, clientInput, clientOutput, upstream)
        } catch (_: Exception) {
            if (!connected) runCatching { writeReply(clientOutput, SOCKS_REPLY_HOST_UNREACHABLE) }
        } finally {
            untrack(upstream)
            runCatching(upstream::close)
        }
    }

    private fun bridgeDirect(
        client: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        upstream: Socket,
    ) {
        gatewayScope.launch {
            try {
                upstream.getInputStream().copyTo(clientOutput, COPY_BUFFER_BYTES)
                clientOutput.flush()
            } catch (_: Exception) {
            } finally {
                runCatching(client::shutdownOutput)
            }
        }
        try {
            clientInput.copyTo(upstream.getOutputStream(), COPY_BUFFER_BYTES)
            upstream.getOutputStream().flush()
            runCatching(upstream::shutdownOutput)
        } catch (_: Exception) {
        }
    }

    private fun bridge(
        client: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        gateway: AndroidProxyStream,
    ) {
        gatewayScope.launch {
            try {
                gateway.input.copyTo(clientOutput, COPY_BUFFER_BYTES)
                clientOutput.flush()
            } catch (_: Exception) {
            } finally {
                runCatching(client::shutdownOutput)
                untrack(gateway)
                gateway.close()
            }
        }
        try {
            clientInput.copyTo(gateway.output, COPY_BUFFER_BYTES)
            gateway.output.flush()
            gateway.shutdownOutput()
        } catch (_: Exception) {
        }
    }

    private fun handleUdpAssociation(client: Socket, controlInput: InputStream, output: OutputStream) {
        val relay = DatagramSocket(InetSocketAddress(ipv4Loopback(), 0))
        track(relay)
        try {
            writeReply(output, SOCKS_REPLY_SUCCEEDED, relay.localPort)
            gatewayScope.launch { relayUdp(relay) }
            client.soTimeout = 0
            while (running.get() && controlInput.read() >= 0) {
                // SOCKS keeps the TCP control channel open for the lifetime of the UDP association.
            }
        } finally {
            untrack(relay)
            relay.close()
        }
    }

    private fun relayUdp(relay: DatagramSocket) {
        val packetBytes = ByteArray(MAXIMUM_UDP_PACKET_BYTES)
        var clientAddress: InetSocketAddress? = null
        while (running.get() && !relay.isClosed) {
            val inbound = DatagramPacket(packetBytes, packetBytes.size)
            try {
                relay.receive(inbound)
            } catch (_: Exception) {
                return
            }
            val source = InetSocketAddress(inbound.address, inbound.port)
            if (clientAddress == null) clientAddress = source
            if (source != clientAddress) continue
            val request = parseUdpRequest(inbound.data, inbound.offset, inbound.length) ?: continue
            if (request.destination.port != DNS_PORT && unsupportedTrafficPolicy == UnsupportedTrafficPolicy.REJECT) {
                continue
            }
            forwardUdp(request, relay, clientAddress)
        }
    }

    private fun track(resource: AutoCloseable) {
        synchronized(resourcesLock) { resources.add(resource) }
    }

    private fun untrack(resource: AutoCloseable) {
        synchronized(resourcesLock) { resources.remove(resource) }
    }

    private fun forwardUdp(request: SocksUdpRequest, relay: DatagramSocket, clientAddress: InetSocketAddress) {
        val upstream = DatagramSocket()
        if (!protector.protect(upstream)) {
            upstream.close()
            return
        }
        upstream.soTimeout = UDP_RESPONSE_TIMEOUT_MILLIS
        try {
            val destination = InetSocketAddress(request.destination.host, request.destination.port)
            upstream.send(DatagramPacket(request.payload, request.payload.size, destination))
            val responseBytes = ByteArray(MAXIMUM_UDP_PACKET_BYTES)
            val response = DatagramPacket(responseBytes, responseBytes.size)
            upstream.receive(response)
            val encoded = encodeUdpResponse(
                InetSocketAddress(response.address, response.port),
                response.data.copyOfRange(response.offset, response.offset + response.length),
            )
            relay.send(DatagramPacket(encoded, encoded.size, clientAddress))
        } catch (_: Exception) {
        } finally {
            upstream.close()
        }
    }

    private companion object {
        private const val ACCEPT_BACKLOG: Int = 64
        private const val MAXIMUM_ACTIVE_FLOWS: Int = 64
        private const val COPY_BUFFER_BYTES: Int = 16 * 1024
        private const val NEGOTIATION_TIMEOUT_MILLIS: Int = 10_000
        private const val INITIAL_PAYLOAD_TIMEOUT_MILLIS: Int = 15_000
        private const val UDP_RESPONSE_TIMEOUT_MILLIS: Int = 5_000
        private const val DIRECT_CONNECT_TIMEOUT_MILLIS: Int = 10_000
        private const val MAXIMUM_HTTP_HEADER_BYTES: Int = 64 * 1024
        private const val MAXIMUM_UDP_PACKET_BYTES: Int = 65_535
        private const val DNS_PORT: Int = 53
        private const val SOCKS_VERSION: Int = 5
        private const val SOCKS_AUTH_NONE: Int = 0
        private const val SOCKS_AUTH_UNACCEPTABLE: Int = 0xFF
        private const val SOCKS_COMMAND_CONNECT: Int = 1
        private const val SOCKS_COMMAND_UDP_ASSOCIATE: Int = 3
        private const val SOCKS_REPLY_SUCCEEDED: Int = 0
        private const val SOCKS_REPLY_GENERAL_FAILURE: Int = 1
        private const val SOCKS_REPLY_HOST_UNREACHABLE: Int = 4
        private const val SOCKS_REPLY_COMMAND_NOT_SUPPORTED: Int = 7
        private const val ADDRESS_IPV4: Int = 1
        private const val ADDRESS_DOMAIN: Int = 3
        private const val ADDRESS_IPV6: Int = 4
    }
}

private val DIRECT_DNS_TCP_PORTS: Set<Int> = setOf(53, 853)

private data class SocksRequest(val command: Int, val destination: SocksDestination)
private data class SocksDestination(val host: String, val port: Int)
private data class InitialPayload(val bytes: ByteArray, val isHttpOneHeader: Boolean)
private data class SocksUdpRequest(val destination: SocksDestination, val payload: ByteArray)

private fun negotiateAuthentication(input: InputStream, output: OutputStream): Boolean {
    if (input.read() != 5) return false
    val methodCount = input.read()
    if (methodCount !in 1..255) return false
    val methods = input.readExactly(methodCount) ?: return false
    val accepted = methods.any { byte -> byte.toInt() and 0xFF == 0 }
    output.write(byteArrayOf(5, if (accepted) 0 else 0xFF.toByte()))
    output.flush()
    return accepted
}

private fun readRequest(input: InputStream): SocksRequest? {
    val prefix = input.readExactly(4) ?: return null
    if ((prefix[0].toInt() and 0xFF) != 5 || prefix[2].toInt() != 0) return null
    val host = readAddress(input, prefix[3].toInt() and 0xFF) ?: return null
    val portBytes = input.readExactly(2) ?: return null
    val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
    if (port == 0) return null
    return SocksRequest(prefix[1].toInt() and 0xFF, SocksDestination(host, port))
}

private fun readAddress(input: InputStream, type: Int): String? = when (type) {
    1 -> input.readExactly(4)?.let { bytes -> InetAddress.getByAddress(bytes).hostAddress }
    3 -> {
        val length = input.read()
        if (length !in 1..255) null else input.readExactly(length)?.decodeToString()
    }
    4 -> input.readExactly(16)?.let { bytes -> InetAddress.getByAddress(bytes).hostAddress }
    else -> null
}

private fun writeReply(output: OutputStream, reply: Int, port: Int = 0) {
    output.write(
        byteArrayOf(
            5,
            reply.toByte(),
            0,
            1,
            127,
            0,
            0,
            1,
            (port ushr 8).toByte(),
            port.toByte(),
        ),
    )
    output.flush()
}

private fun readInitialPayload(input: InputStream): InitialPayload? {
    val first = input.read()
    if (first < 0) return null
    val initial = ByteArrayOutputStream().apply { write(first) }
    if (!first.toChar().isUpperCase()) return InitialPayload(initial.toByteArray(), false)
    var matched = 0
    val ending = byteArrayOf(13, 10, 13, 10)
    while (initial.size() < 64 * 1024) {
        val next = input.read()
        if (next < 0) return null
        initial.write(next)
        matched = when {
            ending[matched].toInt() == next -> matched + 1
            ending[0].toInt() == next -> 1
            else -> 0
        }
        if (matched == ending.size) break
    }
    val bytes = initial.toByteArray()
    val firstLine = bytes.toString(Charsets.ISO_8859_1).substringBefore("\r\n")
    val method = firstLine.substringBefore(' ')
    val isHttp = method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE")
    return InitialPayload(bytes, isHttp)
}

internal fun normalizeHttpProxyRequest(
    header: ByteArray,
    destinationHost: String,
    destinationPort: Int,
): ByteArray? {
    val text = header.toString(Charsets.ISO_8859_1)
    if (!text.endsWith("\r\n\r\n")) return null
    val firstEnd = text.indexOf("\r\n")
    if (firstEnd <= 0) return null
    val tokens = text.substring(0, firstEnd).split(' ')
    if (tokens.size != 3) return null
    val target = tokens[1]
    val absoluteTarget = when {
        target.startsWith("http://", ignoreCase = true) -> target
        target.startsWith('/') -> "http://${formatSocksAuthority(destinationHost, destinationPort)}$target"
        target == "*" -> target
        else -> return null
    }
    return buildString {
        append(tokens[0]).append(' ').append(absoluteTarget).append(' ').append(tokens[2])
        append(text.substring(firstEnd))
    }.toByteArray(Charsets.ISO_8859_1)
}

private fun formatSocksAuthority(hostValue: String, port: Int): String {
    val host = if (':' in hostValue && !hostValue.startsWith('[')) {
        "[$hostValue]"
    } else {
        hostValue
    }
    return if (port == 80) host else "$host:$port"
}

private fun parseUdpRequest(bytes: ByteArray, offset: Int, length: Int): SocksUdpRequest? {
    if (length < 7) return null
    var index = offset
    val end = offset + length
    if (bytes[index++].toInt() != 0 || bytes[index++].toInt() != 0 || bytes[index++].toInt() != 0) return null
    val type = bytes[index++].toInt() and 0xFF
    val addressLength = when (type) {
        1 -> 4
        4 -> 16
        3 -> if (index < end) bytes[index++].toInt() and 0xFF else return null
        else -> return null
    }
    if (addressLength == 0 || index + addressLength + 2 > end) return null
    val addressBytes = bytes.copyOfRange(index, index + addressLength)
    index += addressLength
    val host = if (type == 3) {
        addressBytes.decodeToString()
    } else {
        runCatching { InetAddress.getByAddress(addressBytes).hostAddress }.getOrNull() ?: return null
    }
    val port = ((bytes[index++].toInt() and 0xFF) shl 8) or (bytes[index++].toInt() and 0xFF)
    if (port == 0) return null
    return SocksUdpRequest(SocksDestination(host, port), bytes.copyOfRange(index, end))
}

private fun encodeUdpResponse(source: InetSocketAddress, payload: ByteArray): ByteArray {
    val address = source.address
    val type = when (address) {
        is Inet4Address -> 1
        is Inet6Address -> 4
        else -> error("Unsupported response address.")
    }
    return ByteArrayOutputStream(payload.size + 22).apply {
        write(byteArrayOf(0, 0, 0, type.toByte()))
        write(address.address)
        write(byteArrayOf((source.port ushr 8).toByte(), source.port.toByte()))
        write(payload)
    }.toByteArray()
}

private fun InputStream.readExactly(size: Int): ByteArray? {
    val bytes = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(bytes, offset, size - offset)
        if (read < 0) return null
        offset += read
    }
    return bytes
}

private fun ipv4Loopback(): InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
