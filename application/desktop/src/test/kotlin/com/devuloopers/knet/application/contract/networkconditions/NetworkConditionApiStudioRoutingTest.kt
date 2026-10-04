package com.devuloopers.knet.application.contract.networkconditions

import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NetworkConditionApiStudioRoutingTest {
    @Test
    fun `capture or conditions use the running local proxy while an inactive workspace stays direct`() {
        val running = running(port = 8_080)

        assertNull(
            resolveApiStudioProxyPort(
                CaptureSessionState.Paused,
                NetworkConditionConfiguration(enabled = false),
                running,
            ),
        )
        assertEquals(
            8_080,
            resolveApiStudioProxyPort(
                CaptureSessionState.Capturing(CaptureSessionId("capture")),
                NetworkConditionConfiguration(enabled = false),
                running,
            ),
        )
        assertEquals(
            8_080,
            resolveApiStudioProxyPort(
                CaptureSessionState.Paused,
                NetworkConditionConfiguration(enabled = true),
                running,
            ),
        )
    }

    @Test
    fun `enabled conditions fail closed for every unavailable proxy lifecycle state`() {
        listOf(
            ProxyRuntimeState.Stopped,
            ProxyRuntimeState.Starting,
            ProxyRuntimeState.Stopping,
            ProxyRuntimeState.Failed("test", recoverable = true),
        ).forEach { proxyState ->
            val failure = assertFailsWith<IllegalStateException> {
                resolveApiStudioProxyPort(
                    CaptureSessionState.Paused,
                    NetworkConditionConfiguration(enabled = true),
                    proxyState,
                )
            }
            assertEquals(NETWORK_CONDITIONS_PROXY_UNAVAILABLE_MESSAGE, failure.message)
        }
    }

    private fun running(port: Int) = ProxyRuntimeState.Running(
        ProxyRuntimeHandle(
            runtimeId = "test-runtime",
            endpoints = ProxyEndpointSnapshot(
                version = ProxyEndpointVersion(1L),
                endpoints = listOf(
                    ProxyEndpoint(
                        host = "127.0.0.1",
                        port = port,
                        scope = ProxyEndpointScope.LOOPBACK,
                        accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
                    ),
                ),
            ),
        ),
    )
}
