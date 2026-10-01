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
}
