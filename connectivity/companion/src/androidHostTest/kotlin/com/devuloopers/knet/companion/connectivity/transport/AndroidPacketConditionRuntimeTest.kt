package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidPacketConditionRuntimeTest {
    @Test
    fun `parallel UDP flows share one aggregate 100 kbps download budget`() {
        val runtime = AndroidPacketConditionRuntime(
            configuration = PacketConditionConfiguration(
                enabled = true,
                downloadBitsPerSecond = 100_000L,
            ),
            nanoTime = { 0L },
        )

        val first = assertIs<PacketConditionAction.Forward>(
            runtime.plan(PacketConditionDirection.DOWNLOAD, "video-a:443", 1_250, 1L),
        )
        val second = assertIs<PacketConditionAction.Forward>(
            runtime.plan(PacketConditionDirection.DOWNLOAD, "video-b:443", 1_250, 2L),
        )

        assertEquals(100L, first.delayMillis)
        assertEquals(200L, second.delayMillis)
    }

    @Test
    fun `upload and download budgets remain independent`() {
        val runtime = AndroidPacketConditionRuntime(
            PacketConditionConfiguration(
                enabled = true,
                uploadBitsPerSecond = null,
                downloadBitsPerSecond = 100_000L,
            ),
            nanoTime = { 0L },
        )

        val upload = assertIs<PacketConditionAction.Forward>(
            runtime.plan(PacketConditionDirection.UPLOAD, "call:3478", 1_250, 1L),
        )
        val download = assertIs<PacketConditionAction.Forward>(
            runtime.plan(PacketConditionDirection.DOWNLOAD, "call:3478", 1_250, 2L),
        )

        assertEquals(0L, upload.delayMillis)
        assertEquals(100L, download.delayMillis)
    }

    @Test
    fun `seeded packet actions cover loss duplication and bounded reordering`() {
        val dropped = AndroidPacketConditionRuntime(
            PacketConditionConfiguration(enabled = true, lossPercent = 100),
        ).plan(PacketConditionDirection.UPLOAD, "flow", 100, 1L)
        assertIs<PacketConditionAction.Drop>(dropped)

        val duplicatedAndReordered = assertIs<PacketConditionAction.Forward>(
            AndroidPacketConditionRuntime(
                PacketConditionConfiguration(
                    enabled = true,
                    duplicationPercent = 100,
                    reorderingPercent = 100,
                ),
            ).plan(PacketConditionDirection.DOWNLOAD, "flow", 100, 1L),
        )
        assertEquals(2, duplicatedAndReordered.copies)
        assertTrue(duplicatedAndReordered.delayMillis >= 10L)
    }

    @Test
    fun `duplicated datagrams consume bandwidth for both transmitted copies`() {
        val runtime = AndroidPacketConditionRuntime(
            PacketConditionConfiguration(
                enabled = true,
                downloadBitsPerSecond = 100_000L,
                duplicationPercent = 100,
            ),
            nanoTime = { 0L },
        )

        val action = assertIs<PacketConditionAction.Forward>(
            runtime.plan(PacketConditionDirection.DOWNLOAD, "video:443", 1_250, 1L),
        )

        assertEquals(2, action.copies)
        assertEquals(200L, action.delayMillis)
    }

    @Test
    fun `datagram queue is bounded and released safely`() {
        val runtime = AndroidPacketConditionRuntime(
            PacketConditionConfiguration(enabled = true, maximumQueuedDatagrams = 2),
        )

        assertTrue(runtime.tryEnqueue())
        assertTrue(runtime.tryEnqueue())
        assertFalse(runtime.tryEnqueue())
        runtime.complete()
        assertTrue(runtime.tryEnqueue())
    }

    @Test
    fun `latency and signed jitter are deterministic and bounded`() {
        val configuration = PacketConditionConfiguration(
            enabled = true,
            latencyMillis = 100L,
            jitterMillis = 20L,
            seed = 42L,
        )
        fun delays() = AndroidPacketConditionRuntime(configuration).let { runtime ->
            (1L..100L).map { sequence ->
                assertIs<PacketConditionAction.Forward>(
                    runtime.plan(PacketConditionDirection.DOWNLOAD, "call:3478", 100, sequence),
                ).delayMillis
            }
        }

        val first = delays()
        assertEquals(first, delays())
        assertTrue(first.all { it in 80L..120L })
        assertTrue(first.distinct().size > 1)
    }

    @Test
    fun `intermediate packet percentages are reproducible without degenerating to always or never`() {
        val configuration = PacketConditionConfiguration(
            enabled = true,
            lossPercent = 37,
            duplicationPercent = 23,
            reorderingPercent = 19,
            seed = 7L,
        )
        fun actions() = AndroidPacketConditionRuntime(configuration).let { runtime ->
            (1L..1_000L).map { sequence ->
                runtime.plan(PacketConditionDirection.DOWNLOAD, "flow", 100, sequence)
            }
        }

        val first = actions()
        val second = actions()
        assertEquals(first, second)
        assertTrue(first.count { it is PacketConditionAction.Drop } in 320..420)
        val forwarded = first.filterIsInstance<PacketConditionAction.Forward>()
        assertTrue(forwarded.count { it.copies == 2 } in 100..200)
        assertTrue(forwarded.any { it.delayMillis >= 10L })
    }

    @Test
    fun `disabled runtime is an immediate pass-through and queue completion cannot underflow`() {
        val runtime = AndroidPacketConditionRuntime(PacketConditionConfiguration.Disabled)

        runtime.complete()
        assertTrue(runtime.tryEnqueue())
        assertEquals(
            PacketConditionAction.Forward(delayMillis = 0L, copies = 1),
            runtime.plan(PacketConditionDirection.UPLOAD, "flow", 1_250, 1L),
        )
    }

    @Test
    fun `idle time does not accumulate UDP burst credit`() {
        var now = 0L
        val runtime = AndroidPacketConditionRuntime(
            PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000L),
            nanoTime = { now },
        )
        assertEquals(
            100L,
            assertIs<PacketConditionAction.Forward>(
                runtime.plan(PacketConditionDirection.DOWNLOAD, "flow", 1_250, 1L),
            ).delayMillis,
        )

        now = 10_000_000_000L
        assertEquals(
            100L,
            assertIs<PacketConditionAction.Forward>(
                runtime.plan(PacketConditionDirection.DOWNLOAD, "flow", 1_250, 2L),
            ).delayMillis,
        )
    }
}
