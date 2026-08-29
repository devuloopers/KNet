@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel.network

import com.devuloopers.knet.companion.packettunnel.options.TunnelFailure
import com.devuloopers.knet.companion.packettunnel.options.TunnelStartOptions
import platform.Network.NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT
import platform.Network.NW_PARAMETERS_DEFAULT_CONFIGURATION
import platform.Network.NW_PARAMETERS_DISABLE_PROTOCOL
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_t
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_listener_cancel
import platform.Network.nw_listener_create
import platform.Network.nw_listener_get_port
import platform.Network.nw_listener_set_new_connection_handler
import platform.Network.nw_listener_set_queue
import platform.Network.nw_listener_set_state_changed_handler
import platform.Network.nw_listener_start
import platform.Network.nw_listener_state_cancelled
import platform.Network.nw_listener_state_failed
import platform.Network.nw_listener_state_ready
import platform.Network.nw_listener_t
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_parameters_set_local_endpoint
import platform.Network.nw_parameters_set_reuse_local_address
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

internal class LocalSocks5Gateway(options: TunnelStartOptions) {
    private val queue: dispatch_queue_t = dispatch_queue_create("com.devuloopers.knet.companion.socks", null)
    private val desktopProxy = PinnedDesktopProxy(options, queue)
    private val clients: MutableSet<SocksClient> = mutableSetOf()
    private var listener: nw_listener_t = null

    fun start(completion: (Result<UShort>) -> Unit) {
        dispatch_async(queue) {
            val parameters = nw_parameters_create_secure_tcp(
                configure_tls = NW_PARAMETERS_DISABLE_PROTOCOL,
                configure_tcp = NW_PARAMETERS_DEFAULT_CONFIGURATION,
            )
            nw_parameters_set_reuse_local_address(parameters, true)
            nw_parameters_set_local_endpoint(parameters, nw_endpoint_create_host(LOOPBACK_HOST, ANY_PORT))
            val newListener = nw_listener_create(parameters)
            if (newListener == null) {
                completion(Result.failure(TunnelFailure.UNABLE_TO_START_GATEWAY.exception()))
                return@dispatch_async
            }

            var completed = false
            nw_listener_set_state_changed_handler(newListener) { state, _ ->
                when (state) {
                    nw_listener_state_ready -> if (!completed) {
                        completed = true
                        val port = nw_listener_get_port(newListener)
                        if (port == 0.toUShort()) {
                            completion(Result.failure(TunnelFailure.UNABLE_TO_START_GATEWAY.exception()))
                            stopOnQueue()
                        } else {
                            completion(Result.success(port))
                        }
                    }

                    nw_listener_state_failed -> {
                        if (!completed) {
                            completed = true
                            completion(Result.failure(TunnelFailure.UNABLE_TO_START_GATEWAY.exception()))
                        }
                        stopOnQueue()
                    }

                    nw_listener_state_cancelled -> Unit
                }
            }
            nw_listener_set_new_connection_handler(newListener) { connection ->
                if (connection != null) accept(connection)
            }
            listener = newListener
            nw_listener_set_queue(newListener, queue)
            nw_listener_start(newListener)
        }
    }

    fun stop() {
        dispatch_async(queue) { stopOnQueue() }
    }

    private fun stopOnQueue() {
        listener?.let(::nw_listener_cancel)
        listener = null
        clients.toList().forEach(SocksClient::stop)
        clients.clear()
        desktopProxy.close()
    }

    private fun accept(connection: nw_connection_t) {
        lateinit var client: SocksClient
        client = SocksClient(connection, desktopProxy, queue) { clients.remove(client) }
        clients += client
        client.start()
    }

    private companion object {
        const val LOOPBACK_HOST: String = "127.0.0.1"
        const val ANY_PORT: String = "0"
    }
}

