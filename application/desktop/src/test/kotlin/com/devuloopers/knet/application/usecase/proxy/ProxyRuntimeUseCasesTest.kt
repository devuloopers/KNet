package com.devuloopers.knet.application.usecase.proxy

import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.proxy.ProxyStartResult
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.proxy.ProxyStopResult
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests application-owned safe proxy startup and shutdown policy. */
class ProxyRuntimeUseCasesTest {

    /** Verifies desktop capture requests only loopback exposure with strict upstream TLS. */
    @Test
    fun `loopback start use case supplies safe runtime configuration`() = runTest {
        val runtime = RecordingProxyRuntime()

        StartLoopbackProxyUseCase(runtime).execute(port = 9090)

        val configuration = requireNotNull(runtime.startedConfiguration)
        assertEquals(1, configuration.bindings.size)
        assertEquals(StartLoopbackProxyUseCase.LOOPBACK_HOST, configuration.bindings.single().host)
        assertEquals(9090, configuration.bindings.single().port)
        assertEquals(ProxyEndpointScope.LOOPBACK, configuration.bindings.single().scope)
        assertTrue(configuration.verifyUpstreamTls)
    }

    /** Verifies shutdown reasons cross the application contract without string conversion. */
    @Test
    fun `stop use case preserves typed shutdown reason`() = runTest {
        val runtime = RecordingProxyRuntime()

        StopProxyRuntimeUseCase(runtime).execute(ProxyStopReason.APPLICATION_SHUTDOWN)

        assertEquals(ProxyStopReason.APPLICATION_SHUTDOWN, runtime.stopReason)
    }

    /** Minimal runtime test adapter recording application commands. */
    private class RecordingProxyRuntime : ProxyRuntime {
        private val mutableState = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
        override val state: StateFlow<ProxyRuntimeState> = mutableState
        var startedConfiguration: ProxyRuntimeConfiguration? = null
        var stopReason: ProxyStopReason? = null

        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
            startedConfiguration = configuration
            val handle = ProxyRuntimeHandle(
                runtimeId = "test-runtime",
                endpoints = ProxyEndpointSnapshot(ProxyEndpointVersion(1L), emptyList()),
            )
            mutableState.value = ProxyRuntimeState.Running(handle)
            return ProxyStartResult.Running(handle)
        }

        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            stopReason = reason
            mutableState.value = ProxyRuntimeState.Stopped
            return ProxyStopResult.Stopped
        }
    }
}
