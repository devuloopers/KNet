package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeSnapshot
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.domain.networkconditions.EffectiveNetworkCondition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionMatcher
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.traffic.model.AppliedNetworkCondition
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

enum class NetworkConditionDirection { UPLOAD, DOWNLOAD }

sealed interface NetworkConditionPlan {
    data object PassThrough : NetworkConditionPlan
    data class Delay(
        val nanoseconds: Long,
        val profile: NetworkConditionProfile,
        val ruleId: NetworkConditionRuleId?,
    ) : NetworkConditionPlan
    data class Fail(val behavior: NetworkFailureBehavior) : NetworkConditionPlan
}

/**
 * Aggregate rule-scoped bandwidth clock shared by every proxy channel and HTTP/2 stream.
 * Reservations use monotonic time, do not accumulate idle burst credit, and rebase immediately
 * when a live profile rate changes.
 */
class NetworkConditionEngine(
    private val configuration: () -> NetworkConditionConfiguration,
    private val nanoTime: () -> Long = System::nanoTime,
    private val maximumQueuedBytes: Long = DEFAULT_MAXIMUM_QUEUED_BYTES,
) : NetworkConditionRuntimeTelemetry {
    private val buckets = ConcurrentHashMap<BudgetKey, AggregateBudget>()
    private val configurationListeners = CopyOnWriteArraySet<() -> Unit>()
    private val activeFlows = AtomicInteger()
    private val queuedBytes = AtomicLong()
    private val uploadedBytes = AtomicLong()
    private val downloadedBytes = AtomicLong()
    private val delayedUnits = AtomicLong()
    private val faultedFlows = AtomicLong()
    private val appliedRules = ConcurrentHashMap.newKeySet<NetworkConditionRuleId>()
    private val mutableSnapshot = MutableStateFlow(NetworkConditionRuntimeSnapshot())
    override val snapshot: StateFlow<NetworkConditionRuntimeSnapshot> = mutableSnapshot.asStateFlow()

    constructor(
        configuration: StateFlow<NetworkConditionConfiguration>,
        scope: CoroutineScope,
        nanoTime: () -> Long = System::nanoTime,
        maximumQueuedBytes: Long = DEFAULT_MAXIMUM_QUEUED_BYTES,
    ) : this(
        configuration = { configuration.value },
        nanoTime = nanoTime,
        maximumQueuedBytes = maximumQueuedBytes,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            configuration.drop(1).collect { configurationChanged() }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableSnapshot.subscriptionCount
                .map { subscriberCount -> subscriberCount > 0 }
                .distinctUntilChanged()
                .collectLatest { observed ->
                    if (observed) {
                        publish()
                        while (currentCoroutineContext().isActive) {
                            delay(TELEMETRY_SAMPLE_MILLIS)
                            publish()
                        }
                    }
                }
        }
    }

    internal fun currentTimeNanos(): Long = nanoTime()

    internal fun conditionsEnabled(): Boolean = configuration().enabled

    /** Returns the current immutable policy snapshot for protocol-boundary resolvers. */
    fun configurationSnapshot(): NetworkConditionConfiguration = configuration()

    /** Notifies active channel handlers so queued bytes are replanned against a live edit. */
    internal fun observeConfigurationChanges(listener: () -> Unit): AutoCloseable {
        configurationListeners += listener
        return AutoCloseable { configurationListeners -= listener }
    }

    internal fun appliedCondition(host: String, port: Int): AppliedNetworkCondition? =
        NetworkConditionMatcher.resolve(configuration(), host, port)?.let { effective ->
            appliedCondition(effective)
        }

    /** Maps a resolved rule selection to payload-free Traffic evidence. */
    fun appliedCondition(effective: EffectiveNetworkCondition?): AppliedNetworkCondition? = effective?.let {
        AppliedNetworkCondition(
            profileId = it.profile.id.value,
            ruleId = it.ruleId?.value,
            source = when (it.source) {
                EffectiveNetworkCondition.Source.DOMAIN_RULE -> AppliedNetworkConditionSource.DOMAIN_RULE
                EffectiveNetworkCondition.Source.PROTOCOL_RULE -> AppliedNetworkConditionSource.PROTOCOL_RULE
                EffectiveNetworkCondition.Source.GLOBAL -> AppliedNetworkConditionSource.GLOBAL
            },
        )
    }

    internal fun virtualMtu(host: String, port: Int): Int? =
        NetworkConditionMatcher.resolve(configuration(), host, port)
            ?.profile
            ?.takeUnless(NetworkConditionProfile::isPassThrough)
            ?.virtualMtuBytes

    /** Returns the active virtual MTU for an already resolved semantic or destination selection. */
    fun virtualMtu(effective: EffectiveNetworkCondition?): Int? = effective
        ?.profile
        ?.takeUnless(NetworkConditionProfile::isPassThrough)
        ?.virtualMtuBytes

    fun openFlow(): AutoCloseable {
        activeFlows.incrementAndGet()
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                activeFlows.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
            }
        }
    }

    fun plan(
        host: String,
        port: Int,
        direction: NetworkConditionDirection,
        bytes: Int,
        flowSequence: Long,
    ): NetworkConditionPlan {
        val effective = NetworkConditionMatcher.resolve(configuration(), host, port)
            ?: return NetworkConditionPlan.PassThrough
        return plan(effective, direction, bytes, flowSequence)
    }

    /** Plans one protocol-safe unit against an already resolved semantic selection. */
    fun plan(
        effective: EffectiveNetworkCondition?,
        direction: NetworkConditionDirection,
        bytes: Int,
        flowSequence: Long,
    ): NetworkConditionPlan {
        effective ?: return NetworkConditionPlan.PassThrough
        val profile = effective.profile
        if (profile.isPassThrough) return NetworkConditionPlan.PassThrough
        if (shouldFail(profile.failure, effective, flowSequence)) {
            faultedFlows.incrementAndGet()
            return NetworkConditionPlan.Fail(profile.failure)
        }
        effective.ruleId?.let(appliedRules::add)
        val rate = when (direction) {
            NetworkConditionDirection.UPLOAD -> profile.upload.effectiveBitsPerSecond
            NetworkConditionDirection.DOWNLOAD -> profile.download.effectiveBitsPerSecond
        }
        val now = nanoTime()
        val bandwidthDelay = if (rate != null && bytes > 0) {
            val key = BudgetKey(
                rule = effective.ruleId?.value ?: "global:${profile.id.value}",
                direction = direction,
            )
            buckets.computeIfAbsent(key) { AggregateBudget() }.reserve(now, bytes, rate)
        } else {
            0L
        }
        val jitterMillis = deterministicJitter(profile, effective, flowSequence)
        val fixedDelay = saturatingMultiply(
            (profile.latencyMillis + jitterMillis).coerceAtLeast(0L),
            NANOS_PER_MILLISECOND,
        )
        val delay = saturatingAdd(bandwidthDelay, fixedDelay)
        if (delay <= 0L) return NetworkConditionPlan.PassThrough
        delayedUnits.incrementAndGet()
        publish()
        return NetworkConditionPlan.Delay(delay, profile, effective.ruleId)
    }

    fun tryQueue(bytes: Int): Boolean {
        if (bytes <= 0) return true
        while (true) {
            val current = queuedBytes.get()
            if (current > maximumQueuedBytes - bytes) return false
            if (queuedBytes.compareAndSet(current, current + bytes)) return true
        }
    }

    /** Removes bytes from the bounded scheduler queue without implying that they were forwarded. */
    internal fun releaseQueued(bytes: Int) {
        if (bytes > 0) queuedBytes.updateAndGet { current -> (current - bytes).coerceAtLeast(0L) }
    }

    /** Records payload at the single common point where KNet releases it to the next pipeline stage. */
    internal fun recordForwarded(direction: NetworkConditionDirection, bytes: Int) {
        if (bytes <= 0) return
        when (direction) {
            NetworkConditionDirection.UPLOAD -> uploadedBytes.addAndGet(bytes.toLong())
            NetworkConditionDirection.DOWNLOAD -> downloadedBytes.addAndGet(bytes.toLong())
        }
    }

    fun abandon(bytes: Int) {
        if (bytes > 0) queuedBytes.updateAndGet { current -> (current - bytes).coerceAtLeast(0L) }
    }

    /** Records a scheduler-level flow fault such as bounded-queue rejection. */
    internal fun recordFaultedFlow() {
        faultedFlows.incrementAndGet()
        publish()
    }

    /** Makes deterministic tests and non-coroutine embedders able to request the latest aggregate counters. */
    internal fun refreshTelemetry() = publish()

    private fun configurationChanged() {
        buckets.clear()
        configurationListeners.forEach { listener -> listener() }
    }

    private fun shouldFail(
        failure: NetworkFailureBehavior,
        effective: EffectiveNetworkCondition,
        sequence: Long,
    ): Boolean = when (failure) {
        NetworkFailureBehavior.None -> false
        NetworkFailureBehavior.Offline,
        NetworkFailureBehavior.Timeout,
        NetworkFailureBehavior.ResetFlow,
        -> true
        is NetworkFailureBehavior.SeededReset -> {
            val hash = mix(failure.seed xor sequence xor effective.profile.id.value.hashCode().toLong())
            ((hash ushr 1) % 100L) < failure.probabilityPercent
        }
    }

    private fun deterministicJitter(
        profile: NetworkConditionProfile,
        effective: EffectiveNetworkCondition,
        sequence: Long,
    ): Long {
        val bound = profile.jitterMillis
        if (bound == 0L) return 0L
        val seed = (profile.failure as? NetworkFailureBehavior.SeededReset)?.seed ?: DEFAULT_JITTER_SEED
        val hash = mix(seed xor sequence xor effective.profile.id.value.hashCode().toLong())
        return ((hash ushr 1) % (bound * 2L + 1L)) - bound
    }

    private fun publish() {
        mutableSnapshot.value = NetworkConditionRuntimeSnapshot(
            sampledAtNanos = nanoTime(),
            activeFlows = activeFlows.get(),
            queuedBytes = queuedBytes.get(),
            uploadedBytes = uploadedBytes.get(),
            downloadedBytes = downloadedBytes.get(),
            delayedUnits = delayedUnits.get(),
            faultedFlows = faultedFlows.get(),
            appliedRuleIds = appliedRules.toSet(),
        )
    }

    private data class BudgetKey(val rule: String, val direction: NetworkConditionDirection)

    private class AggregateBudget {
        private var nextAvailableNanos = 0L
        private var lastRate = 0L

        @Synchronized
        fun reserve(now: Long, bytes: Int, bitsPerSecond: Long): Long {
            if (lastRate != bitsPerSecond) {
                nextAvailableNanos = now
                lastRate = bitsPerSecond
            }
            val start = maxOf(now, nextAvailableNanos)
            val durationAsDouble = ceil(bytes.toDouble() * 8.0 * NANOS_PER_SECOND / bitsPerSecond.toDouble())
            val duration = durationAsDouble.coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
            nextAvailableNanos = saturatingAdd(start, duration)
            return if (nextAvailableNanos >= now) nextAvailableNanos - now else Long.MAX_VALUE
        }
    }

    companion object {
        const val DEFAULT_MAXIMUM_QUEUED_BYTES: Long = 64L * 1_024L * 1_024L
        private const val TELEMETRY_SAMPLE_MILLIS = 1_000L
        private const val DEFAULT_JITTER_SEED = 0x4b4e4554L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000L

        private fun mix(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
            mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
            return mixed xor (mixed ushr 31)
        }

        private fun saturatingAdd(left: Long, right: Long): Long = when {
            right > 0L && left > Long.MAX_VALUE - right -> Long.MAX_VALUE
            right < 0L && left < Long.MIN_VALUE - right -> Long.MIN_VALUE
            else -> left + right
        }

        private fun saturatingMultiply(left: Long, right: Long): Long {
            if (left == 0L || right == 0L) return 0L
            if (left > 0L && right > 0L && left > Long.MAX_VALUE / right) return Long.MAX_VALUE
            return left * right
        }
    }
}
