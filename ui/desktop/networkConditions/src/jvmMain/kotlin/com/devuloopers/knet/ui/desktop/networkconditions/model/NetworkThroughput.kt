package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import kotlin.math.roundToLong

/** Immutable, right-aligned 60-second chart data. Primitive arrays keep retained history bounded. */
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

/** Converts cumulative monotonic counters into a bounded, allocation-light rate history. */
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

    init {
        require(capacity > 0)
        require(maximumSampleIntervalNanos > 0L)
    }

    fun reset() {
        previous = null
        nextIndex = 0
        storedSamples = 0
    }

    /** The first snapshot establishes a baseline and intentionally produces no chart point. */
    fun accept(snapshot: NetworkConditionRuntimeSnapshot): NetworkThroughputHistory? {
        val baseline = previous
        previous = snapshot
        if (baseline == null) return null

        val elapsedNanos = snapshot.sampledAtNanos - baseline.sampledAtNanos
        val uploadedDelta = snapshot.uploadedBytes - baseline.uploadedBytes
        val downloadedDelta = snapshot.downloadedBytes - baseline.downloadedBytes
        val valid = elapsedNanos in 1..maximumSampleIntervalNanos &&
            snapshot.sampledAtNanos >= baseline.sampledAtNanos &&
            uploadedDelta >= 0L &&
            downloadedDelta >= 0L

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
        const val BITS_PER_BYTE = 8.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_MAXIMUM_SAMPLE_INTERVAL_NANOS = 5_000_000_000L
    }
}
