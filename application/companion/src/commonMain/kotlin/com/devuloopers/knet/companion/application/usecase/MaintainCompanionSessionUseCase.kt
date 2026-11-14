package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Maintains one VPN-owned control channel with bounded reconnects and an independent five-second policy lease.
 * Native policy changes remain behind the existing sink. A socket acknowledgement never fabricates application.
 * Pairing, key storage, credential refresh and endpoint discovery remain in their existing use cases.
 */
public class MaintainCompanionSessionUseCase(
    private val transport: CompanionSessionTransport,
    private val credentials: CompanionCredentialStore,
    private val registrations: CompanionRegistrationRepository,
    private val fallback: MaintainCompanionPacketPolicyUseCase,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutableState = MutableStateFlow<CompanionSessionObservation?>(null)

    /** Current VPN-owned control health. Idle and legacy HTTPS peers have no WebSocket observation. */
    public val state: kotlinx.coroutines.flow.StateFlow<CompanionSessionObservation?> = mutableState.asStateFlow()

    /** Runs for the exact VPN identity until cancelled, switched, unauthorized or the native owner stops. */
    public suspend fun execute(registration: CompanionRegistration, runId: String,
        initiallyApplied: PacketConditionConfiguration, sink: CompanionPacketPolicySink): Unit = coroutineScope {
        require(runId.isNotBlank())
        mutableState.value = CompanionSessionObservation(registration.desktopId, runId, CompanionSessionPhase.CONNECTING)
        var rejected = false
        fun report(phase: CompanionSessionPhase) {
            mutableState.update { observed -> if (observed?.runId == runId) observed.copy(phase = phase) else observed }
        }
        val policyLock = Mutex()
        var applied = initiallyApplied
        var lastFresh = timeSource.markNow()
        var leaseValid = true
        var currentConnection: CompanionSessionConnection? = null
        val watchdog = launch {
            while (isActive) {
                delay(250L)
                val expiredConnection = policyLock.withLock {
                    if (leaseValid && lastFresh.elapsedNow().inWholeMilliseconds >= CompanionSessionProtocol.POLICY_LEASE_MILLIS) {
                        leaseValid = false
                        if (applied != PacketConditionConfiguration.Disabled) {
                            releasePolicy(sink)
                            applied = PacketConditionConfiguration.Disabled
                        }
                        currentConnection
                    } else null
                }
                // An expired snapshot needs a fresh CONFIGURATION. A renewal cannot resurrect disabled shaping.
                expiredConnection?.let { releaseConnection(it) }
            }
        }
        var attempt = 0
        try {
            while (isActive) {
                val current = registrations.activeRegistration.value
                if (!sameIdentity(registration, current)) return@coroutineScope
                current!!
                var connection: CompanionSessionConnection? = null
                var attemptedCredential: String? = null
                try {
                    val credential = credentials.read(current.credentialReference) ?: return@coroutineScope
                    attemptedCredential = credential
                    connection = withTimeout(10_000L) { transport.connect(CompanionSessionAccess.fromRegistration(current), credential) }
                    val session = connection
                    policyLock.withLock { currentConnection = session }
                    coroutineScope {
                        val sendLock = Mutex()
                        var sequence = 0L
                        suspend fun send(kind: CompanionSessionMessageKind, revision: Long = 0L,
                            acknowledgement: CompanionPolicyAcknowledgement? = null) = sendLock.withLock {
                            check(sequence < Long.MAX_VALUE)
                            withTimeout(2_000L) { session.send(CompanionSessionMessage(kind, runId, ++sequence, revision,
                                acknowledgement = acknowledgement)) }
                        }
                        send(CompanionSessionMessageKind.START)
                        val welcome = withTimeout(10_000L) { session.receive() }
                        require(welcome.kind == CompanionSessionMessageKind.WELCOME && welcome.runId == runId)
                        report(CompanionSessionPhase.CONNECTED)
                        val connectedAt = timeSource.markNow()
                        var revision = 0L
                        val identityWatcher = launch {
                            registrations.activeRegistration.first { it != current }
                            releaseConnection(session)
                        }
                        val heartbeat = launch {
                            while (isActive) {
                                delay(CompanionSessionProtocol.HEARTBEAT_MILLIS)
                                if (registrations.activeRegistration.value != current) {
                                    session.close()
                                    return@launch
                                }
                                send(CompanionSessionMessageKind.HEARTBEAT)
                            }
                        }
                        try {
                            while (isActive) {
                                // The policy lease can be disabled by UNSUPPORTED. Control liveness must still
                                // detect a half-open connection and perform a fresh START after desktop restart.
                                val message = withTimeout(CompanionSessionProtocol.RECEIVE_TIMEOUT_MILLIS) { session.receive() }
                                require(message.runId == runId)
                                if (registrations.activeRegistration.value != current) break
                                when (message.kind) {
                                    CompanionSessionMessageKind.CONFIGURATION -> {
                                        if (message.revision <= revision) continue
                                        val target = checkNotNull(message.configuration)
                                        val result = policyLock.withLock {
                                            val result = try { withTimeout(2_000L) { sink.apply(target) } }
                                            catch (_: TimeoutCancellationException) { null }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { null }
                                            if (result == PacketPolicyUpdateResult.APPLIED) {
                                                applied = target
                                                lastFresh = timeSource.markNow()
                                                leaseValid = true
                                                revision = message.revision
                                            } else if (result == PacketPolicyUpdateResult.UNSUPPORTED) {
                                                // Lack of packet shaping is not a presence failure. Release any old
                                                // policy and keep the authenticated heartbeat channel open.
                                                releasePolicy(sink)
                                                applied = PacketConditionConfiguration.Disabled
                                                leaseValid = false
                                                revision = message.revision
                                            }
                                            result
                                        }
                                        val acknowledgement = when (result) {
                                            PacketPolicyUpdateResult.APPLIED -> CompanionPolicyAcknowledgement.APPLIED
                                            PacketPolicyUpdateResult.INACTIVE -> CompanionPolicyAcknowledgement.INACTIVE
                                            PacketPolicyUpdateResult.UNSUPPORTED -> CompanionPolicyAcknowledgement.UNSUPPORTED
                                            null -> CompanionPolicyAcknowledgement.FAILED
                                        }
                                        send(CompanionSessionMessageKind.POLICY_ACK, message.revision, acknowledgement)
                                        if (result == PacketPolicyUpdateResult.INACTIVE) throw SessionStopped()
                                    }
                                    CompanionSessionMessageKind.POLICY_RENEWAL -> policyLock.withLock {
                                        if (message.revision == revision && revision > 0L && leaseValid) lastFresh = timeSource.markNow()
                                    }
                                    CompanionSessionMessageKind.REVOKED -> {
                                        if (credentials.read(current.credentialReference) != credential) break
                                        throw SessionStopped(rejected = true)
                                    }
                                    else -> throw CompanionSessionException(CompanionSessionFailure.INVALID_MESSAGE)
                                }
                            }
                        } finally { withContext(NonCancellable) {
                            identityWatcher.cancelAndJoin()
                            heartbeat.cancelAndJoin()
                            if (connectedAt.elapsedNow().inWholeMilliseconds >= CompanionSessionProtocol.HEARTBEAT_MILLIS) attempt = 0
                            withContext(NonCancellable) {
                                runCatching { withTimeoutOrNull(500L) { send(CompanionSessionMessageKind.STOP) } }
                            }
                        } }
                    }
                } catch (stopped: SessionStopped) {
                    rejected = stopped.rejected
                    if (rejected) report(CompanionSessionPhase.REJECTED)
                    return@coroutineScope
                }
                catch (_: TimeoutCancellationException) { /* Bounded handshake or send expired; reconnect below. */ }
                catch (_: CancellationException) {
                    // Ktor/native socket jobs can cancel their receive channel without cancelling the VPN.
                    // Preserve actual owner cancellation; a connection-local cancellation is recoverable.
                    currentCoroutineContext().ensureActive()
                }
                catch (failure: CompanionSessionException) {
                    if (failure.reason == CompanionSessionFailure.UNAUTHORIZED &&
                        credentials.read(current.credentialReference) == attemptedCredential) {
                        rejected = true
                        report(CompanionSessionPhase.REJECTED)
                        return@coroutineScope
                    }
                    if (failure.reason == CompanionSessionFailure.UNSUPPORTED) {
                        watchdog.cancelAndJoin()
                        mutableState.update { observed -> observed?.takeUnless { it.runId == runId } }
                        fallback.execute(current, applied, sink)
                        return@coroutineScope
                    }
                } catch (_: Exception) { /* Retry preserves the existing policy only until its independent safety lease. */ }
                finally { withContext(NonCancellable) {
                    policyLock.withLock { if (currentConnection === connection) currentConnection = null }
                    connection?.let { releaseConnection(it) }
                } }
                if (isActive) {
                    report(CompanionSessionPhase.RECONNECTING)
                    val backoff = minOf(5000L, 500L shl minOf(attempt++, 3))
                    delay(backoff + Random.nextLong(0L, 251L))
                }
            }
        } finally { withContext(NonCancellable) {
            watchdog.cancelAndJoin()
            releasePolicy(sink)
            if (!rejected) mutableState.update { observed -> observed?.takeUnless { it.runId == runId } }
        } }
    }

    private class SessionStopped(val rejected: Boolean = false) : Exception()

    private suspend fun releaseConnection(connection: CompanionSessionConnection) = withContext(NonCancellable) {
        runCatching { withTimeoutOrNull(1_000L) { connection.close() } }
        Unit
    }

    private suspend fun releasePolicy(sink: CompanionPacketPolicySink) = withContext(NonCancellable) {
        runCatching { withTimeoutOrNull(2_000L) { sink.apply(PacketConditionConfiguration.Disabled) } }
        Unit
    }

    private fun sameIdentity(original: CompanionRegistration, active: CompanionRegistration?): Boolean = active != null &&
        original.desktopId == active.desktopId && original.deviceId == active.deviceId &&
        original.rootCertificateSha256 == active.rootCertificateSha256 && original.transportIdentitySha256 == active.transportIdentitySha256
}
