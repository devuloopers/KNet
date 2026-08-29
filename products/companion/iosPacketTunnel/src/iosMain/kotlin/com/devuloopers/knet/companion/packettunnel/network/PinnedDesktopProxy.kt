@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel.network

import com.devuloopers.knet.companion.packettunnel.options.TunnelFailure
import com.devuloopers.knet.companion.packettunnel.options.TunnelStartOptions
import com.devuloopers.knet.companion.packettunnel.options.sha256Hex
import kotlinx.cinterop.*
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.*
import platform.Network.NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT
import platform.Network.NW_PARAMETERS_DEFAULT_CONFIGURATION
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
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
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_parameters_set_reuse_local_address
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.SecTrustSetPolicies
import platform.Security.errSecSuccess
import platform.Security.sec_protocol_options_set_min_tls_protocol_version
import platform.Security.sec_protocol_options_set_tls_server_name
import platform.Security.sec_protocol_options_set_verify_block
import platform.Security.sec_trust_copy_ref
import platform.Security.tls_protocol_version_TLSv12
import platform.darwin.dispatch_queue_t
import platform.posix.memcpy

internal class PinnedDesktopProxy(
    private val options: TunnelStartOptions,
    private val queue: dispatch_queue_t,
) {
    private val rootCertificate: SecCertificateRef = options.rootCertificate.toSecCertificate()
        ?: throw TunnelFailure.INVALID_ROOT_CERTIFICATE.exception()
    private var closed: Boolean = false

    fun close() {
        if (closed) return
        closed = true
        CFRelease(rootCertificate)
    }

    fun openTunnel(host: String, port: UShort, completion: (Result<PinnedProxyTunnel>) -> Unit) {
        if (!host.isSafeAuthorityHost() || port == 0.toUShort()) {
            completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
            return
        }
        val endpoint = nw_endpoint_create_host(options.proxyHost, options.proxyPort.toString())
        val parameters = tlsParameters()
        nw_parameters_set_reuse_local_address(parameters, true)
        val connection = nw_connection_create(endpoint, parameters)
        var completed = false
        nw_connection_set_state_changed_handler(connection) { state, error ->
            when (state) {
                nw_connection_state_ready -> if (!completed) {
                    completed = true
                    nw_connection_set_state_changed_handler(connection, null)
                    sendConnect(connection, host, port, completion)
                }
                nw_connection_state_failed, nw_connection_state_cancelled -> if (!completed) {
                    completed = true
                    nw_connection_set_state_changed_handler(connection, null)
                    nw_connection_cancel(connection)
                    completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
                }
            }
        }
        nw_connection_set_queue(connection, queue)
        nw_connection_start(connection)
    }

    private fun tlsParameters() = nw_parameters_create_secure_tcp(
        configure_tls = { tlsOptions ->
            val securityOptions = nw_tls_copy_sec_protocol_options(tlsOptions)
            sec_protocol_options_set_tls_server_name(securityOptions, TLS_SERVER_NAME)
            sec_protocol_options_set_min_tls_protocol_version(securityOptions, tls_protocol_version_TLSv12)
            sec_protocol_options_set_verify_block(
                securityOptions,
                { _, trustObject, complete ->
                    val trust = sec_trust_copy_ref(trustObject)
                    val accepted = trust != null && validateTrust(trust)
                    if (trust != null) CFRelease(trust)
                    complete?.invoke(accepted)
                },
                queue,
            )
        },
        configure_tcp = NW_PARAMETERS_DEFAULT_CONFIGURATION,
    )

    private fun validateTrust(trust: platform.Security.SecTrustRef): Boolean {
        val hostname = CFStringCreateWithCString(null, TLS_SERVER_NAME, kCFStringEncodingUTF8) ?: return false
        val policy = SecPolicyCreateSSL(true, hostname)
        CFRelease(hostname)
        if (policy == null) return false
        val configured = SecTrustSetPolicies(trust, policy) == errSecSuccess && setPinnedAnchor(trust)
        CFRelease(policy)
        if (!configured || !SecTrustEvaluateWithError(trust, null)) return false

        var rootMatches = false
        var transportMatches = false
        val count = SecTrustGetCertificateCount(trust)
        for (index in 0 until count) {
            val certificate = SecTrustGetCertificateAtIndex(trust, index) ?: continue
            val data = CFBridgingRelease(SecCertificateCopyData(certificate)) as NSData
            val bytes = data.copyBytes()
            rootMatches = rootMatches || bytes.contentEquals(options.rootCertificate)
            transportMatches = transportMatches || bytes.sha256Hex() == options.transportSha256
        }
        return rootMatches && transportMatches
    }

    private fun setPinnedAnchor(trust: platform.Security.SecTrustRef): Boolean = memScoped {
        val values = allocArray<COpaquePointerVar>(1)
        values[0] = rootCertificate
        val anchors = CFArrayCreate(null, values, 1, null) ?: return@memScoped false
        val configured = SecTrustSetAnchorCertificates(trust, anchors) == errSecSuccess &&
            SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess
        CFRelease(anchors)
        configured
    }

    private fun sendConnect(
        connection: nw_connection_t,
        host: String,
        port: UShort,
        completion: (Result<PinnedProxyTunnel>) -> Unit,
    ) {
        val authority = if (':' in host) "[$host]:$port" else "$host:$port"
        val header = buildString {
            append("CONNECT $authority HTTP/1.1\r\n")
            append("Host: $authority\r\n")
            append("Proxy-Authorization: ${options.authorization}\r\n")
            append("Proxy-Connection: keep-alive\r\n\r\n")
        }
        nw_connection_send(
            connection,
            header.encodeToByteArray().toDispatchData(),
            NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT,
            true,
        ) { error ->
            if (error != null) {
                nw_connection_cancel(connection)
                completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
            } else {
                readHeader(connection, ByteArray(0), completion)
            }
        }
    }

    private fun readHeader(
        connection: nw_connection_t,
        buffered: ByteArray,
        completion: (Result<PinnedProxyTunnel>) -> Unit,
    ) {
        nw_connection_receive(connection, 1u, HEADER_READ_BYTES.toUInt()) { data, _, complete, error ->
            val received = data?.copyBytes().orEmpty()
            val next = buffered + received
            if (next.size > MAXIMUM_HEADER_BYTES) {
                reject(connection, completion)
                return@nw_connection_receive
            }
            val end = next.indexOf(HEADER_END)
            if (end >= 0) {
                val bodyStart = end + HEADER_END.size
                val firstLine = next.copyOfRange(0, end).decodeToString().substringBefore("\r\n")
                val status = firstLine.split(' ').filter(String::isNotBlank)
                if (status.size < 2 || status[0] !in SUPPORTED_HTTP_VERSIONS || status[1] != "200") {
                    reject(connection, completion)
                    return@nw_connection_receive
                }
                completion(Result.success(PinnedProxyTunnel(connection, next.copyOfRange(bodyStart, next.size))))
            } else if (complete || error != null) {
                reject(connection, completion)
            } else {
                readHeader(connection, next, completion)
            }
        }
    }

    private fun reject(connection: nw_connection_t, completion: (Result<PinnedProxyTunnel>) -> Unit) {
        nw_connection_cancel(connection)
        completion(Result.failure(TunnelFailure.PROXY_CONNECTION_FAILED.exception()))
    }

    private fun String.isSafeAuthorityHost(): Boolean =
        length in 1..255 && trim() == this && none { it.code <= 0x20 || it.code == 0x7f || it in "/\\?#@" }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        for (start in 0..size - needle.size) {
            if (needle.indices.all { index -> this[start + index] == needle[index] }) return start
        }
        return -1
    }

    private companion object {
        const val TLS_SERVER_NAME: String = "knet.local"
        const val HEADER_READ_BYTES: Int = 4_096
        const val MAXIMUM_HEADER_BYTES: Int = 32 * 1_024
        val HEADER_END: ByteArray = "\r\n\r\n".encodeToByteArray()
        val SUPPORTED_HTTP_VERSIONS: Set<String> = setOf("HTTP/1.0", "HTTP/1.1")
    }
}

internal data class PinnedProxyTunnel(
    val connection: nw_connection_t,
    val initialTargetBytes: ByteArray,
)

private fun ByteArray.toSecCertificate(): SecCertificateRef? = usePinned { pinned ->
    val data = NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong())
    val retained = CFBridgingRetain(data) ?: return@usePinned null
    try {
        SecCertificateCreateWithData(null, retained.reinterpret())
    } finally {
        CFRelease(retained)
    }
}

private fun NSData.copyBytes(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
}

private fun ByteArray?.orEmpty(): ByteArray = this ?: ByteArray(0)
