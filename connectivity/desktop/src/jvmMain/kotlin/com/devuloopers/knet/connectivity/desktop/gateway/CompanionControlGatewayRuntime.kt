package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Process-owned retry policy that keeps the companion control plane available independently of the proxy. */
public class CompanionControlGatewayRuntime(
    private val gateway: CompanionControlGatewayLifecycle,
    private val retryIntervalMillis: Long = DEFAULT_RETRY_INTERVAL_MILLIS,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private var lifecycleJob: Job? = null

    public val state: StateFlow<CompanionControlGatewayState> = gateway.state

    init {
        require(retryIntervalMillis in 100L..60_000L) { "Companion gateway retry interval is invalid." }
    }

    /** Starts one retrying lifecycle loop; repeated calls are idempotent. */
    public fun start() {
        check(!closed.get()) { "Companion control gateway runtime is closed." }
        if (lifecycleJob != null) return
        lifecycleJob = scope.launch {
            while (isActive) {
                if (gateway.state.value !is CompanionControlGatewayState.Listening &&
                    gateway.state.value !is CompanionControlGatewayState.Starting
                ) {
                    runCatching(gateway::start).onFailure { failure ->
                        val reason = (gateway.state.value as? CompanionControlGatewayState.Failed)?.reason
                            ?: CompanionControlGatewayFailure.BIND_FAILED
                        KNetLogger.warn(DISCOVERY_TAG) {
                            "companion_event=control_gateway_retry reason=$reason " +
                                "failure=${failure::class.simpleName ?: "unknown"} retry_ms=$retryIntervalMillis"
                        }
                    }
                }
                delay(retryIntervalMillis)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        lifecycleJob?.cancel()
        lifecycleJob = null
        gateway.close()
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_RETRY_INTERVAL_MILLIS: Long = 1_000L
        const val DISCOVERY_TAG: String = "CompanionDiscovery"
    }
}
