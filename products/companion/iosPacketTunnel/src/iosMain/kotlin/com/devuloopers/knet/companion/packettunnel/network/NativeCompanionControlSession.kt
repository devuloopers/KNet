package com.devuloopers.knet.companion.packettunnel.network

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.connectivity.control.createIosCompanionSessionTransport
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.companion.packettunnel.options.TunnelStartOptions
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Extension-owned Darwin control channel. iOS packet shaping remains unavailable, so enabled packet policies
 * are acknowledged UNSUPPORTED while presence stays alive; desktop-proxy shaping remains independent.
 */
internal class NativeCompanionControlSession(
    private val options: TunnelStartOptions,
    private val onRevoked: () -> Unit,
    private val transport: CompanionSessionTransport = createIosCompanionSessionTransport(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onReconnecting: (Boolean) -> Unit = {},
) {
    private var job: Job? = null

    fun start() {
        if (job != null || options.controlHost == null || options.controlPort == null) return
        val authorization = options.authorization.removePrefix("Bearer ").split(':')
        if (authorization.size != 2) return
        val access = CompanionSessionAccess(
            CompanionServiceEndpoint(options.controlHost, options.controlPort.toInt(), CompanionEndpointScheme.HTTPS),
            RegisteredDeviceId(authorization[0]), CompanionRootCertificate(options.rootCertificate),
            Sha256Fingerprint(options.rootSha256), Sha256Fingerprint(options.transportSha256))
        val runId = kotlin.uuid.Uuid.random().toString()
        job = scope.launch {
            var attempt = 0
            onReconnecting(true)
            while (isActive) {
                var connection: CompanionSessionConnection? = null
                try {
                    connection = withTimeout(10_000L) { transport.connect(access, authorization[1]) }
                    val session = connection
                    coroutineScope {
                        val sendLock = Mutex()
                        var sequence = 0L
                        suspend fun send(kind: CompanionSessionMessageKind, revision: Long = 0L,
                            result: CompanionPolicyAcknowledgement? = null) = sendLock.withLock {
                            withTimeout(2_000L) { session.send(CompanionSessionMessage(kind, runId, ++sequence, revision, acknowledgement = result)) }
                        }
                        send(CompanionSessionMessageKind.START)
                        val welcome = withTimeout(10_000L) { session.receive() }
                        require(welcome.kind == CompanionSessionMessageKind.WELCOME && welcome.runId == runId)
                        onReconnecting(false)
                        val connectedAt = kotlin.time.TimeSource.Monotonic.markNow()
                        val heartbeat = launch {
                            while (isActive) { delay(CompanionSessionProtocol.HEARTBEAT_MILLIS); send(CompanionSessionMessageKind.HEARTBEAT) }
                        }
                        try {
                            var revision = 0L
                            while (isActive) {
                                val message = withTimeout(CompanionSessionProtocol.RECEIVE_TIMEOUT_MILLIS) { session.receive() }
                                require(message.runId == runId)
                                when (message.kind) {
                                    CompanionSessionMessageKind.CONFIGURATION -> if (message.revision > revision) {
                                        revision = message.revision
                                        send(CompanionSessionMessageKind.POLICY_ACK, revision,
                                            if (message.configuration?.enabled == false) CompanionPolicyAcknowledgement.APPLIED
                                            else CompanionPolicyAcknowledgement.UNSUPPORTED)
                                    }
                                    CompanionSessionMessageKind.POLICY_RENEWAL -> Unit
                                    CompanionSessionMessageKind.REVOKED -> { onRevoked(); throw Revoked() }
                                    else -> throw CompanionSessionException(CompanionSessionFailure.INVALID_MESSAGE)
                                }
                            }
                        } finally { withContext(NonCancellable) {
                            heartbeat.cancelAndJoin()
                            if (connectedAt.elapsedNow().inWholeMilliseconds >= CompanionSessionProtocol.HEARTBEAT_MILLIS) attempt = 0
                            withContext(NonCancellable) { runCatching { withTimeout(500L) { send(CompanionSessionMessageKind.STOP) } } }
                        } }
                    }
                } catch (_: Revoked) { return@launch }
                catch (_: TimeoutCancellationException) { /* Retry a bounded handshake or send. */ }
                catch (_: CancellationException) { currentCoroutineContext().ensureActive() }
                catch (failure: CompanionSessionException) {
                    if (failure.reason == CompanionSessionFailure.UNAUTHORIZED) { onRevoked(); return@launch }
                    if (failure.reason == CompanionSessionFailure.UNSUPPORTED) return@launch
                } catch (_: Exception) { /* Recovery remains bounded; no credential-bearing errors are logged. */ }
                finally { withContext(NonCancellable) { runCatching { withTimeoutOrNull(1000L) { connection?.close() } } } }
                onReconnecting(true)
                delay(minOf(5000L, 500L shl minOf(attempt++, 3)) + kotlin.random.Random.nextLong(0L, 251L))
            }
        }
    }

    private class Revoked : Exception()

    /** Cancels the extension-owned run; its finally block performs a bounded best-effort stop/close. */
    fun close() { job?.cancel(); job = null; scope.cancel() }
}
