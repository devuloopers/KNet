package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkThroughputSamplerTest {
    @Test
    fun `six thousand chunk updates fill sixty seconds without accelerating the graph or losing bytes`() {
        val sampler = NetworkThroughputSampler()
        assertNull(sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L)))
        repeat(6_000) { index ->
            val chunk = index + 1L
            val history = sampler.accept(snapshot(time = chunk * 10_000_000L,
                uploaded = chunk * 25L, downloaded = chunk * 125L))
            if (chunk % 100L == 0L) {
                val completed = requireNotNull(history)
                assertEquals((chunk / 100L).toInt(), completed.sampleCount)
                assertEquals(100_000L, completed.currentDownloadBitsPerSecond)
                assertEquals(20_000L, completed.currentUploadBitsPerSecond)
                assertTrue(completed.currentSampleIsValid)
            } else assertNull(history, "Subsecond events must not advance the chart")
        }
    }

    @Test
    fun `cumulative bytes become independent rates over actual elapsed time`() {
        val sampler = NetworkThroughputSampler()

        assertNull(sampler.accept(snapshot(time = 1_000_000_000L, uploaded = 100L, downloaded = 200L)))
        val history = sampler.accept(
            snapshot(time = 3_000_000_000L, uploaded = 12_600L, downloaded = 25_200L),
        )!!

        assertEquals(50_000L, history.currentUploadBitsPerSecond)
        assertEquals(100_000L, history.currentDownloadBitsPerSecond)
        assertTrue(history.currentSampleIsValid)
    }

    @Test
    fun `counter reset invalidates one interval and duplicate timestamps cannot advance history`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = 0L, uploaded = 10L, downloaded = 20L))

        val reset = sampler.accept(snapshot(time = 1_000_000_000L, uploaded = 1L, downloaded = 2L))!!
        assertFalse(reset.currentSampleIsValid)
        assertEquals(0L, reset.currentDownloadBitsPerSecond)
        assertEquals(1, reset.sampleCount)

        assertNull(sampler.accept(snapshot(time = 1_000_000_000L, uploaded = 2L, downloaded = 3L)))
        val recovered = requireNotNull(sampler.accept(snapshot(time = 2_000_000_000L, uploaded = 101L, downloaded = 202L)))
        assertTrue(recovered.currentSampleIsValid)
        assertEquals(800L, recovered.currentUploadBitsPerSecond)
        assertEquals(1_600L, recovered.currentDownloadBitsPerSecond)
        assertEquals(2, recovered.sampleCount)
    }

    @Test
    fun `long gaps are not rendered as misleading averaged throughput`() {
        val sampler = NetworkThroughputSampler(maximumSampleIntervalNanos = 2_000_000_000L)
        sampler.accept(snapshot(time = 1L, uploaded = 0L, downloaded = 0L))

        val history = sampler.accept(
            snapshot(time = 3_000_000_002L, uploaded = 1_000_000L, downloaded = 1_000_000L),
        )!!

        assertFalse(history.currentSampleIsValid)
        assertEquals(3, history.sampleCount)
    }

    @Test
    fun `out of order and duplicate readings neither consume slots nor change the rate baseline`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L))
        assertNull(sampler.accept(snapshot(time = 500_000_000L, uploaded = 1_250L, downloaded = 6_250L)))
        assertNull(sampler.accept(snapshot(time = 500_000_000L, uploaded = 99_000L, downloaded = 99_000L)))
        assertNull(sampler.accept(snapshot(time = 400_000_000L, uploaded = 0L, downloaded = 0L)))
        val history = requireNotNull(sampler.accept(snapshot(time = 1_000_000_000L, uploaded = 2_500L, downloaded = 12_500L)))
        assertEquals(1, history.sampleCount)
        assertEquals(100_000L, history.currentDownloadBitsPerSecond)
        assertEquals(20_000L, history.currentUploadBitsPerSecond)
        assertTrue(history.currentSampleIsValid)
    }

    @Test
    fun `subsecond counter reset remains invalid even if new counters pass the old baseline`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L))
        assertNull(sampler.accept(snapshot(time = 500_000_000L, uploaded = 100L, downloaded = 1_000L)))
        assertNull(sampler.accept(snapshot(time = 750_000_000L, uploaded = 0L, downloaded = 0L)))
        val reset = requireNotNull(sampler.accept(snapshot(time = 1_000_000_000L, uploaded = 2_500L, downloaded = 12_500L)))
        assertFalse(reset.currentSampleIsValid)
        assertEquals(0L, reset.currentDownloadBitsPerSecond)
        val recovered = requireNotNull(sampler.accept(snapshot(time = 2_000_000_000L, uploaded = 5_000L, downloaded = 25_000L)))
        assertTrue(recovered.currentSampleIsValid)
        assertEquals(100_000L, recovered.currentDownloadBitsPerSecond)
        assertEquals(20_000L, recovered.currentUploadBitsPerSecond)
    }

    @Test
    fun `monitor restart drops pending observations and large gaps retain only bounded invalid slots`() {
        val sampler = NetworkThroughputSampler(capacity = 3)
        sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L))
        assertNull(sampler.accept(snapshot(time = 500_000_000L, uploaded = 10L, downloaded = 20L)))
        sampler.reset()
        assertNull(sampler.accept(snapshot(time = 10_000_000_000L, uploaded = 100L, downloaded = 200L)))
        val restarted = requireNotNull(sampler.accept(snapshot(time = 11_000_000_000L, uploaded = 200L, downloaded = 400L)))
        assertEquals(1, restarted.sampleCount)
        assertEquals(1_600L, restarted.currentDownloadBitsPerSecond)
        val suspended = requireNotNull(sampler.accept(snapshot(time = Long.MAX_VALUE, uploaded = 1_000L, downloaded = 2_000L)))
        assertEquals(3, suspended.sampleCount)
        assertContentEquals(booleanArrayOf(false, false, false), suspended.validSamples)
        assertContentEquals(longArrayOf(0L, 0L, 0L), suspended.downloadBitsPerSecond)
    }

    @Test
    fun `history remains fixed size and right aligned after wraparound`() {
        val sampler = NetworkThroughputSampler(capacity = 3)
        sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L))
        repeat(5) { index ->
            val sample = index + 1L
            sampler.accept(
                snapshot(
                    time = sample * 1_000_000_000L,
                    uploaded = sample * 100L,
                    downloaded = sample * 200L,
                ),
            )
        }

        val history = sampler.accept(
            snapshot(time = 6_000_000_000L, uploaded = 900L, downloaded = 1_800L),
        )!!

        assertEquals(3, history.sampleCount)
        assertEquals(3, history.downloadBitsPerSecond.size)
        assertContentEquals(longArrayOf(1_600L, 1_600L, 6_400L), history.downloadBitsPerSecond)
        assertContentEquals(booleanArrayOf(true, true, true), history.validSamples)
    }

    @Test
    fun `sustained 100 kbps samples settle exactly and idle intervals fall to zero`() {
        val sampler = NetworkThroughputSampler(capacity = 4)
        sampler.accept(snapshot(time = 0L, uploaded = 0L, downloaded = 0L))
        repeat(3) { index ->
            val second = index + 1L
            val history = requireNotNull(
                sampler.accept(
                    snapshot(
                        time = second * 1_000_000_000L,
                        uploaded = 0L,
                        downloaded = second * 12_500L,
                    ),
                ),
            )
            assertEquals(100_000L, history.currentDownloadBitsPerSecond)
        }

        val idle = requireNotNull(
            sampler.accept(snapshot(time = 4_000_000_000L, uploaded = 0L, downloaded = 37_500L)),
        )
        assertTrue(idle.currentSampleIsValid)
        assertEquals(0L, idle.currentDownloadBitsPerSecond)
    }

    @Test
    fun `very large counters never produce negative rates and backward clocks do not add a point`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = Long.MAX_VALUE - 1_000_000_000L, uploaded = Long.MAX_VALUE - 100L, downloaded = 0L))
        val large = requireNotNull(
            sampler.accept(snapshot(time = Long.MAX_VALUE, uploaded = Long.MAX_VALUE, downloaded = Long.MAX_VALUE)),
        )
        assertTrue(large.currentSampleIsValid)
        assertTrue(large.currentUploadBitsPerSecond >= 0L)
        assertTrue(large.currentDownloadBitsPerSecond >= 0L)

        assertNull(sampler.accept(snapshot(time = Long.MIN_VALUE, uploaded = 0L, downloaded = 0L)))
    }

    private fun snapshot(time: Long, uploaded: Long, downloaded: Long) = NetworkConditionRuntimeSnapshot(
        sampledAtNanos = time,
        uploadedBytes = uploaded,
        downloadedBytes = downloaded,
    )
}
