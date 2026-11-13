package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.AttributeKey
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * One TLS control endpoint hosting the unchanged bounded HTTPS operations and authenticated VPN WebSockets.
 * Netty owns protocol framing; application coordinators own identity and lease semantics. Long-lived channels
 * use a different admission budget from short HTTPS operations and never enter the capture/simulator pipeline.
 */
public class KtorCompanionControlGateway(
    private val bindHost: String,
    private val bindPort: Int,
    private val keyStore: KeyStore,
    private val http: CompanionControlGateway,
    private val pairing: PairingCoordinator,
    private val presence: DevicePresenceCoordinator,
    private val policies: Flow<PacketConditionConfiguration>,
    private val maximumSessions: Int = 128,
) : CompanionControlGatewayLifecycle {
    private val mutableState = MutableStateFlow<CompanionControlGatewayState>(CompanionControlGatewayState.Stopped)
    private val closed = AtomicBoolean(false)
    private val sessions = Semaphore(maximumSessions)
    private val requests = Semaphore(32)
    private val physicalConnections = java.util.concurrent.Semaphore(maximumSessions + 32)
    private var server: EmbeddedServer<*, *>? = null
    private var timer: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Listener health used by existing discovery and retry ownership. */
    override val state: StateFlow<CompanionControlGatewayState> = mutableState.asStateFlow()

    init { require(bindPort in 0..65535 && maximumSessions in 1..4096) }

    /** Opens one embedded TLS listener; startup failure leaves retryable state without a leaked server. */
    @Synchronized
    override fun start() {
        check(!closed.get())
        if (server != null) return
        mutableState.value = CompanionControlGatewayState.Starting
        try {
            val created = embeddedServer(Netty, configure = {
                connectionGroupSize = 1
                workerGroupSize = 2
                callGroupSize = 2
                maxHeaderSize = 32 * 1024
                // The control protocol uses HTTP/1.1/WSS, as the previous listener did. Installing this
                // guard during pipeline initialization bounds pending TLS handshakes and idle sockets too.
                enableHttp2 = false
                channelPipelineConfig = { addFirst("knet-control-admission", ControlConnectionAdmission(physicalConnections)) }
                sslConnector(keyStore, "knet-companion", { CharArray(0) }, { CharArray(0) }) {
                    host = bindHost
                    port = bindPort
                }
            }) {
                install(WebSockets) {
                    maxFrameSize = CompanionSessionProtocol.MAXIMUM_MESSAGE_BYTES.toLong()
                    pingPeriod = 15.seconds
                    timeout = 30.seconds
                    channels {
                        incoming = bounded(16, ChannelOverflow.CLOSE)
                        outgoing = bounded(16, ChannelOverflow.SUSPEND)
                    }
                }
                routing {
                    route(CompanionSessionProtocol.PATH) {
                        intercept(ApplicationCallPipeline.Call) {
                            val authorization = parseAuthorization(call.request.headers["Authorization"])
                            if (authorization == null || call.request.headers["Origin"] != null ||
                                withContext(Dispatchers.IO) { pairing.authenticate(authorization.deviceId,
                                    authorization.credential, DeviceScope.PROXY_STREAM) } !is DeviceAuthenticationResult.Authenticated) {
                                call.respond(HttpStatusCode.Unauthorized)
                                finish()
                            } else call.attributes.put(AUTHORIZATION, authorization)
                        }
                        webSocket {
                            if (!sessions.tryAcquire()) {
                                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "session_capacity"))
                                return@webSocket
                            }
                            try { maintainSession(call.attributes[AUTHORIZATION]) }
                            finally { sessions.release() }
                        }
                    }
                    route("/{path...}") { handle { dispatchHttp(call) } }
                }
            }
            server = created
            created.start(wait = false)
            // Connector resolution is suspendable. Failure must release the failed listener and remain retryable.
            scope.launch {
                try {
                    val port = created.engine.resolvedConnectors().single().port
                    if (!closed.get()) mutableState.value = CompanionControlGatewayState.Listening(port)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    synchronized(this@KtorCompanionControlGateway) {
                        if (server === created && !closed.get()) {
                            created.stop(0, 1000)
                            server = null
                            timer?.cancel()
                            mutableState.value = CompanionControlGatewayState.Failed(CompanionControlGatewayFailure.BIND_FAILED)
                        }
                    }
                }
            }
            timer = scope.launch { while (isActive) { presence.expire(); delay(1000) } }
        } catch (failure: Throwable) {
            server?.stop(0, 1000)
            server = null
            mutableState.value = CompanionControlGatewayState.Failed(CompanionControlGatewayFailure.BIND_FAILED)
            throw failure
        }
    }

    private suspend fun DefaultWebSocketServerSession.maintainSession(authorization: ControlGatewayAuthorization): Unit = coroutineScope {
        var token: Long? = null
        var explicitStop = false
        try {
            val first = withTimeout(10_000L) { readControlMessage() }
            require(first.kind == CompanionSessionMessageKind.START)
            val opened = presence.open(authorization.deviceId, first.runId, first.sequence)
            token = opened
            sendCompanionControlMessage(outgoing, CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, first.runId))
            var revision = 0L
            var currentPolicy: PacketConditionConfiguration? = null
            val policyLock = kotlinx.coroutines.sync.Mutex()
            val updater = launch {
                policies.distinctUntilChanged().collect { configuration ->
                    policyLock.lock()
                    try {
                        check(revision < Long.MAX_VALUE)
                        revision++
                        currentPolicy = configuration
                        sendCompanionControlMessage(outgoing, CompanionSessionMessage(
                            CompanionSessionMessageKind.CONFIGURATION, first.runId, revision = revision, configuration = configuration))
                    } finally { policyLock.unlock() }
                }
            }
            val renewer = launch {
                while (isActive) {
                    delay(CompanionSessionProtocol.POLICY_RENEWAL_MILLIS)
                    val authorized = withContext(Dispatchers.IO) { pairing.authenticate(authorization.deviceId,
                        authorization.credential, DeviceScope.PROXY_STREAM) } is DeviceAuthenticationResult.Authenticated
                    if (!authorized || !presence.isCurrent(authorization.deviceId, opened)) {
                        presence.stop(authorization.deviceId, opened)
                        sendCompanionControlMessage(outgoing, CompanionSessionMessage(CompanionSessionMessageKind.REVOKED, first.runId))
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session_no_longer_authorized"))
                        return@launch
                    }
                    policyLock.lock()
                    try {
                        if (currentPolicy != null) sendCompanionControlMessage(outgoing, CompanionSessionMessage(
                            CompanionSessionMessageKind.POLICY_RENEWAL, first.runId, revision = revision))
                    } finally { policyLock.unlock() }
                }
            }
            try {
                var sequence = first.sequence
                while (isActive) {
                    val message = withTimeout(65_000L) { readControlMessage() }
                    require(message.runId == first.runId && message.sequence > sequence &&
                        CompanionSessionProtocol.allowsClient(message.kind))
                    sequence = message.sequence
                    when (message.kind) {
                        CompanionSessionMessageKind.HEARTBEAT -> check(presence.heartbeat(authorization.deviceId, opened, first.runId, sequence))
                        CompanionSessionMessageKind.STOP -> { presence.stop(authorization.deviceId, opened); explicitStop = true; break }
                        CompanionSessionMessageKind.POLICY_ACK -> {
                            require(policyLock.withLock { message.revision <= revision })
                            presence.acknowledge(authorization.deviceId, opened,
                                com.devuloopers.knet.application.contract.device.DevicePolicyObservation(message.revision,
                                    checkNotNull(message.acknowledgement)))
                        }
                        else -> error("Unexpected client control operation.")
                    }
                }
            } finally { updater.cancelAndJoin(); renewer.cancelAndJoin() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid_control_session")) }
        finally { token?.let { generation -> if (!explicitStop) presence.disconnected(authorization.deviceId, generation) } }
    }

    private suspend fun DefaultWebSocketServerSession.readControlMessage(): CompanionSessionMessage {
        val frame = incoming.receive() as? Frame.Text ?: error("Control sessions require text frames.")
        require(frame.data.size <= CompanionSessionProtocol.MAXIMUM_MESSAGE_BYTES)
        return CompanionSessionProtocol.decode(frame.readText())
    }

    private suspend fun dispatchHttp(call: ApplicationCall) {
        if (!requests.tryAcquire()) { call.respond(HttpStatusCode.ServiceUnavailable); return }
        try { withTimeout(10_000L) {
            val headers = call.request.headers
            require(headers.entries().all { (_, values) -> values.size == 1 })
            require(headers["Transfer-Encoding"] == null)
            val length = headers["Content-Length"]?.toIntOrNull() ?: 0
            require(length in 0..CompanionControlProtocol.MAXIMUM_REQUEST_BYTES)
            // Netty owns HTTP framing. Reading an extra byte from a keep-alive GET can wait for a body that will never arrive.
            val body = if (length == 0) ByteArray(0) else call.receiveChannel().readRemaining(length.toLong()).readByteArray()
            require(body.size == length)
            val rawAuthorization = headers["Authorization"]
            val authorization = parseAuthorization(rawAuthorization)
            require(rawAuthorization == null || authorization != null)
            val challenge = headers[CompanionCertificateProtocol.CHALLENGE_HEADER]?.let(::CompanionCertificateChallengeNonce)
            val response = withTimeout(10_000L) { withContext(Dispatchers.IO) { http.dispatch(ControlGatewayRequest(
                call.request.httpMethod.value, call.request.uri, authorization, challenge,
                headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase(), body)) } }
            call.response.headers.append("Cache-Control", "no-store")
            call.response.headers.append("X-Content-Type-Options", "nosniff")
            response.extraHeaders.forEach { (name, value) -> call.response.headers.append(name, value) }
            call.respondBytes(response.body, ContentType.parse(response.mediaType), HttpStatusCode.fromValue(response.statusCode))
        } } catch (_: TimeoutCancellationException) { call.respond(HttpStatusCode.RequestTimeout) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { call.respond(HttpStatusCode.BadRequest) }
        finally { requests.release() }
    }

    /** Stops native listener resources and all ephemeral leases. It never owns captured traffic or pairing storage. */
    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        timer?.cancel()
        server?.stop(0, 2000)
        server = null
        http.close()
        presence.clear()
        scope.cancel()
        mutableState.value = CompanionControlGatewayState.Stopped
    }

    private fun parseAuthorization(value: String?): ControlGatewayAuthorization? {
        if (value == null || !value.startsWith("Bearer ")) return null
        val components = value.removePrefix("Bearer ").split(':')
        if (components.size != 2 || components.any { token -> !token.matches(Regex("[A-Za-z0-9._~-]{1,512}")) } ||
            components[1].length < 16) return null
        return ControlGatewayAuthorization(RegisteredDeviceId(components[0]), components[1])
    }

    private companion object {
        val AUTHORIZATION = AttributeKey<ControlGatewayAuthorization>("knet.control.authorization")
    }
}
