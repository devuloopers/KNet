package com.devuloopers.knet.data.desktop.proxy.repository

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficRecordPort
import com.devuloopers.knet.application.port.traffic.TrafficRecordReceipt
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.capture.StreamingProxyCaptureSession
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

import com.devuloopers.knet.core.logger.LogTags
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Desktop adapter for application-owned proxy lifecycle and canonical traffic capture.
 *
 * [ProxyRuntimePort] is the stable control boundary and every accepted exchange is persisted only
 * through the canonical session writer.
 *
 * @property proxyRuntimeRepository Existing Netty lifecycle adapter retained behind the application port.
 * @property canonicalCaptureSessionFactory Factory for the sole canonical persistence authority.
 */
@OptIn(ExperimentalUuidApi::class)
class DesktopProxyRuntimeAdapter(
    private val proxyRuntimeRepository: ProxyRuntimeRepository,
    private val canonicalCaptureSessionFactory: CanonicalCaptureSessionFactory,
) : ProxyRuntimePort, CaptureSessionControlPort, TrafficRecordPort {

    private val lifecycleMutex = Mutex()
    private val endpointVersion = AtomicLong(0L)
    private val _runtimeState = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
    override val state: StateFlow<ProxyRuntimeState> = _runtimeState.asStateFlow()
    private val closed = AtomicBoolean(false)
    private val canonicalCallbackLock = Any()
    @Volatile
    private var canonicalCaptureSession: StreamingProxyCaptureSession? = null
    @Volatile
    private var directCaptureSession: StreamingProxyCaptureSession? = null

    /**
     * Starts the Netty implementation through the application runtime contract.
     *
     * Only one loopback binding is accepted until authenticated LAN and internal gateway access
     * enforcement exists. Unsupported exposure requests fail before a listener is created.
     */
    override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult = lifecycleMutex.withLock {
        val currentState = _runtimeState.value
        if (currentState is ProxyRuntimeState.Running) {
            return@withLock ProxyStartResult.Running(currentState.handle)
        }

        val binding = configuration.bindings.singleOrNull()
            ?: return@withLock failStart(START_FAILURE_UNSUPPORTED_BINDINGS)
        if (binding.scope != ProxyEndpointScope.LOOPBACK || !isSupportedLoopbackHost(binding.host)) {
            return@withLock failStart(START_FAILURE_UNAUTHENTICATED_EXPOSURE)
        }

        _runtimeState.value = ProxyRuntimeState.Starting
        try {
            closeDirectCaptureSession()
            val session = canonicalCaptureSessionFactory.openStreamingProxy(binding.port)
            synchronized(canonicalCallbackLock) {
                canonicalCaptureSession = session
            }
            withContext(Dispatchers.IO) {
                proxyRuntimeRepository.startProxy(
                    port = binding.port,
                    host = binding.host,
                    verifyUpstreamTls = configuration.verifyUpstreamTls,
                    runtimePolicy = KNetProxyRuntimePolicy(
                        connectTimeoutMillis = configuration.timeouts.connectMillis,
                        tlsHandshakeTimeoutMillis = configuration.timeouts.tlsHandshakeMillis,
                        readIdleTimeoutMillis = configuration.timeouts.readIdleMillis,
                        writeIdleTimeoutMillis = configuration.timeouts.writeIdleMillis,
                        gracefulShutdownTimeoutMillis = configuration.timeouts.gracefulShutdownMillis,
                        maximumDownstreamConnections =
                            configuration.connectionLimits.maximumDownstreamConnections,
                        maximumConnectionsPerClient =
                            configuration.connectionLimits.maximumConnectionsPerClient,
                        maximumUpstreamConnections =
                            configuration.connectionLimits.maximumUpstreamConnections,
                    ),
                    captureSink = session,
                )
            }

            val handle = ProxyRuntimeHandle(
                runtimeId = Uuid.random().toString(),
                endpoints = ProxyEndpointSnapshot(
                    version = ProxyEndpointVersion(endpointVersion.incrementAndGet()),
                    endpoints = listOf(
                        ProxyEndpoint(
                            host = binding.host,
                            port = binding.port,
                            scope = binding.scope,
                            accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
                        )
                    ),
                ),
            )
            _runtimeState.value = ProxyRuntimeState.Running(handle)
            KNetLogger.info(tag = LogTags.PROXY) {
                "Proxy engine started on ${binding.host}:${binding.port} with strict upstream TLS=${configuration.verifyUpstreamTls}."
            }
            ProxyStartResult.Running(handle)
        } catch (failure: Exception) {
            withContext(Dispatchers.IO) {
                proxyRuntimeRepository.stopProxy()
            }
            runCatching { closeCanonicalCaptureSession() }
                .onFailure { closeFailure ->
                    KNetLogger.error(tag = LogTags.PROXY, throwable = closeFailure) {
                        "Failed to close canonical capture after proxy startup rollback."
                    }
                }
            KNetLogger.error(tag = LogTags.PROXY, throwable = failure) {
                "Failed to start proxy engine on ${binding.host}:${binding.port}."
            }
            failStart(START_FAILURE_RUNTIME)
        }
    }

    /**
     * Records one complete application-authored exchange through the active canonical authority.
     *
     * Direct calls reuse a running proxy session when one exists. Otherwise one persistent direct
     * session is opened and reused until proxy start, clear, or application shutdown. This preserves
     * one active capture authority.
     */
    override suspend fun record(command: RecordHttpExchangeCommand): TrafficRecordReceipt = lifecycleMutex.withLock {
        check(!closed.get()) { "Traffic recording is unavailable after repository shutdown." }
        val proxySession = synchronized(canonicalCallbackLock) { canonicalCaptureSession }
        if (proxySession != null) {
            proxySession.recordCanonical(command)
            proxySession.flush()
            return@withLock TrafficRecordReceipt(
                sessionId = proxySession.sessionId,
                exchangeId = command.exchangeId,
            )
        }
        val directSession = directCaptureSession
            ?: canonicalCaptureSessionFactory.openDirect(command.startedAtEpochMillis)
                .also { opened -> directCaptureSession = opened }
        directSession.recordCanonical(command)
        directSession.flush()
        TrafficRecordReceipt(sessionId = directSession.sessionId, exchangeId = command.exchangeId)
    }


    /** Stops the Netty runtime and awaits active channel and event-loop closure. */
    override suspend fun stop(reason: ProxyStopReason): ProxyStopResult = lifecycleMutex.withLock {
        if (_runtimeState.value is ProxyRuntimeState.Stopped) {
            closeCanonicalCaptureSession()
            return@withLock ProxyStopResult.Stopped
        }

        _runtimeState.value = ProxyRuntimeState.Stopping
        try {
            withContext(Dispatchers.IO) {
                proxyRuntimeRepository.stopProxy()
            }
            closeCanonicalCaptureSession()
            _runtimeState.value = ProxyRuntimeState.Stopped
            KNetLogger.info(tag = LogTags.PROXY) { "Proxy engine stopped for reason ${reason.name}." }
            ProxyStopResult.Stopped
        } catch (failure: Exception) {
            KNetLogger.error(tag = LogTags.PROXY, throwable = failure) { "Error stopping proxy engine." }
            _runtimeState.value = ProxyRuntimeState.Failed(
                code = STOP_FAILURE_RUNTIME,
                recoverable = true,
            )
            ProxyStopResult.Forced(listOf(STOP_FAILURE_RUNTIME))
        }
    }

    /**
     * Replaces the active canonical writer before terminal traffic is removed.
     *
     * Existing client channels are closed after the callback target swaps so retries enter the new
     * session. The old adapter terminalizes any unfinished exchanges before becoming clearable.
     */
    override suspend fun rotateForTrafficClear(): CaptureClearPreparation = lifecycleMutex.withLock {
        val running = _runtimeState.value as? ProxyRuntimeState.Running
        if (running == null) {
            closeCanonicalCaptureSession()
            closeDirectCaptureSession()
            return@withLock CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
        }
        closeDirectCaptureSession()
        val listenerPort = running.handle.endpoints.endpoints.singleOrNull()?.port
            ?: error("Canonical capture rotation requires one active listener endpoint.")
        val replacement = canonicalCaptureSessionFactory.openStreamingProxy(listenerPort)
        val previous = synchronized(canonicalCallbackLock) {
            val current = canonicalCaptureSession
            canonicalCaptureSession = replacement
            current
        }
        try {
            withContext(Dispatchers.IO) {
                proxyRuntimeRepository.flushActiveChannels()
            }
        } finally {
            previous?.close()
        }
        CaptureClearPreparation.CANONICAL_SESSION_ROTATED
    }

    /** Publishes a typed failed state for a rejected or rolled-back startup. */
    private fun failStart(code: String): ProxyStartResult.Failed {
        _runtimeState.value = ProxyRuntimeState.Failed(code = code, recoverable = true)
        return ProxyStartResult.Failed(code)
    }

    /** Returns whether the runtime can safely bind the supplied loopback token. */
    private fun isSupportedLoopbackHost(host: String): Boolean {
        return host == "127.0.0.1" || host == "::1" || host.equals("localhost", ignoreCase = true)
    }

    /**
     * Permanently closes process-scoped runtime and canonical capture resources.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        proxyRuntimeRepository.stopProxy()
        val canonicalSession = synchronized(canonicalCallbackLock) {
            val session = canonicalCaptureSession
            canonicalCaptureSession = null
            session
        }
        canonicalSession?.let { session ->
            if (!session.closeAndAwait(CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS)) {
                KNetLogger.error(tag = LogTags.PROXY) {
                    "Timed out while closing the canonical capture writer during application shutdown."
                }
            }
        }
        directCaptureSession?.also { directCaptureSession = null }?.let { session ->
            if (!session.closeAndAwait(CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS)) {
                KNetLogger.error(tag = LogTags.PROXY) {
                    "Timed out while closing the direct canonical writer during application shutdown."
                }
            }
        }
        _runtimeState.value = ProxyRuntimeState.Stopped
    }

    /** Closes and forgets the canonical session after proxy callbacks have stopped. */
    private suspend fun closeCanonicalCaptureSession() {
        val session = synchronized(canonicalCallbackLock) {
            val current = canonicalCaptureSession
            canonicalCaptureSession = null
            current
        } ?: return
        session.close()
    }

    /** Closes the direct producer session before another owner becomes active. */
    private suspend fun closeDirectCaptureSession() {
        val session = directCaptureSession ?: return
        directCaptureSession = null
        session.close()
    }

    private companion object {
        private const val CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS = 5_000L
        private const val START_FAILURE_UNSUPPORTED_BINDINGS = "proxy-start-unsupported-bindings"
        private const val START_FAILURE_UNAUTHENTICATED_EXPOSURE = "proxy-start-unauthenticated-exposure"
        private const val START_FAILURE_RUNTIME = "proxy-start-runtime-failed"
        private const val STOP_FAILURE_RUNTIME = "proxy-stop-runtime-failed"
    }
}
