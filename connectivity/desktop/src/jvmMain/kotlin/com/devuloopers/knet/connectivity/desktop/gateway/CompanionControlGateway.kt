package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.usecase.pairing.PairingOnboardingRedemptionResult
import com.devuloopers.knet.application.usecase.pairing.RedeemPairingOnboardingUseCase
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionCodec
import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionControlProtocol
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrant
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrantCodec
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequestCodec
import com.devuloopers.knet.companion.model.CompanionInvitationResponseCodec
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationCodec
import com.devuloopers.knet.companion.model.CompanionPairingCompletionCodec
import com.devuloopers.knet.companion.model.CompanionPairingGrant
import com.devuloopers.knet.companion.model.CompanionPairingGrantCodec
import com.devuloopers.knet.connectivity.desktop.certificate.AppleRootCertificateProfileRenderer
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingCredentialRefreshResult
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Authenticated TLS listener for bootstrap redemption, pairing, credential rotation, and certificate proofs.
 *
 * The listener is deliberately separate from the proxy data plane. Every request is scope checked, bounded, and
 * closed after one response. A nonce is accepted once during its replay window.
 *
 * @param bindHost exact local address on which the gateway listens.
 * @param bindPort configured port, or zero to request an operating-system-assigned test port.
 * @param serverSocketFactory server identity used for authenticated TLS.
 * @param rootCertificateDer defensive provider for the paired KNet root certificate.
 * @param pairing authority used to authenticate device credentials and scopes.
 * @param redeemOnboarding one-time bootstrap exchange used before a device has a durable credential.
 * @param nowEpochMillis monotonic-enough wall-clock source used to expire challenge replay entries.
 * @param maximumConnections upper bound for concurrent admitted TLS connections.
 * @param maximumTrackedChallenges upper bound for nonces retained during the replay window.
 */
