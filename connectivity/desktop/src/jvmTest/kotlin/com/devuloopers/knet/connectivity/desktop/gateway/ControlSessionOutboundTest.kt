package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.companion.model.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Deterministic backpressure qualification; blocked peers cannot keep a policy producer alive indefinitely. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ControlSessionOutboundTest {
    @Test fun `a blocked queue fails the owner at the write deadline and does not deliver a late frame`() = runTest {
        val queue = Channel<Frame>(1)
        queue.send(Frame.Text("occupied"))
        val result = async { runCatching { sendCompanionControlMessage(queue, welcome) }.exceptionOrNull() }
        advanceTimeBy(1_999); runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertIs<ControlSessionWriteTimeout>(result.await())
        assertEquals("occupied", (queue.receive() as Frame.Text).readText())
        assertTrue(queue.tryReceive().isFailure)
        queue.close()
    }
    @Test fun `an available queue carries one complete validated envelope`() = runTest {
        val queue = Channel<Frame>(1)
        sendCompanionControlMessage(queue, welcome)
        assertEquals(welcome, CompanionSessionProtocol.decode((queue.receive() as Frame.Text).readText()))
        queue.close()
    }
    private val welcome = CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, "run")
}
