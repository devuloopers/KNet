package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** Direction of an opaque UDP datagram at the Android VPN boundary. */
internal enum class PacketConditionDirection { UPLOAD, DOWNLOAD }

/** Deterministic action for one datagram. Payload bytes never enter the condition engine. */
internal sealed interface PacketConditionAction {
    data object Drop : PacketConditionAction

    data class Forward(
        val delayMillis: Long,
        val copies: Int,
    ) : PacketConditionAction
}

/**
 * Bounded aggregate UDP scheduler shared by every SOCKS UDP association in one VPN session.
 *
 * Rate reservations are direction-wide, so parallel WebRTC, QUIC, and custom UDP flows share
 * the configured budget rather than each receiving a separate limit. The runtime is deliberately
 * payload-blind and uses a deterministic seed to make loss, duplication, jitter, and reordering
 * reproducible in tests.
 */
internal class AndroidPacketConditionRuntime(
    private val configuration: PacketConditionConfiguration,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val uploadBudget = AggregatePacketBudget()
    private val downloadBudget = AggregatePacketBudget()
    private val queuedDatagrams = AtomicInteger()

    val enabled: Boolean
        get() = configuration.enabled

    fun tryEnqueue(): Boolean {
        if (!enabled) return true
        while (true) {
            val current = queuedDatagrams.get()
            if (current >= configuration.maximumQueuedDatagrams) return false
            if (queuedDatagrams.compareAndSet(current, current + 1)) return true
        }
    }

    fun complete() {
        if (enabled) queuedDatagrams.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }

    fun plan(
        direction: PacketConditionDirection,
        flowKey: String,
        bytes: Int,
        sequence: Long,
    ): PacketConditionAction {
        if (!enabled) return PacketConditionAction.Forward(delayMillis = 0L, copies = 1)
        val directionSalt = if (direction == PacketConditionDirection.UPLOAD) UPLOAD_SALT else DOWNLOAD_SALT
        if (matchesPercent(configuration.lossPercent, flowKey, sequence, directionSalt xor LOSS_SALT)) {
            return PacketConditionAction.Drop
        }

        val copies = if (
            matchesPercent(configuration.duplicationPercent, flowKey, sequence, directionSalt xor DUPLICATION_SALT)
        ) {
            2
        } else {
            1
        }

        val now = nanoTime()
        val rate = when (direction) {
            PacketConditionDirection.UPLOAD -> configuration.uploadBitsPerSecond
            PacketConditionDirection.DOWNLOAD -> configuration.downloadBitsPerSecond
        }
        val bandwidthDelayNanos = when {
            rate == null || bytes <= 0 -> 0L
            direction == PacketConditionDirection.UPLOAD -> uploadBudget.reserve(now, bytes.toLong() * copies, rate)
            else -> downloadBudget.reserve(now, bytes.toLong() * copies, rate)
        }
        val jitter = signedJitter(flowKey, sequence, directionSalt xor JITTER_SALT)
        val reorderingDelay = if (
            matchesPercent(configuration.reorderingPercent, flowKey, sequence, directionSalt xor REORDER_SALT)
        ) {
            maxOf(MINIMUM_REORDER_DELAY_MILLIS, configuration.jitterMillis * 2L)
        } else {
            0L
        }
        val fixedDelayMillis = (configuration.latencyMillis + jitter + reorderingDelay).coerceAtLeast(0L)
        val bandwidthDelayMillis = ceil(bandwidthDelayNanos.toDouble() / NANOS_PER_MILLISECOND).toLong()
        return PacketConditionAction.Forward(
            delayMillis = fixedDelayMillis + bandwidthDelayMillis,
            copies = copies,
        )
    }

    private fun signedJitter(flowKey: String, sequence: Long, salt: Long): Long {
        val bound = configuration.jitterMillis
        if (bound == 0L) return 0L
        val mixed = mix(configuration.seed xor flowKey.hashCode().toLong() xor sequence xor salt)
        return ((mixed ushr 1) % (bound * 2L + 1L)) - bound
    }

    private fun matchesPercent(percent: Int, flowKey: String, sequence: Long, salt: Long): Boolean {
        if (percent <= 0) return false
        if (percent >= 100) return true
        val mixed = mix(configuration.seed xor flowKey.hashCode().toLong() xor sequence xor salt)
        return ((mixed ushr 1) % 100L) < percent
    }

    private class AggregatePacketBudget {
        private var nextAvailableNanos: Long = 0L

        @Synchronized
        fun reserve(now: Long, bytes: Long, bitsPerSecond: Long): Long {
            val start = maxOf(now, nextAvailableNanos)
            val duration = ceil(bytes.toDouble() * 8.0 * NANOS_PER_SECOND / bitsPerSecond.toDouble()).toLong()
            nextAvailableNanos = start + duration
            return (nextAvailableNanos - now).coerceAtLeast(0L)
        }
    }

    private companion object {
        private const val NANOS_PER_MILLISECOND: Long = 1_000_000L
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
        private const val MINIMUM_REORDER_DELAY_MILLIS: Long = 10L
        private const val UPLOAD_SALT: Long = 0x55504c4f4144L
        private const val DOWNLOAD_SALT: Long = 0x444f574e4c4f4144L
        private const val LOSS_SALT: Long = 0x4c4f5353L
        private const val DUPLICATION_SALT: Long = 0x4455504cL
        private const val REORDER_SALT: Long = 0x52454f52444552L
        private const val JITTER_SALT: Long = 0x4a4954544552L

        private fun mix(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
            mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
            return mixed xor (mixed ushr 31)
        }
    }
}
