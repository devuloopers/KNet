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
    fun `counter reset and zero elapsed time produce invalid zero samples`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = 1L, uploaded = 10L, downloaded = 20L))

        val reset = sampler.accept(snapshot(time = 2L, uploaded = 1L, downloaded = 2L))!!
        assertFalse(reset.currentSampleIsValid)
        assertEquals(0L, reset.currentDownloadBitsPerSecond)

        val zeroElapsed = sampler.accept(snapshot(time = 2L, uploaded = 2L, downloaded = 3L))!!
        assertFalse(zeroElapsed.currentSampleIsValid)
        assertEquals(0L, zeroElapsed.currentUploadBitsPerSecond)
    }

    @Test
    fun `long gaps are not rendered as misleading averaged throughput`() {
        val sampler = NetworkThroughputSampler(maximumSampleIntervalNanos = 2_000_000_000L)
        sampler.accept(snapshot(time = 1L, uploaded = 0L, downloaded = 0L))

        val history = sampler.accept(
            snapshot(time = 3_000_000_002L, uploaded = 1_000_000L, downloaded = 1_000_000L),
        )!!

        assertFalse(history.currentSampleIsValid)
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
    fun `very large counters backward clocks and overflow resets never produce negative spikes`() {
        val sampler = NetworkThroughputSampler()
        sampler.accept(snapshot(time = Long.MAX_VALUE - 1_000_000_000L, uploaded = Long.MAX_VALUE - 100L, downloaded = 0L))
        val large = requireNotNull(
            sampler.accept(snapshot(time = Long.MAX_VALUE, uploaded = Long.MAX_VALUE, downloaded = Long.MAX_VALUE)),
        )
        assertTrue(large.currentSampleIsValid)
        assertTrue(large.currentUploadBitsPerSecond >= 0L)
        assertTrue(large.currentDownloadBitsPerSecond >= 0L)

        val wrapped = requireNotNull(sampler.accept(snapshot(time = Long.MIN_VALUE, uploaded = 0L, downloaded = 0L)))
        assertFalse(wrapped.currentSampleIsValid)
        assertEquals(0L, wrapped.currentUploadBitsPerSecond)
        assertEquals(0L, wrapped.currentDownloadBitsPerSecond)
    }

    private fun snapshot(time: Long, uploaded: Long, downloaded: Long) = NetworkConditionRuntimeSnapshot(
        sampledAtNanos = time,
        uploadedBytes = uploaded,
        downloadedBytes = downloaded,
    )
}
