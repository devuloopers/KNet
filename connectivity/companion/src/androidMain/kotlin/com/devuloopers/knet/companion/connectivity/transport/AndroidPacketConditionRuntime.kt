package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

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
    configuration: PacketConditionConfiguration,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Volatile private var configuration: PacketConditionConfiguration = configuration
    private val revisions = MutableStateFlow(0L)
    private val uploadBudget = AggregatePacketBudget()
    private val downloadBudget = AggregatePacketBudget()
    private val queuedDatagrams = AtomicInteger()
    private val queuedControlDatagrams = AtomicInteger()

    /** Current bounded outstanding work; useful for shutdown and pressure diagnostics. */
    val queuedCount: Int get() = queuedDatagrams.get() + queuedControlDatagrams.get()

    val enabled: Boolean
        get() = configuration.enabled

    /** Atomically resets stale reservations and wakes every delayed unit for replanning. */
    @Synchronized
    fun update(configuration: PacketConditionConfiguration) {
        if (this.configuration == configuration) return
        this.configuration = configuration
        uploadBudget.reset()
        downloadBudget.reset()
        revisions.value = Math.incrementExact(revisions.value)
    }

    /** Removes abandoned reservations and re-schedules remaining owners without accumulating burst credit. */
    @Synchronized
    fun replanAfterCancellation() {
        uploadBudget.reset()
        downloadBudget.reset()
        revisions.value = Math.incrementExact(revisions.value)
    }

    /** One completed reservation, fenced against policy edits before the actual transport send. */
    data class Scheduled(val action: PacketConditionAction, val revision: Long)

    /** Waits for the latest policy's deadline; policy edits cancel stale waits instead of leaving old timers. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun schedule(direction: PacketConditionDirection, flowKey: String, bytes: Int, sequence: Long): Scheduled =
        revisions.mapLatest { revision ->
            val action = synchronized(this) {
                if (revisions.value != revision) null else plan(direction, flowKey, bytes, sequence)
            } ?: return@mapLatest null
            if (action is PacketConditionAction.Forward && action.delayMillis > 0L) delay(action.delayMillis)
            Scheduled(action, revision)
        }.filterNotNull().first()

    /** A short transport send is serialized with updates; a superseded unit must reserve under the new policy. */
    @Synchronized
    fun ifCurrent(revision: Long, send: () -> Unit): Boolean {
        if (revisions.value != revision) return false
        send()
        return true
    }

    fun tryEnqueue(controlTraffic: Boolean = false): Boolean {
        val counter = if (controlTraffic) queuedControlDatagrams else queuedDatagrams
        val limit = if (controlTraffic) MAXIMUM_CONTROL_DATAGRAMS else configuration.maximumQueuedDatagrams
        while (true) {
            val current = counter.get()
            if (current >= limit) return false
            if (counter.compareAndSet(current, current + 1)) return true
        }
    }

    fun complete(controlTraffic: Boolean = false) {
        (if (controlTraffic) queuedControlDatagrams else queuedDatagrams).updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }

    @Synchronized
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
        fun reset() { nextAvailableNanos = 0L }

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
        private const val MAXIMUM_CONTROL_DATAGRAMS: Int = 64
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
