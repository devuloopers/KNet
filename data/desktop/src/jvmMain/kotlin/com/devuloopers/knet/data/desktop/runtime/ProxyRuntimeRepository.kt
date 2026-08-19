package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointGate
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequirements
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.interceptor.KNetInterceptorHandler
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Desktop runtime coordinator managing Netty proxy server lifecycle.
 */
class ProxyRuntimeRepository(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache,
    private val keyManagerProvider: KeyManagerProvider? = null,
    private val breakpointGate: BreakpointGate = BreakpointCoordinator(),
    private val ingressAttribution: IngressAttributionLookup? = null,
) {
    private val lifecycleLock = Any()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observedBreakpointRequirements = breakpointGate.requirements.value.toPipelineRequirements()
    private var proxyServer: KNetProxyServer? = null
    private var closed = false

    init {
        // A downstream Netty channel selects either the streaming or full-message pipeline exactly
        // once when accepted. Close existing child channels whenever that selection changes so the
        // client reconnects through a pipeline built from the current breakpoint requirements.
        breakpointGate.requirements
            .map { requirements -> requirements.toPipelineRequirements() }
            .distinctUntilChanged()
            .onEach(::applyBreakpointRequirements)
            .launchIn(runtimeScope)
    }

    /**
     * Starts the Netty MITM proxy server using an explicit listener and upstream trust policy.
     *
     * @param port TCP port to bind.
     * @param host Explicit listener host; loopback is the safe default.
     * @param verifyUpstreamTls Whether upstream certificates must be verified.
     * @param runtimePolicy Concrete timeout and connection limits enforced by Netty.
     * @param captureSink Canonical non-blocking streaming capture side output.
     * @throws Exception When listener startup fails after the server rolls back allocated resources.
     */
    fun startProxy(
        port: Int = 8080,
        host: String = KNetProxyServer.DEFAULT_BIND_HOST,
        verifyUpstreamTls: Boolean = true,
        runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
        captureSink: ProxyCaptureSink,
    ) {
        synchronized(lifecycleLock) {
            check(!closed) { "Proxy runtime repository is closed." }
            if (proxyServer != null) {
                KNetLogger.warn(tag = LogTags.PROXY) { "Proxy server is already running." }
                return
            }
            KNetLogger.info(tag = LogTags.PROXY) { "Starting Netty proxy server on port $port..." }

            val pipelineInitializers = listOf<(io.netty.channel.ChannelPipeline) -> Unit>({ pipeline ->
                pipeline.addLast("knetInterceptorHandler", KNetInterceptorHandler(breakpointGate))
            })
            val server = KNetProxyServer(
                bindHost = host,
                port = port,
                ca = certificateAuthority,
                certCache = certificateCache,
                keyManagerProvider = keyManagerProvider,
                verifyUpstreamTls = verifyUpstreamTls,
                runtimePolicy = runtimePolicy,
                pipelineInitializers = pipelineInitializers,
                captureSink = captureSink,
                ingressAttribution = ingressAttribution,
                requiresFullResponseAggregation = {
                    breakpointGate.requirements.value.hasResponseRules
                },
                requiresFullRequestAggregation = {
                    breakpointGate.requirements.value.hasRequestRules ||
                        breakpointGate.requirements.value.hasResponseRules
                },
            )
            server.start()
            proxyServer = server
        }
    }

    /**
     * Stops the running proxy server.
     */
    fun stopProxy() {
        val server = synchronized(lifecycleLock) {
            proxyServer.also { proxyServer = null }
        }
        server?.let {
            KNetLogger.info(tag = LogTags.PROXY) { "Stopping Netty proxy server..." }
            it.stop()
        }
    }

    /**
     * Returns whether the proxy server is currently active.
     */
    fun isRunning(): Boolean = synchronized(lifecycleLock) { proxyServer != null }

    /**
     * Permanently closes the runtime observer and any active proxy server.
     *
     * A stopped repository may be started again, while a closed repository is process-terminal and
     * no longer observes breakpoint pipeline requirements.
     */
    fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) return
        runtimeScope.cancel()
        stopProxy()
    }

    /** Applies one distinct breakpoint pipeline selection to current and future client channels. */
    private fun applyBreakpointRequirements(requirements: BreakpointPipelineRequirements) {
        val server = synchronized(lifecycleLock) {
            if (requirements == observedBreakpointRequirements) return
            observedBreakpointRequirements = requirements
            proxyServer
        } ?: return

        KNetLogger.info(tag = LogTags.PROXY) {
            "Breakpoint pipeline requirements changed; refreshing active client connections " +
                "(requestAggregation=${requirements.aggregateRequests}, " +
                "responseAggregation=${requirements.aggregateResponses})."
        }
        server.closeActiveConnections()
    }

    /** Immutable selection that controls the per-connection HTTP pipeline shape. */
    private data class BreakpointPipelineRequirements(
        val aggregateRequests: Boolean,
        val aggregateResponses: Boolean,
    )

    /** Reduces application breakpoint requirements to the fields that alter Netty pipeline shape. */
    private fun BreakpointRequirements.toPipelineRequirements() =
        BreakpointPipelineRequirements(
            aggregateRequests = hasRequestRules || hasResponseRules,
            aggregateResponses = hasResponseRules,
        )
}
