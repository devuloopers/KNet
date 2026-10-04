package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import kotlin.math.ceil

internal data class ThroughputReference(
    val label: String,
    val downloadBitsPerSecond: Long? = null,
    val uploadBitsPerSecond: Long? = null,
)

internal fun NetworkThroughputHistory.hasObservedTraffic(): Boolean =
    downloadBitsPerSecond.indices.any { index ->
        validSamples.getOrElse(index) { false } &&
            (downloadBitsPerSecond[index] > 0L || uploadBitsPerSecond[index] > 0L)
    }

internal fun throughputReference(state: NetworkConditionsState): ThroughputReference {
    val configuration = state.configuration
    if (!configuration.enabled) return ThroughputReference("Conditions bypassed")
    if (configuration.rules.any { it.enabled }) return ThroughputReference("Mixed profiles")
    val profile = configuration.profile(configuration.globalProfileId)
        ?: return ThroughputReference("No global limit")
    val download = profile.download.effectiveBitsPerSecond
    val upload = profile.upload.effectiveBitsPerSecond
    val label = when {
        profile.failure == NetworkFailureBehavior.Offline -> "Offline"
        download == null && upload == null -> "Unlimited"
        else -> "Configured reference"
    }
    return ThroughputReference(label, download, upload)
}

internal fun niceChartMaximum(requiredBitsPerSecond: Long): Long {
    if (requiredBitsPerSecond <= 0L) return 100_000L
    val step = when {
        requiredBitsPerSecond <= 100_000L -> 25_000L
        requiredBitsPerSecond <= 1_000_000L -> 100_000L
        requiredBitsPerSecond <= 10_000_000L -> 1_000_000L
        requiredBitsPerSecond <= 100_000_000L -> 10_000_000L
        else -> (requiredBitsPerSecond / 4L).coerceAtLeast(1L)
    }
    val maximum = ceil(requiredBitsPerSecond.toDouble() * 1.1 / step.toDouble()) * step.toDouble()
    return maximum.coerceIn(1.0, Long.MAX_VALUE.toDouble()).toLong()
}
