package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.connectivity.certificate.AndroidPairedTlsTrustFactory
import com.devuloopers.knet.companion.connectivity.certificate.isServedByRoot
import com.devuloopers.knet.companion.connectivity.certificate.isValidPairingRoot
import com.devuloopers.knet.companion.connectivity.certificate.matchesPinnedTransportIdentity
import com.devuloopers.knet.companion.connectivity.certificate.parseX509Certificate
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionProxyProtocol
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionTransportKind
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android direct-LAN companion carrier backed by the paired KNet TLS identity.
 *
 * [connect] performs an authenticated readiness probe before retaining the in-memory credential. Per-flow sockets
 * are opened only by the Android VPN runtime and are protected before connecting, preventing a recursive VPN route.
 */
public class AndroidCompanionProxyTransport(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : CompanionTransport {
    private val lifecycleLock: Mutex = Mutex()
    private val activeSession: AtomicReference<Session?> = AtomicReference(null)
    private val mutableState: MutableStateFlow<CompanionConnectionState> =
        MutableStateFlow(CompanionConnectionState.Disconnected)

    override val state: StateFlow<CompanionConnectionState> = mutableState.asStateFlow()

    /** Authenticates the paired desktop and confirms that its inspected proxy is currently running. */
    override suspend fun connect(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionTransportResult = lifecycleLock.withLock {
        if (!credential.matches(SAFE_CREDENTIAL)) {
            return@withLock reject(credentialFailure())
        }
        val existing = activeSession.get()
        if (existing?.matches(registration, credential) == true &&
            mutableState.value is CompanionConnectionState.Connected
        ) {
            return@withLock CompanionTransportResult.Connected
        }
        activeSession.set(null)
        mutableState.value = CompanionConnectionState.Connecting(registration.desktopId, attempt = 1)
        val candidate = try {
            Session.create(registration, credential)
        } catch (_: Exception) {
            return@withLock reject(identityFailure())
        }
        val probe = try {
            withContext(Dispatchers.IO) { candidate.probeReadiness() }
        } catch (cancelled: CancellationException) {
            mutableState.value = CompanionConnectionState.Disconnected
            throw cancelled
        } catch (_: IdentityRejectedException) {
            return@withLock reject(identityFailure())
        } catch (_: Exception) {
            return@withLock reject(unavailableFailure())
        }
        when (probe) {
            Readiness.READY -> Unit
            Readiness.PROXY_STOPPED -> return@withLock reject(proxyStoppedFailure())
            Readiness.AUTHENTICATION_REJECTED -> return@withLock reject(authenticationFailure())
            Readiness.UNEXPECTED_RESPONSE -> return@withLock reject(unavailableFailure())
        }
        activeSession.set(candidate)
        mutableState.value = CompanionConnectionState.Connected(
            desktopId = registration.desktopId,
            transport = CompanionTransportKind.DIRECT_LAN,
            connectedAtEpochMillis = nowEpochMillis(),
        )
        CompanionTransportResult.Connected
    }

    /** Clears the in-memory carrier credential; existing VPN flows are closed by the inspection backend first. */
    override suspend fun disconnect(): Unit = lifecycleLock.withLock {
        activeSession.set(null)
        mutableState.value = CompanionConnectionState.Disconnected
    }

    internal fun openConnectTunnel(
        authorityHost: String,
        authorityPort: Int,
        protector: AndroidSocketProtector,
    ): AndroidProxyStream? {
        val session = activeSession.get() ?: return null
        if (!isSafeAuthorityHost(authorityHost) || authorityPort !in 1..65_535) return null
        val socket = session.openSocket(protector) ?: return null
        return try {
            val request = buildString {
                append("CONNECT ${formatAuthority(authorityHost, authorityPort)} HTTP/1.1\r\n")
                append("Host: ${formatAuthority(authorityHost, authorityPort)}\r\n")
                append(session.authorizationHeader())
                append("Proxy-Connection: keep-alive\r\n\r\n")
            }.encodeToByteArray()
            socket.outputStream.write(request)
            socket.outputStream.flush()
            val response = readHeader(socket, MAXIMUM_GATEWAY_HEADER_BYTES) ?: return socket.closeAndNull()
            if (!response.decodeToString().lineSequence().firstOrNull().orEmpty().contains(" 200 ")) {
                return socket.closeAndNull()
            }
            AndroidProxyStream(socket)
        } catch (_: Exception) {
            socket.closeAndNull()
        }
    }

    internal fun openHttpForward(
        requestHeader: ByteArray,
        protector: AndroidSocketProtector,
    ): AndroidProxyStream? {
        val session = activeSession.get() ?: return null
        val authorized = addAuthorization(requestHeader, session.authorizationHeader()) ?: return null
        val socket = session.openSocket(protector) ?: return null
        return try {
            socket.outputStream.write(authorized)
            socket.outputStream.flush()
            AndroidProxyStream(socket)
        } catch (_: Exception) {
            socket.closeAndNull()
        }
    }

    private fun reject(failure: CompanionFailure): CompanionTransportResult.Rejected {
        activeSession.set(null)
        mutableState.value = CompanionConnectionState.Failed(failure)
        return CompanionTransportResult.Rejected(failure)
    }

    private class Session private constructor(
        private val registration: CompanionRegistration,
        private val credential: String,
        private val rootCertificate: X509Certificate,
    ) {
        fun matches(other: CompanionRegistration, otherCredential: String): Boolean =
            registration == other && credential == otherCredential

        fun authorizationHeader(): String =
            "Proxy-Authorization: Bearer ${registration.deviceId.value}:$credential\r\n"

        fun probeReadiness(): Readiness {
            val socket = openSocket(protector = null) ?: throw TransportUnavailableException()
            return socket.use {
                val request = buildString {
                    append("GET ${CompanionProxyProtocol.READINESS_PATH} HTTP/1.1\r\n")
                    append("Host: ${CompanionCertificateProtocol.TLS_SERVER_NAME}\r\n")
                    append(authorizationHeader())
                    append("Connection: close\r\n\r\n")
                }.encodeToByteArray()
                socket.outputStream.write(request)
                socket.outputStream.flush()
                val response = readHeader(socket, MAXIMUM_GATEWAY_HEADER_BYTES)
                    ?: return@use Readiness.UNEXPECTED_RESPONSE
                when (response.decodeToString().lineSequence().firstOrNull().orEmpty().statusCode()) {
                    204 -> Readiness.READY
                    401, 403 -> Readiness.AUTHENTICATION_REJECTED
                    503 -> Readiness.PROXY_STOPPED
                    else -> Readiness.UNEXPECTED_RESPONSE
                }
            }
        }

        fun openSocket(protector: AndroidSocketProtector?): SSLSocket? {
            val transport = Socket()
            try {
                transport.tcpNoDelay = true
                transport.keepAlive = true
                if (protector != null && !protector.protect(transport)) return transport.closeAndNull()
                transport.connect(
                    InetSocketAddress(registration.proxyEndpoint.host, registration.proxyEndpoint.port),
                    CONNECT_TIMEOUT_MILLIS,
                )
                val sslSocket = AndroidPairedTlsTrustFactory.socketFactory(rootCertificate).createSocket(
                    transport,
                    CompanionCertificateProtocol.TLS_SERVER_NAME,
                    registration.proxyEndpoint.port,
                    true,
                ) as SSLSocket
                sslSocket.enabledProtocols = sslSocket.supportedProtocols
                    .filter { protocol -> protocol == "TLSv1.3" || protocol == "TLSv1.2" }
                    .toTypedArray()
                sslSocket.sslParameters = sslSocket.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(CompanionCertificateProtocol.TLS_SERVER_NAME))
                }
                sslSocket.soTimeout = HANDSHAKE_TIMEOUT_MILLIS
                sslSocket.startHandshake()
                validatePeer(sslSocket)
                sslSocket.soTimeout = 0
                return sslSocket
            } catch (rejected: IdentityRejectedException) {
                runCatching(transport::close)
                throw rejected
            } catch (_: Exception) {
                runCatching(transport::close)
                return null
            }
        }

        private fun validatePeer(socket: SSLSocket) {
            val session = socket.session
            val certificates = try {
                session.peerCertificates.filterIsInstance<X509Certificate>()
            } catch (_: SSLPeerUnverifiedException) {
                throw IdentityRejectedException()
            }
            if (!certificates.matchesPinnedTransportIdentity(registration.transportIdentitySha256.value) ||
                !certificates.isServedByRoot(rootCertificate)
            ) {
                throw IdentityRejectedException()
            }
        }

        companion object {
            fun create(registration: CompanionRegistration, credential: String): Session {
                require(registration.proxyEndpoint.secure)
                val root = registration.rootCertificate.copyBytes().parseX509Certificate()
                    ?.takeIf { certificate ->
                        certificate.isValidPairingRoot(registration.rootCertificateSha256.value)
                    }
                    ?: throw IdentityRejectedException()
                return Session(registration, credential, root)
            }
        }
    }

    private companion object {
        private val SAFE_CREDENTIAL: Regex = Regex("[A-Za-z0-9._~-]{1,512}")
        private const val CONNECT_TIMEOUT_MILLIS: Int = 5_000
        private const val HANDSHAKE_TIMEOUT_MILLIS: Int = 10_000
        private const val MAXIMUM_GATEWAY_HEADER_BYTES: Int = 32 * 1024
    }

    private enum class Readiness {
        READY,
        PROXY_STOPPED,
        AUTHENTICATION_REJECTED,
        UNEXPECTED_RESPONSE,
    }
}

