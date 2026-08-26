package com.devuloopers.knet.application.usecase.proxy

import com.devuloopers.knet.application.contract.proxy.ProxyBindingConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyConnectionLimits
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.proxy.ProxyStartResult
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.proxy.ProxyStopResult
import com.devuloopers.knet.application.contract.proxy.ProxyTimeoutPolicy
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts the desktop proxy with a safe loopback-only listener and strict upstream TLS validation.
 *
 * This use case intentionally exposes only loopback startup. Future LAN or internal-gateway use
 * cases must supply an authenticated access policy instead of weakening this default.
 */
public class StartLoopbackProxyUseCase(
    private val runtime: ProxyRuntime,
) {
    public companion object {
        /** Default proxy port used by the desktop capture control. */
        public const val DEFAULT_PORT: Int = 8080

        /** IPv4 loopback address used by the desktop Netty runtime. */
        public const val LOOPBACK_HOST: String = "127.0.0.1"
    }

    /**
     * Starts or idempotently returns the active loopback proxy.
     *
     * @param port Valid TCP listener port.
     * @return Typed runtime startup result after complete bind or rollback.
     */
    public suspend fun execute(port: Int = DEFAULT_PORT): ProxyStartResult {
        val configuration = ProxyRuntimeConfiguration(
            bindings = listOf(
                ProxyBindingConfiguration(
                    host = LOOPBACK_HOST,
                    port = port,
                    scope = ProxyEndpointScope.LOOPBACK,
                )
            ),
            verifyUpstreamTls = true,
            timeouts = ProxyTimeoutPolicy(
                connectMillis = 10_000L,
                tlsHandshakeMillis = 10_000L,
                readIdleMillis = 60_000L,
                writeIdleMillis = 60_000L,
                gracefulShutdownMillis = 5_000L,
            ),
            connectionLimits = ProxyConnectionLimits(
                maximumDownstreamConnections = 1_024,
                maximumConnectionsPerClient = 128,
                maximumUpstreamConnections = 1_024,
            ),
        )
        return runtime.start(configuration)
    }
}

/** Stops the application-owned proxy runtime and awaits its resource cleanup. */
public class StopProxyRuntimeUseCase(
    private val runtime: ProxyRuntime,
) {
    /**
     * Stops the runtime for [reason].
     *
     * @param reason Typed reason used by shutdown and audit policy.
     * @return Final stopped or forced-close result.
     */
    public suspend fun execute(
        reason: ProxyStopReason = ProxyStopReason.USER_REQUEST,
    ): ProxyStopResult = runtime.stop(reason)
}

/** Exposes the serialized application proxy lifecycle state to presentation consumers. */
public class ObserveProxyRuntimeStateUseCase(
    private val runtime: ProxyRuntime,
) {
    /**
     * Returns the hot lifecycle state owned by the runtime adapter.
     *
     * @return Stable state stream whose current value is available synchronously.
     */
    public fun execute(): StateFlow<ProxyRuntimeState> = runtime.state
}