private class SocksClient(
    private val connection: nw_connection_t,
    private val desktopProxy: PinnedDesktopProxy,
    private val queue: dispatch_queue_t,
    private val onClose: () -> Unit,
) {
    private var buffer: ByteArray = ByteArray(0)
    private var upstream: nw_connection_t = null
    private var stopped: Boolean = false

    fun start() {
        nw_connection_set_state_changed_handler(connection) { state, _ ->
            when (state) {
                nw_connection_state_ready -> {
                    nw_connection_set_state_changed_handler(connection, null)
                    readGreeting()
                }

                nw_connection_state_failed, nw_connection_state_cancelled -> stop()
            }
        }
        nw_connection_set_queue(connection, queue)
        nw_connection_start(connection)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        nw_connection_cancel(connection)
        upstream?.let(::nw_connection_cancel)
        upstream = null
        onClose()
    }

    private fun readGreeting() {
        readExactly(2) { prefixResult ->
            val prefix = prefixResult.getOrNull()
            if (prefix == null || prefix.unsigned(0) != SOCKS_VERSION) {
                stop()
                return@readExactly
            }
            val methodCount = prefix.unsigned(1)
            if (methodCount !in 1..MAXIMUM_METHOD_COUNT) {
                stop()
                return@readExactly
            }
            readExactly(methodCount) { methodsResult ->
                val methods = methodsResult.getOrNull()
                if (methods == null) {
                    stop()
                } else if (methods.none { it.toUByte().toInt() == NO_AUTHENTICATION }) {
                    send(byteArrayOf(SOCKS_VERSION.byte(), NO_ACCEPTABLE_METHOD.byte())) { stop() }
                } else {
                    send(byteArrayOf(SOCKS_VERSION.byte(), NO_AUTHENTICATION.byte())) { readRequest() }
                }
            }
        }
    }

    private fun readRequest() {
        readExactly(4) { headerResult ->
            val header = headerResult.getOrNull()
            if (header == null || header.unsigned(0) != SOCKS_VERSION || header.unsigned(2) != RESERVED) {
                stop()
                return@readExactly
            }
            if (header.unsigned(1) != CONNECT_COMMAND) {
                reply(COMMAND_NOT_SUPPORTED) { stop() }
                return@readExactly
            }
            readAddress(header.unsigned(3)) { address ->
                if (address == null) {
                    stop()
                    return@readAddress
                }
                readExactly(2) { portResult ->
                    val portBytes = portResult.getOrNull()
                    if (portBytes == null) {
                        stop()
                        return@readExactly
                    }
                    val rawPort = (portBytes.unsigned(0) shl 8) or portBytes.unsigned(1)
                    if (rawPort == 0) {
                        reply(GENERAL_FAILURE) { stop() }
                        return@readExactly
                    }
                    desktopProxy.openTunnel(address, rawPort.toUShort()) { tunnelResult ->
                        val tunnel = tunnelResult.getOrNull()
                        if (tunnel == null) {
                            reply(CONNECTION_REFUSED) { stop() }
                        } else {
                            upstream = tunnel.connection
                            reply(SUCCEEDED) { beginPiping(tunnel) }
                        }
                    }
                }
            }
        }
    }

    private fun readAddress(type: Int, completion: (String?) -> Unit) {
        when (type) {
            IPV4_ADDRESS -> readExactly(IPV4_BYTE_COUNT) { result ->
                completion(result.getOrNull()?.joinToString(".") { it.toUByte().toString() })
            }

            DOMAIN_ADDRESS -> readExactly(1) { lengthResult ->
                val length = lengthResult.getOrNull()?.unsigned(0) ?: 0
                if (length == 0) {
                    completion(null)
                    return@readExactly
                }
                readExactly(length) { domainResult ->
                    val domain = runCatching { domainResult.getOrThrow().decodeToString(throwOnInvalidSequence = true) }
                        .getOrNull()
                        ?.takeIf { it.isSafeDomain() }
                    completion(domain)
                }
            }

            IPV6_ADDRESS -> readExactly(IPV6_BYTE_COUNT) { result ->
                completion(result.getOrNull()?.toIpv6Address())
            }

            else -> reply(ADDRESS_TYPE_NOT_SUPPORTED) {
                completion(null)
                stop()
            }
        }
    }

    private fun beginPiping(tunnel: PinnedProxyTunnel) {
        pipe(connection, tunnel.connection)
        if (tunnel.initialTargetBytes.isEmpty()) {
            pipe(tunnel.connection, connection)
        } else {
            sendTo(connection, tunnel.initialTargetBytes) { sent ->
                if (sent) pipe(tunnel.connection, connection) else stop()
            }
        }
    }

    private fun pipe(source: nw_connection_t, destination: nw_connection_t) {
        nw_connection_receive(source, 1u, PIPE_BUFFER_BYTES.toUInt()) { data, _, complete, error ->
            if (stopped) return@nw_connection_receive
            val received = data?.copyBytes().orEmpty()
            when {
                received.isNotEmpty() -> sendTo(destination, received) { sent ->
                    when {
                        !sent -> stop()
                        complete -> finish(destination)
                        else -> pipe(source, destination)
                    }
                }

                complete -> finish(destination)
                error != null -> stop()
                else -> pipe(source, destination)
            }
        }
    }

    private fun finish(destination: nw_connection_t) {
        nw_connection_send(
            destination,
            null,
            NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT,
            true,
        ) { error -> if (error != null) stop() }
    }

    private fun reply(code: Int, completion: () -> Unit) {
        send(byteArrayOf(SOCKS_VERSION.byte(), code.byte(), 0, IPV4_ADDRESS.byte(), 0, 0, 0, 0, 0, 0), completion)
    }

    private fun send(data: ByteArray, completion: () -> Unit) {
        sendTo(connection, data) { sent -> if (sent) completion() else stop() }
    }

    private fun sendTo(destination: nw_connection_t, data: ByteArray, completion: (Boolean) -> Unit) {
        nw_connection_send(
            destination,
            data.toDispatchData(),
            NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT,
            true,
        ) { error -> completion(error == null) }
    }

    private fun readExactly(count: Int, completion: (Result<ByteArray>) -> Unit) {
        if (count <= 0) {
            completion(Result.success(ByteArray(0)))
            return
        }
        if (buffer.size >= count) {
            val value = buffer.copyOfRange(0, count)
            buffer = buffer.copyOfRange(count, buffer.size)
            completion(Result.success(value))
            return
        }
        val maximumLength = maxOf(MINIMUM_READ_BYTES, count - buffer.size)
        nw_connection_receive(connection, 1u, maximumLength.toUInt()) { data, _, complete, error ->
            data?.copyBytes()?.let { buffer += it }
            when {
                buffer.size >= count -> readExactly(count, completion)
                error != null -> completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
                complete -> completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
                else -> readExactly(count, completion)
            }
        }
    }

    private fun ByteArray.unsigned(index: Int): Int = this[index].toUByte().toInt()

    private fun String.isSafeDomain(): Boolean =
        length in 1..MAXIMUM_DOMAIN_BYTES && all { it.code > 0x20 && it.code != 0x7f }

    private fun ByteArray.toIpv6Address(): String {
        if (size != IPV6_BYTE_COUNT) return ""
        return (0 until IPV6_GROUP_COUNT).joinToString(":") { group ->
            val offset = group * 2
            ((unsigned(offset) shl 8) or unsigned(offset + 1)).toString(16)
        }
    }

    private fun Int.byte(): Byte = toByte()

    private fun ByteArray?.orEmpty(): ByteArray = this ?: ByteArray(0)

    private companion object {
        const val SOCKS_VERSION: Int = 5
        const val NO_AUTHENTICATION: Int = 0
        const val NO_ACCEPTABLE_METHOD: Int = 0xff
        const val CONNECT_COMMAND: Int = 1
        const val RESERVED: Int = 0
        const val SUCCEEDED: Int = 0
        const val GENERAL_FAILURE: Int = 1
        const val CONNECTION_REFUSED: Int = 5
        const val COMMAND_NOT_SUPPORTED: Int = 7
        const val ADDRESS_TYPE_NOT_SUPPORTED: Int = 8
        const val IPV4_ADDRESS: Int = 1
        const val DOMAIN_ADDRESS: Int = 3
        const val IPV6_ADDRESS: Int = 4
        const val IPV4_BYTE_COUNT: Int = 4
        const val IPV6_BYTE_COUNT: Int = 16
        const val IPV6_GROUP_COUNT: Int = 8
        const val MAXIMUM_METHOD_COUNT: Int = 255
        const val MAXIMUM_DOMAIN_BYTES: Int = 255
        const val MINIMUM_READ_BYTES: Int = 4_096
        const val PIPE_BUFFER_BYTES: Int = 64 * 1_024
    }
}
