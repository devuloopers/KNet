package com.devuloopers.knet.connectivity.desktop.gateway

import java.net.BindException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionControlGatewayRuntimeTest {
    @Test
    fun transientBindFailureIsRetriedUntilListenerIsAvailable() = runTest {
        val gateway = FakeGateway(failuresBeforeListening = 1)
        val runtime = CompanionControlGatewayRuntime(
            gateway = gateway,
            retryIntervalMillis = 100L,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        runtime.start()
        runCurrent()

        assertEquals(1, gateway.startCalls)
        assertIs<CompanionControlGatewayState.Failed>(runtime.state.value)

        advanceTimeBy(100L)
        runCurrent()

        assertEquals(2, gateway.startCalls)
        assertEquals(CompanionControlGatewayState.Listening(8183), runtime.state.value)

        advanceTimeBy(500L)
        runCurrent()
        assertEquals(2, gateway.startCalls)

        runtime.close()
        assertEquals(CompanionControlGatewayState.Stopped, gateway.state.value)
    }

    private class FakeGateway(
        private var failuresBeforeListening: Int,
    ) : CompanionControlGatewayLifecycle {
        private val mutableState = MutableStateFlow<CompanionControlGatewayState>(CompanionControlGatewayState.Stopped)
        override val state: StateFlow<CompanionControlGatewayState> = mutableState
        var startCalls: Int = 0

        override fun start() {
            startCalls += 1
            if (failuresBeforeListening > 0) {
                failuresBeforeListening -= 1
                mutableState.value = CompanionControlGatewayState.Failed(CompanionControlGatewayFailure.BIND_FAILED)
                throw BindException("Port is temporarily occupied")
            }
            mutableState.value = CompanionControlGatewayState.Listening(8183)
        }

        override fun close() {
            mutableState.value = CompanionControlGatewayState.Stopped
        }
    }
}