public class CompanionControlGateway(
    private val bindHost: String,
    private val bindPort: Int,
    private val serverSocketFactory: SSLServerSocketFactory,
    private val rootCertificateDer: () -> ByteArray,
    private val pairing: PairingCoordinator,
    private val redeemOnboarding: RedeemPairingOnboardingUseCase,
    private val redemptionCodec: CompanionBootstrapRedemptionCodec = CompanionBootstrapRedemptionCodec(),
    private val invitationCodec: CompanionInvitationResponseCodec = CompanionInvitationResponseCodec(),
    private val endpointDescriptor: () -> CompanionEndpointDescriptor? = { null },
    private val endpointCodec: CompanionEndpointReconciliationCodec = CompanionEndpointReconciliationCodec(),
    private val pairingCompletionCodec: CompanionPairingCompletionCodec = CompanionPairingCompletionCodec(),
    private val pairingGrantCodec: CompanionPairingGrantCodec = CompanionPairingGrantCodec(),
    private val refreshRequestCodec: CompanionCredentialRefreshRequestCodec = CompanionCredentialRefreshRequestCodec(),
    private val refreshGrantCodec: CompanionCredentialRefreshGrantCodec = CompanionCredentialRefreshGrantCodec(),
    private val nowEpochMillis: () -> Long,
    private val maximumConnections: Int = DEFAULT_MAXIMUM_CONNECTIONS,
    private val maximumTrackedChallenges: Int = DEFAULT_MAXIMUM_TRACKED_CHALLENGES,
) : AutoCloseable {
    private val running: AtomicBoolean = AtomicBoolean(false)
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val admission: Semaphore = Semaphore(maximumConnections)
    private val activeSockets: MutableSet<SSLSocket> = ConcurrentHashMap.newKeySet()
    private val challengeLock: Any = Any()
    private val acceptedChallenges: MutableMap<String, Long> = mutableMapOf()
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(maximumConnections + 1),
    )
    private var listener: SSLServerSocket? = null

    init {
        require(bindHost.isNotBlank())
        require(bindPort in 0..65_535)
        require(maximumConnections in 1..MAXIMUM_CONFIGURABLE_CONNECTIONS)
        require(maximumTrackedChallenges in 1..MAXIMUM_CONFIGURABLE_TRACKED_CHALLENGES)
    }

    /** Starts the TLS listener once; repeated calls while running are idempotent. */
    public fun start() {
        check(!closed.get()) { "Companion control gateway is already closed." }
        if (!running.compareAndSet(false, true)) return
        try {
            val created = serverSocketFactory.createServerSocket() as SSLServerSocket
            created.reuseAddress = false
            created.needClientAuth = false
            created.enabledProtocols = created.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
            created.bind(InetSocketAddress(bindHost, bindPort), LISTENER_BACKLOG)
            listener = created
            scope.launch { acceptLoop(created) }
        } catch (failure: Throwable) {
            running.set(false)
            close()
            throw failure
        }
    }

    /** Returns the bound port after [start], including an operating-system-assigned port when configured with zero. */
    public val boundPort: Int?
        get() = listener?.localPort

    private fun acceptLoop(server: SSLServerSocket) {
        while (running.get()) {
            val socket = try {
                server.accept() as SSLSocket
            } catch (_: SocketException) {
                break
            }
            if (!admission.tryAcquire()) {
                runCatching { socket.close() }
                continue
            }
            activeSockets.add(socket)
            scope.launch {
                try {
                    handle(socket)
                } finally {
                    runCatching(socket::close)
                    activeSockets.remove(socket)
                    admission.release()
                }
            }
        }
    }

    private suspend fun handle(socket: SSLSocket) {
        socket.soTimeout = REQUEST_TIMEOUT_MILLIS
        socket.startHandshake()
        val request = readRequest(socket) ?: return socket.respond(400, "bad_request")
        if (request.method == "POST" && request.path == CompanionBootstrapProtocol.REDEEM_PATH) {
            return redeemBootstrap(socket, request)
        }
        if (request.method == "POST" && request.path == CompanionControlProtocol.PAIR_PATH) {
            return completePairing(socket, request)
        }
        if (request.method == "POST" && request.path == CompanionControlProtocol.REFRESH_PATH) {
            return refreshCredential(socket, request)
        }
        if (request.method == "POST" && request.path == CompanionControlProtocol.RECONCILE_PATH) {
            return reconcileEndpoint(socket, request)
        }
        if (request.body.isNotEmpty()) return socket.respond(400, "body_not_allowed")
        val authorization = request.authorization ?: return socket.respond(401, "authorization_required")
        val authentication = pairing.authenticate(
            authorization.deviceId,
            authorization.credential,
            DeviceScope.SETUP_ARTIFACT_READ,
        )
        if (authentication !is DeviceAuthenticationResult.Authenticated) {
            return socket.respond(401, "authorization_rejected")
        }
        when {
            request.method == "GET" && request.path == CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH -> {
                val certificate = rootCertificateDer()
                if (certificate.isEmpty() || certificate.size > CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES) {
                    socket.respond(503, "certificate_unavailable")
                } else {
                    socket.respond(
                        statusCode = 200,
                        reason = "OK",
                        mediaType = CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                        body = certificate,
                    )
                }
            }
            request.method == "GET" && request.path == CompanionCertificateProtocol.APPLE_PROFILE_PATH -> {
                val certificate = rootCertificateDer()
                if (certificate.isEmpty() || certificate.size > CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES) {
                    socket.respond(503, "certificate_unavailable")
                } else {
                    val profile = AppleRootCertificateProfileRenderer.render(certificate).encodeToByteArray()
                    if (profile.size > CompanionCertificateProtocol.MAXIMUM_INSTALLATION_ARTIFACT_BYTES) {
                        socket.respond(503, "certificate_profile_unavailable")
                    } else {
                        socket.respond(
                            statusCode = 200,
                            reason = "OK",
                            mediaType = CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE,
                            body = profile,
                            extraHeaders = mapOf(
                                "Content-Disposition" to "attachment; filename=\"$APPLE_PROFILE_FILE_NAME\"",
                            ),
                        )
                    }
                }
            }
            request.method == "POST" && request.path == CompanionCertificateProtocol.TRUST_CHALLENGE_PATH -> {
                val challenge = request.challenge ?: return socket.respond(400, "challenge_required")
                when (claimChallenge(authorization.deviceId, challenge)) {
                    ChallengeClaim.REPLAYED -> return socket.respond(409, "challenge_replayed")
                    ChallengeClaim.CAPACITY_REACHED -> return socket.respond(429, "challenge_capacity_reached")
                    ChallengeClaim.ACCEPTED -> Unit
                }
                socket.respond(
                    statusCode = 204,
                    reason = "No Content",
                    mediaType = "application/octet-stream",
                    body = ByteArray(0),
                    extraHeaders = mapOf(CompanionCertificateProtocol.CHALLENGE_HEADER to challenge.value),
                )
            }
            else -> socket.respond(404, "not_found")
        }
    }

    private suspend fun redeemBootstrap(socket: SSLSocket, request: ControlGatewayRequest) {
        if (request.contentType != CompanionBootstrapProtocol.REQUEST_MEDIA_TYPE) {
            return socket.respond(415, "unsupported_media_type")
        }
        val redemption = runCatching { redemptionCodec.decode(request.body) }.getOrNull()
            ?: return socket.respond(400, "invalid_redemption_request")
        when (val result = redeemOnboarding.execute(redemption)) {
            PairingOnboardingRedemptionResult.Rejected -> socket.respond(401, "invitation_rejected")
            is PairingOnboardingRedemptionResult.Redeemed -> socket.respond(
                statusCode = 200,
                reason = "OK",
                mediaType = CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE,
                body = invitationCodec.encode(result.invitation),
            )
        }
    }

    private suspend fun completePairing(socket: SSLSocket, request: ControlGatewayRequest) {
        if (request.authorization != null) return socket.respond(400, "authorization_not_allowed")
        if (request.contentType != CompanionControlProtocol.PAIR_REQUEST_MEDIA_TYPE) {
            return socket.respond(415, "unsupported_media_type")
        }
        val completion = runCatching { pairingCompletionCodec.decode(request.body) }.getOrNull()
            ?: return socket.respond(400, "invalid_pairing_request")
        when (val result = pairing.complete(completion)) {
            is PairingCompletionResult.Rejected -> socket.respond(401, "pairing_rejected")
            is PairingCompletionResult.Paired -> socket.respond(
                statusCode = 200,
                reason = "OK",
                mediaType = CompanionControlProtocol.PAIR_RESPONSE_MEDIA_TYPE,
                body = pairingGrantCodec.encode(
                    CompanionPairingGrant(
                        credential = result.issued.credential,
                        scopes = result.issued.device.scopes,
                        credentialExpiresAtEpochMillis = result.issued.device.credentialExpiresAtEpochMillis,
                    ),
                ),
            )
        }
    }

    private suspend fun refreshCredential(socket: SSLSocket, request: ControlGatewayRequest) {
        if (request.contentType != CompanionControlProtocol.REFRESH_REQUEST_MEDIA_TYPE) {
            return socket.respond(415, "unsupported_media_type")
        }
        val authorization = request.authorization ?: return socket.respond(401, "authorization_required")
        val refresh = runCatching { refreshRequestCodec.decode(request.body) }.getOrNull()
            ?: return socket.respond(400, "invalid_refresh_request")
        if (refresh.deviceId != authorization.deviceId) return socket.respond(401, "authorization_rejected")
        when (val result = pairing.refreshCredential(authorization.deviceId, authorization.credential)) {
            is PairingCredentialRefreshResult.Rejected -> socket.respond(401, "authorization_rejected")
            is PairingCredentialRefreshResult.Refreshed -> socket.respond(
                statusCode = 200,
                reason = "OK",
                mediaType = CompanionControlProtocol.REFRESH_RESPONSE_MEDIA_TYPE,
                body = refreshGrantCodec.encode(
                    CompanionCredentialRefreshGrant(
                        credential = result.issued.credential,
                        credentialExpiresAtEpochMillis = result.issued.device.credentialExpiresAtEpochMillis,
                    ),
                ),
            )
        }
    }

    private suspend fun reconcileEndpoint(socket: SSLSocket, request: ControlGatewayRequest) {
        if (request.contentType != CompanionControlProtocol.RECONCILE_REQUEST_MEDIA_TYPE) {
            return socket.respond(415, "unsupported_media_type")
        }
        val authorization = request.authorization ?: return socket.respond(401, "authorization_required")
        val authentication = pairing.authenticate(
            authorization.deviceId,
            authorization.credential,
            DeviceScope.SETUP_ARTIFACT_READ,
        )
        if (authentication !is DeviceAuthenticationResult.Authenticated) {
            return socket.respond(401, "authorization_rejected")
        }
        val reconciliation = runCatching { endpointCodec.decodeRequest(request.body) }.getOrNull()
            ?: return socket.respond(400, "invalid_endpoint_request")
        val descriptor = endpointDescriptor() ?: return socket.respond(503, "endpoint_unavailable")
        if (!descriptor.accepts(reconciliation.desktopId)) return socket.respond(409, "desktop_identity_mismatch")
        socket.respond(
            statusCode = 200,
            reason = "OK",
            mediaType = CompanionControlProtocol.RECONCILE_RESPONSE_MEDIA_TYPE,
            body = endpointCodec.encodeDescriptor(descriptor),
        )
    }

    private fun claimChallenge(
        deviceId: RegisteredDeviceId,
        challenge: CompanionCertificateChallengeNonce,
    ): ChallengeClaim = synchronized(challengeLock) {
        val now = nowEpochMillis()
        acceptedChallenges.entries.removeIf { (_, acceptedAt) ->
            now >= acceptedAt && now - acceptedAt >= CHALLENGE_REPLAY_WINDOW_MILLIS
        }
        val key = "${deviceId.value}:${challenge.value}"
        when {
            key in acceptedChallenges -> ChallengeClaim.REPLAYED
            acceptedChallenges.size >= maximumTrackedChallenges -> ChallengeClaim.CAPACITY_REACHED
            else -> {
                acceptedChallenges[key] = now
                ChallengeClaim.ACCEPTED
            }
        }
    }

    private fun readRequest(socket: SSLSocket): ControlGatewayRequest? {
        val output = ByteArrayOutputStream()
        var matched = 0
        while (output.size() < MAXIMUM_HEADER_BYTES) {
            val next = socket.inputStream.read()
            if (next < 0) return null
            output.write(next)
            matched = when {
                HEADER_END[matched].toInt() == next -> matched + 1
                HEADER_END[0].toInt() == next -> 1
                else -> 0
            }
            if (matched == HEADER_END.size) break
        }
        if (matched != HEADER_END.size) return null
        val lines = runCatching {
            output.toByteArray().decodeToString(throwOnInvalidSequence = true)
                .removeSuffix("\r\n\r\n")
                .split("\r\n")
        }.getOrNull() ?: return null
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size != 3 || requestLine[2] != "HTTP/1.1") return null
        val headerPairs = lines.drop(1).map { line ->
            val separator = line.indexOf(':')
            if (separator <= 0 || line.startsWith(' ') || line.startsWith('\t')) return null
            line.substring(0, separator).lowercase() to line.substring(separator + 1).trim()
        }
        if (headerPairs.map(Pair<String, String>::first).distinct().size != headerPairs.size) return null
        val headers = headerPairs.toMap()
        if ("transfer-encoding" in headers) return null
        val contentLength = when (val declaredLength = headers["content-length"]) {
            null -> 0
            else -> declaredLength.toIntOrNull() ?: return null
        }
        if (contentLength !in 0..CompanionControlProtocol.MAXIMUM_REQUEST_BYTES) return null
        val body = ByteArray(contentLength)
        var bodyOffset = 0
        while (bodyOffset < body.size) {
            val read = socket.inputStream.read(body, bodyOffset, body.size - bodyOffset)
            if (read < 0) return null
            bodyOffset += read
        }
        val authorizationHeader = headers["authorization"]
        val authorization = authorizationHeader?.parseAuthorization()
        if (authorizationHeader != null && authorization == null) return null
        val challenge = headers[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()]?.let { value ->
            runCatching { CompanionCertificateChallengeNonce(value) }.getOrNull()
        }
        return ControlGatewayRequest(
            method = requestLine[0],
            path = requestLine[1],
            authorization = authorization,
            challenge = challenge,
            contentType = headers["content-type"]?.substringBefore(';')?.trim()?.lowercase(),
            body = body,
        )
    }

    /** Stops admission and all listener work. Repeated calls are safe. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        runCatching { listener?.close() }
        listener = null
        activeSockets.forEach { socket -> runCatching(socket::close) }
        activeSockets.clear()
        synchronized(challengeLock) { acceptedChallenges.clear() }
        scope.cancel()
    }

    private fun SSLSocket.respond(statusCode: Int, token: String) {
        respond(statusCode, statusReason(statusCode), "text/plain; charset=utf-8", token.encodeToByteArray())
    }

    private fun SSLSocket.respond(
        statusCode: Int,
        reason: String,
        mediaType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val header = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $mediaType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Connection: close\r\n\r\n")
        }.encodeToByteArray()
        outputStream.write(header)
        outputStream.write(body)
        outputStream.flush()
    }

    public companion object {
        /** Stable DNS identity used for SNI, hostname validation, and desktop leaf generation. */
        public const val TLS_SERVER_NAME: String = CompanionCertificateProtocol.TLS_SERVER_NAME

        private val HEADER_END: ByteArray = "\r\n\r\n".encodeToByteArray()
        private val SAFE_TOKEN: Regex = Regex("[A-Za-z0-9._~-]{1,512}")
        private const val MAXIMUM_HEADER_BYTES: Int = 32 * 1024
        private const val REQUEST_TIMEOUT_MILLIS: Int = 10_000
        private const val LISTENER_BACKLOG: Int = 64
        private const val DEFAULT_MAXIMUM_CONNECTIONS: Int = 32
        private const val MAXIMUM_CONFIGURABLE_CONNECTIONS: Int = 256
        private const val DEFAULT_MAXIMUM_TRACKED_CHALLENGES: Int = 4_096
        private const val MAXIMUM_CONFIGURABLE_TRACKED_CHALLENGES: Int = 65_536
        private const val CHALLENGE_REPLAY_WINDOW_MILLIS: Long = 5L * 60L * 1_000L
        private const val APPLE_PROFILE_FILE_NAME: String = "knet-ca.mobileconfig"

        private fun String.parseAuthorization(): ControlGatewayAuthorization? {
            if (!startsWith("Bearer ", ignoreCase = true)) return null
            val token = substringAfter(' ').trim()
            val device = token.substringBefore(':').takeIf { it.matches(SAFE_TOKEN) } ?: return null
            val credential = token.substringAfter(':', "").takeIf { it.matches(SAFE_TOKEN) } ?: return null
            return ControlGatewayAuthorization(RegisteredDeviceId(device), credential)
        }

        private fun statusReason(statusCode: Int): String = when (statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            415 -> "Unsupported Media Type"
            429 -> "Too Many Requests"
            503 -> "Service Unavailable"
            else -> "Error"
        }
    }
}

private enum class ChallengeClaim {
    ACCEPTED,
    REPLAYED,
    CAPACITY_REACHED,
}

private data class ControlGatewayRequest(
    val method: String,
    val path: String,
    val authorization: ControlGatewayAuthorization?,
    val challenge: CompanionCertificateChallengeNonce?,
    val contentType: String?,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ControlGatewayRequest

        if (method != other.method) return false
        if (path != other.path) return false
        if (authorization != other.authorization) return false
        if (challenge != other.challenge) return false
        if (contentType != other.contentType) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + authorization.hashCode()
        result = 31 * result + challenge.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

private data class ControlGatewayAuthorization(
    val deviceId: RegisteredDeviceId,
    val credential: String,
)
