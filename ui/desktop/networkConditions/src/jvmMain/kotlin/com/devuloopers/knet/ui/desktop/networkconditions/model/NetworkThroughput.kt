package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import kotlin.math.roundToLong

/**
 * Immutable, right-aligned one-second chart slots. Primitive arrays keep retained history bounded.
 * @property downloadBitsPerSecond Measured interval-average download rates, oldest to newest.
 * @property uploadBitsPerSecond Measured interval-average upload rates, oldest to newest.
 * @property validSamples Whether the matching slot has trustworthy measurement evidence.
 * @property sampleCount Number of populated slots, including explicit gaps.
 */
data class NetworkThroughputHistory(
    val downloadBitsPerSecond: LongArray = LongArray(DEFAULT_CAPACITY),
    val uploadBitsPerSecond: LongArray = LongArray(DEFAULT_CAPACITY),
    val validSamples: BooleanArray = BooleanArray(DEFAULT_CAPACITY),
    val sampleCount: Int = 0,
) {
    init {
        require(downloadBitsPerSecond.size == uploadBitsPerSecond.size)
        require(downloadBitsPerSecond.size == validSamples.size)
        require(sampleCount in 0..downloadBitsPerSecond.size)
    }

    val currentDownloadBitsPerSecond: Long
        get() = downloadBitsPerSecond.lastOrNull() ?: 0L

    val currentUploadBitsPerSecond: Long
        get() = uploadBitsPerSecond.lastOrNull() ?: 0L

    val currentSampleIsValid: Boolean
        get() = validSamples.lastOrNull() == true

    companion object {
        const val DEFAULT_CAPACITY: Int = 60
    }
}

/**
 * Coalesces cumulative counters into a bounded history at most once per elapsed second.
 * Extra observations retain their bytes through the cumulative baseline without allocating chart arrays.
 * @param capacity Maximum number of one-second slots, including missing intervals.
 * @param maximumSampleIntervalNanos Longest interval whose average rate is useful; longer gaps stay invalid.
 */
internal class NetworkThroughputSampler(
    private val capacity: Int = NetworkThroughputHistory.DEFAULT_CAPACITY,
    private val maximumSampleIntervalNanos: Long = DEFAULT_MAXIMUM_SAMPLE_INTERVAL_NANOS,
) {
    private val downloadRing = LongArray(capacity)
    private val uploadRing = LongArray(capacity)
    private val validRing = BooleanArray(capacity)
    private var nextIndex = 0
    private var storedSamples = 0
    private var previous: NetworkConditionRuntimeSnapshot? = null
    private var latestObservation: NetworkConditionRuntimeSnapshot? = null
    private var countersContinuous = true

    init {
        require(capacity > 0)
        require(maximumSampleIntervalNanos >= SAMPLE_INTERVAL_NANOS)
    }

    /** Discards the rate baseline, pending observations and populated history on a monitoring restart. */
    fun reset() {
        previous = null
        latestObservation = null
        countersContinuous = true
        nextIndex = 0
        storedSamples = 0
    }

    /**
     * Establishes a first baseline, then returns history only after a full second has elapsed.
     * Duplicate/stale observations cannot advance or rewind the chart. Counter resets invalidate their entire
     * measurement interval, even when subsequent subsecond counters grow past its original baseline.
     */
    fun accept(snapshot: NetworkConditionRuntimeSnapshot): NetworkThroughputHistory? {
        val baseline = previous
        if (baseline == null) {
            previous = snapshot
            latestObservation = snapshot
            return null
        }
        val observed = requireNotNull(latestObservation)
        if (snapshot.sampledAtNanos <= observed.sampledAtNanos) return null
        latestObservation = snapshot
        countersContinuous = countersContinuous && snapshot.uploadedBytes >= observed.uploadedBytes &&
            snapshot.downloadedBytes >= observed.downloadedBytes

        val elapsedNanos = snapshot.sampledAtNanos - baseline.sampledAtNanos
        if (elapsedNanos in 1 until SAMPLE_INTERVAL_NANOS) return null
        previous = snapshot
        val uploadedDelta = snapshot.uploadedBytes - baseline.uploadedBytes
        val downloadedDelta = snapshot.downloadedBytes - baseline.downloadedBytes
        val valid = countersContinuous && elapsedNanos in SAMPLE_INTERVAL_NANOS..maximumSampleIntervalNanos &&
            baseline.uploadedBytes >= 0L && baseline.downloadedBytes >= 0L &&
            snapshot.uploadedBytes >= 0L && snapshot.downloadedBytes >= 0L &&
            uploadedDelta >= 0L &&
            downloadedDelta >= 0L
        countersContinuous = true

        // A late timer or suspended screen leaves gaps rather than compressing elapsed seconds into one slot.
        // Even a multi-hour gap performs at most capacity writes and retains no extra history.
        val slots = (elapsedNanos / SAMPLE_INTERVAL_NANOS).coerceIn(1L, capacity.toLong()).toInt()
        repeat(slots - 1) { append(0L, 0L, false) }
        append(
            downloadBitsPerSecond = if (valid) bitsPerSecond(downloadedDelta, elapsedNanos) else 0L,
            uploadBitsPerSecond = if (valid) bitsPerSecond(uploadedDelta, elapsedNanos) else 0L,
            valid = valid,
        )
        return snapshot()
    }

    private fun append(downloadBitsPerSecond: Long, uploadBitsPerSecond: Long, valid: Boolean) {
        downloadRing[nextIndex] = downloadBitsPerSecond
        uploadRing[nextIndex] = uploadBitsPerSecond
        validRing[nextIndex] = valid
        nextIndex = (nextIndex + 1) % capacity
        storedSamples = (storedSamples + 1).coerceAtMost(capacity)
    }

    private fun snapshot(): NetworkThroughputHistory {
        val download = LongArray(capacity)
        val upload = LongArray(capacity)
        val valid = BooleanArray(capacity)
        val firstOutputIndex = capacity - storedSamples
        val firstRingIndex = (nextIndex - storedSamples + capacity) % capacity
        repeat(storedSamples) { offset ->
            val source = (firstRingIndex + offset) % capacity
            val target = firstOutputIndex + offset
            download[target] = downloadRing[source]
            upload[target] = uploadRing[source]
            valid[target] = validRing[source]
        }
        return NetworkThroughputHistory(download, upload, valid, storedSamples)
    }

    private fun bitsPerSecond(byteDelta: Long, elapsedNanos: Long): Long {
        val rate = byteDelta.toDouble() * BITS_PER_BYTE * NANOS_PER_SECOND / elapsedNanos.toDouble()
        return rate.coerceIn(0.0, Long.MAX_VALUE.toDouble()).roundToLong()
    }

    private companion object {
        const val SAMPLE_INTERVAL_NANOS = 1_000_000_000L
        const val BITS_PER_BYTE = 8.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_MAXIMUM_SAMPLE_INTERVAL_NANOS = 5_000_000_000L
    }
}