internal class AndroidProxyStream(private val socket: SSLSocket) : AutoCloseable {
    val input = socket.inputStream
    val output = socket.outputStream

    fun shutdownOutput() {
        runCatching(socket::shutdownOutput)
    }

    override fun close() {
        runCatching(socket::close)
    }
}

private class IdentityRejectedException : Exception()
private class TransportUnavailableException : Exception()

private fun String.statusCode(): Int? = split(' ').getOrNull(1)?.toIntOrNull()

internal fun addAuthorization(header: ByteArray, authorizationHeader: String): ByteArray? {
    if (header.size > 64 * 1024) return null
    val text = runCatching { header.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
    if (!text.endsWith("\r\n\r\n")) return null
    val lines = text.removeSuffix("\r\n\r\n").split("\r\n")
    if (lines.isEmpty() || lines.first().isBlank()) return null
    if (lines.drop(1).any { line -> line.startsWith(' ') || line.startsWith('\t') }) return null
    val sanitized = lines.filterIndexed { index, line ->
        index == 0 || !line.substringBefore(':').equals("Proxy-Authorization", ignoreCase = true)
    }
    return buildString {
        sanitized.forEach { line -> append(line).append("\r\n") }
        append(authorizationHeader)
        append("\r\n")
    }.encodeToByteArray()
}

private fun readHeader(socket: Socket, maximumBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val ending = "\r\n\r\n".encodeToByteArray()
    var matched = 0
    while (output.size() < maximumBytes) {
        val next = socket.inputStream.read()
        if (next < 0) return null
        output.write(next)
        matched = when {
            ending[matched].toInt() == next -> matched + 1
            ending[0].toInt() == next -> 1
            else -> 0
        }
        if (matched == ending.size) return output.toByteArray()
    }
    return null
}

private fun isSafeAuthorityHost(host: String): Boolean =
    host.length in 1..255 && host == host.trim() && host.none { character ->
        character.code in 0..32 || character.code == 127 || character in "/\\?#@"
    }

private fun formatAuthority(host: String, port: Int): String =
    if (':' in host && !host.startsWith('[')) "[$host]:$port" else "$host:$port"

private fun <T> Socket.closeAndNull(): T? {
    runCatching(::close)
    return null
}

private fun credentialFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.CREDENTIAL_NOT_FOUND,
    "Paired credential is invalid.",
    recoverable = false,
)

private fun authenticationFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.CREDENTIAL_EXPIRED,
    "The paired desktop rejected this companion credential. Refresh or pair the device again.",
    recoverable = true,
)

private fun identityFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
    "The paired desktop TLS identity could not be verified.",
    recoverable = false,
)

private fun unavailableFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Unable to reach the paired desktop securely.",
    recoverable = true,
)

private fun proxyStoppedFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Start the KNet desktop proxy before starting inspection.",
    recoverable = true,
)
