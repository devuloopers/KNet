package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Checks policy changes against running timers and actual final-send generation fences. */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePacketPolicyTest {
    @Test fun `disable releases a previously delayed datagram immediately`() = runTest {
        val runtime = AndroidPacketConditionRuntime(PacketConditionConfiguration(enabled = true, latencyMillis = 60_000), nanoTime = { testScheduler.currentTime * 1_000_000 })
        val result = async { runtime.schedule(PacketConditionDirection.DOWNLOAD, "flow", 1_250, 1) }
        runCurrent()
        assertFalse(result.isCompleted)
        runtime.update(PacketConditionConfiguration.Disabled)
        runCurrent()
        assertIs<PacketConditionAction.Forward>(result.await().action)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `offline cancels a pending delay and produces a drop rather than retaining bytes`() = runTest {
        val runtime = AndroidPacketConditionRuntime(PacketConditionConfiguration(enabled = true, latencyMillis = 60_000))
        val result = async { runtime.schedule(PacketConditionDirection.UPLOAD, "flow", 20, 1) }
        runCurrent()
        runtime.update(PacketConditionConfiguration(enabled = true, lossPercent = 100))
        runCurrent()
        assertIs<PacketConditionAction.Drop>(result.await().action)
    }

    @Test fun `rate edits replace pending deadlines and reject sends from superseded reservations`() = runTest {
        val slow = PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 10_000)
        val runtime = AndroidPacketConditionRuntime(slow, nanoTime = { testScheduler.currentTime * 1_000_000 })
        val result = async { runtime.schedule(PacketConditionDirection.DOWNLOAD, "flow", 1_250, 1) }
        runCurrent()
        runtime.update(slow.copy(downloadBitsPerSecond = 100_000))
        runCurrent(); advanceTimeBy(100); runCurrent()
        val reservation = result.await()
        var sends = 0
        runtime.update(slow)
        assertFalse(runtime.ifCurrent(reservation.revision) { sends++ })
        assertEquals(0, sends)
    }

    @Test fun `cancelling an association does not leave another owner's deadline behind abandoned reservations`() = runTest {
        val runtime = AndroidPacketConditionRuntime(PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000), nanoTime = { testScheduler.currentTime * 1_000_000 })
        val removed = async { runtime.schedule(PacketConditionDirection.DOWNLOAD, "removed", 1_250, 1) }
        val retained = async { runtime.schedule(PacketConditionDirection.DOWNLOAD, "retained", 1_250, 2) }
        runCurrent()
        removed.cancelAndJoin()
        runtime.replanAfterCancellation()
        runCurrent(); advanceTimeBy(100); runCurrent()
        assertTrue(retained.isCompleted)
        assertTrue(runtime.ifCurrent(retained.await().revision) {})
    }

    @Test fun `DNS has separate bounded admission even when shaped datagrams fill their queue`() {
        val runtime = AndroidPacketConditionRuntime(PacketConditionConfiguration(enabled = true, maximumQueuedDatagrams = 1))
        assertTrue(runtime.tryEnqueue())
        assertFalse(runtime.tryEnqueue())
        repeat(64) { assertTrue(runtime.tryEnqueue(controlTraffic = true)) }
        assertFalse(runtime.tryEnqueue(controlTraffic = true))
        runtime.complete()
        repeat(64) { runtime.complete(controlTraffic = true) }
        assertEquals(0, runtime.queuedCount)
    }
}
