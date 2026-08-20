package com.devuloopers.knet.data.desktop.proxy.repository

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.port.breakpoint.BreakpointCaptureAvailabilityPort
import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.capture.CaptureSessionRetirementOwner
import com.devuloopers.knet.data.desktop.capture.StreamingProxyCaptureSession
import com.devuloopers.knet.data.desktop.capture.SwitchableProxyCaptureSink
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
 * @property breakpointCaptureAvailability Runtime switch that prevents uncaptured exchanges from pausing.
 */
@OptIn(ExperimentalUuidApi::class)
class DesktopProxyRuntimeAdapter(
    private val proxyRuntimeRepository: ProxyRuntimeRepository,
    private val canonicalCaptureSessionFactory: CanonicalCaptureSessionFactory,
    private val breakpointCaptureAvailability: BreakpointCaptureAvailabilityPort,
) : ProxyRuntimePort, CaptureSessionControlPort {

    private val lifecycleMutex = Mutex()
    private val endpointVersion = AtomicLong(0L)
    private val _runtimeState = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
    override val state: StateFlow<ProxyRuntimeState> = _runtimeState.asStateFlow()
    private val _captureState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Inactive)
    override val captureState: StateFlow<CaptureSessionState> = _captureState.asStateFlow()
    private val closed = AtomicBoolean(false)
    private val retirementOwner = CaptureSessionRetirementOwner()
    private val canonicalCallbackLock = Any()
    @Volatile
    private var canonicalCaptureSession: StreamingProxyCaptureSession? = null
    private var switchableCaptureSink: SwitchableProxyCaptureSink? = null

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
        _captureState.value = CaptureSessionState.Starting
        try {
            val session = canonicalCaptureSessionFactory.openStreamingProxy(binding.port)
            val captureSink = SwitchableProxyCaptureSink(session)
            synchronized(canonicalCallbackLock) {
                canonicalCaptureSession = session
                switchableCaptureSink = captureSink
            }
            breakpointCaptureAvailability.setCaptureAvailable(true)
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
                    captureSink = captureSink,
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
            _captureState.value = CaptureSessionState.Capturing(session.sessionId)
            KNetLogger.info(tag = LogTags.PROXY) {
                "Proxy engine started on ${binding.host}:${binding.port} with strict upstream TLS=${configuration.verifyUpstreamTls}."
            }
            ProxyStartResult.Running(handle)
        } catch (failure: Exception) {
            breakpointCaptureAvailability.setCaptureAvailable(false)
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
            _captureState.value = CaptureSessionState.Failed(CAPTURE_FAILURE_START)
            failStart(START_FAILURE_RUNTIME)
        }
    }

    /** Stops the Netty runtime and awaits active channel and event-loop closure. */
    override suspend fun stop(reason: ProxyStopReason): ProxyStopResult = lifecycleMutex.withLock {
        if (_runtimeState.value is ProxyRuntimeState.Stopped) {
            breakpointCaptureAvailability.setCaptureAvailable(false)
            closeCanonicalCaptureSession()
            _captureState.value = CaptureSessionState.Inactive
            return@withLock ProxyStopResult.Stopped
        }

        _runtimeState.value = ProxyRuntimeState.Stopping
        breakpointCaptureAvailability.setCaptureAvailable(false)
        val canonicalSession = detachCanonicalCaptureSession()
        try {
            withContext(Dispatchers.IO) {
                proxyRuntimeRepository.stopProxy()
            }
            canonicalSession?.close()
            _runtimeState.value = ProxyRuntimeState.Stopped
            _captureState.value = CaptureSessionState.Inactive
            KNetLogger.info(tag = LogTags.PROXY) { "Proxy engine stopped for reason ${reason.name}." }
            ProxyStopResult.Stopped
        } catch (failure: Exception) {
            runCatching { canonicalSession?.close() }
                .onFailure { closeFailure ->
                    KNetLogger.error(tag = LogTags.PROXY, throwable = closeFailure) {
                        "Failed to close canonical capture after proxy shutdown failure."
                    }
                }
            KNetLogger.error(tag = LogTags.PROXY, throwable = failure) { "Error stopping proxy engine." }
            _runtimeState.value = ProxyRuntimeState.Failed(
                code = STOP_FAILURE_RUNTIME,
                recoverable = true,
            )
            _captureState.value = CaptureSessionState.Failed(CAPTURE_FAILURE_STOP)
            ProxyStopResult.Forced(listOf(STOP_FAILURE_RUNTIME))
        }
    }

    /** Detaches canonical capture while the proxy continues forwarding existing connections. */
    override suspend fun pause(): CapturePauseResult {
        var retiringSession: StreamingProxyCaptureSession? = null
        val result = lifecycleMutex.withLock {
            if (_runtimeState.value !is ProxyRuntimeState.Running) {
                _captureState.value = CaptureSessionState.Inactive
                return@withLock CapturePauseResult.PROXY_INACTIVE
            }
            breakpointCaptureAvailability.setCaptureAvailable(false)
            retiringSession = synchronized(canonicalCallbackLock) {
                val current = canonicalCaptureSession
                switchableCaptureSink?.pause()
                canonicalCaptureSession = null
                current
            }
            _captureState.value = CaptureSessionState.Paused
            if (retiringSession == null) {
                CapturePauseResult.ALREADY_PAUSED
            } else {
                CapturePauseResult.PAUSED
            }
        }
        retiringSession?.let { session -> retirementOwner.retire(session) }
        return result
    }

    /** Attaches a fresh canonical capture generation without rebinding the proxy listener. */
    override suspend fun resume(): CaptureResumeResult = lifecycleMutex.withLock {
        val running = _runtimeState.value as? ProxyRuntimeState.Running
            ?: return@withLock CaptureResumeResult.ProxyInactive.also {
                _captureState.value = CaptureSessionState.Inactive
            }
        synchronized(canonicalCallbackLock) { canonicalCaptureSession }?.let { session ->
            return@withLock CaptureResumeResult.AlreadyCapturing(session.sessionId)
        }

        _captureState.value = CaptureSessionState.Starting
        val listenerPort = running.handle.endpoints.endpoints.singleOrNull()?.port
            ?: error("Capture resume requires one active listener endpoint.")
        try {
            val replacement = canonicalCaptureSessionFactory.openStreamingProxy(listenerPort)
            synchronized(canonicalCallbackLock) {
                checkNotNull(switchableCaptureSink) { "Running proxy must own a switchable capture sink." }
                    .replaceTarget(replacement)
                canonicalCaptureSession = replacement
            }
            breakpointCaptureAvailability.setCaptureAvailable(true)
            _captureState.value = CaptureSessionState.Capturing(replacement.sessionId)
            CaptureResumeResult.Capturing(replacement.sessionId)
        } catch (failure: Exception) {
            breakpointCaptureAvailability.setCaptureAvailable(false)
            _captureState.value = CaptureSessionState.Failed(CAPTURE_FAILURE_RESUME)
            KNetLogger.error(tag = LogTags.PROXY, throwable = failure) {
                "Failed to attach a new capture generation to the running proxy."
            }
            CaptureResumeResult.Failed(CAPTURE_FAILURE_RESUME)
        }
    }

    /**
     * Replaces the active canonical writer before terminal traffic is removed.
     *
     * The stable proxy capture sink redirects subsequent exchanges to the replacement session. The
     * old adapter terminalizes unfinished capture state without closing any client transport channel.
     */
    override suspend fun rotateForTrafficClear(): CaptureClearPreparation = lifecycleMutex.withLock {
        val running = _runtimeState.value as? ProxyRuntimeState.Running
        if (running == null) {
            closeCanonicalCaptureSession()
            return@withLock CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
        }
        if (synchronized(canonicalCallbackLock) { canonicalCaptureSession } == null) {
            return@withLock CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
        }
        val listenerPort = running.handle.endpoints.endpoints.singleOrNull()?.port
            ?: error("Canonical capture rotation requires one active listener endpoint.")
        val replacement = canonicalCaptureSessionFactory.openStreamingProxy(listenerPort)
        val previous = synchronized(canonicalCallbackLock) {
            val current = canonicalCaptureSession
            checkNotNull(switchableCaptureSink) { "Running proxy must own a switchable capture sink." }
                .replaceTarget(replacement)
            canonicalCaptureSession = replacement
            current
        }
        _captureState.value = CaptureSessionState.Capturing(replacement.sessionId)
        previous?.close()
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
        proxyRuntimeRepository.close()
        val canonicalSession = synchronized(canonicalCallbackLock) {
            val session = canonicalCaptureSession
            switchableCaptureSink?.pause()
            canonicalCaptureSession = null
            switchableCaptureSink = null
            session
        }
        canonicalSession?.let { session ->
            if (!session.closeAndAwait(CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS)) {
                KNetLogger.error(tag = LogTags.PROXY) {
                    "Timed out while closing the canonical capture writer during application shutdown."
                }
            }
        }
        if (!retirementOwner.closeAndAwait(CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS)) {
            KNetLogger.error(tag = LogTags.PROXY) {
                "Timed out while draining retired canonical capture writers during application shutdown."
            }
        }
        _runtimeState.value = ProxyRuntimeState.Stopped
        _captureState.value = CaptureSessionState.Inactive
    }

    /** Closes and forgets the canonical session after proxy callbacks have stopped. */
    private suspend fun closeCanonicalCaptureSession() {
        val session = detachCanonicalCaptureSession() ?: return
        session.close()
    }

    /** Detaches the current canonical target and removes the transport-facing switch. */
    private fun detachCanonicalCaptureSession(): StreamingProxyCaptureSession? =
        synchronized(canonicalCallbackLock) {
            val current = canonicalCaptureSession
            switchableCaptureSink?.pause()
            canonicalCaptureSession = null
            switchableCaptureSink = null
            current
        }

    private companion object {
        private const val CANONICAL_WRITER_SHUTDOWN_TIMEOUT_MILLIS = 5_000L
        private const val START_FAILURE_UNSUPPORTED_BINDINGS = "proxy-start-unsupported-bindings"
        private const val START_FAILURE_UNAUTHENTICATED_EXPOSURE = "proxy-start-unauthenticated-exposure"
        private const val START_FAILURE_RUNTIME = "proxy-start-runtime-failed"
        private const val STOP_FAILURE_RUNTIME = "proxy-stop-runtime-failed"
        private const val CAPTURE_FAILURE_START = "capture-start-failed"
        private const val CAPTURE_FAILURE_RESUME = "capture-resume-failed"
        private const val CAPTURE_FAILURE_STOP = "capture-stop-failed"
    }
}
