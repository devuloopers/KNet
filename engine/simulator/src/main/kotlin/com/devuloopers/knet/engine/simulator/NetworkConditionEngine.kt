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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
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
    }

    internal fun currentTimeNanos(): Long = nanoTime()

    internal fun conditionsEnabled(): Boolean = configuration().enabled

    /** Notifies active channel handlers so queued bytes are replanned against a live edit. */
    internal fun observeConfigurationChanges(listener: () -> Unit): AutoCloseable {
        configurationListeners += listener
        return AutoCloseable { configurationListeners -= listener }
    }

    internal fun appliedCondition(host: String, port: Int): AppliedNetworkCondition? =
        NetworkConditionMatcher.resolve(configuration(), host, port)?.let { effective ->
            AppliedNetworkCondition(
                profileId = effective.profile.id.value,
                ruleId = effective.ruleId?.value,
                source = when (effective.source) {
                    EffectiveNetworkCondition.Source.DOMAIN_RULE -> AppliedNetworkConditionSource.DOMAIN_RULE
                    EffectiveNetworkCondition.Source.GLOBAL -> AppliedNetworkConditionSource.GLOBAL
                },
            )
        }

    internal fun virtualMtu(host: String, port: Int): Int? =
        NetworkConditionMatcher.resolve(configuration(), host, port)
            ?.profile
            ?.takeUnless(NetworkConditionProfile::isPassThrough)
            ?.virtualMtuBytes

    fun openFlow(): AutoCloseable {
        activeFlows.incrementAndGet()
        publish()
        return AutoCloseable {
            activeFlows.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
            publish()
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
        val profile = effective.profile
        if (profile.isPassThrough) return NetworkConditionPlan.PassThrough
        if (shouldFail(profile.failure, effective, flowSequence)) {
            faultedFlows.incrementAndGet()
            publish()
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
        val delay = bandwidthDelay + (profile.latencyMillis + jitterMillis)
            .coerceAtLeast(0L)
            .times(NANOS_PER_MILLISECOND)
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
            if (queuedBytes.compareAndSet(current, current + bytes)) {
                publish()
                return true
            }
        }
    }

    fun delivered(direction: NetworkConditionDirection, bytes: Int) {
        if (bytes > 0) {
            queuedBytes.updateAndGet { current -> (current - bytes).coerceAtLeast(0L) }
            when (direction) {
                NetworkConditionDirection.UPLOAD -> uploadedBytes.addAndGet(bytes.toLong())
                NetworkConditionDirection.DOWNLOAD -> downloadedBytes.addAndGet(bytes.toLong())
            }
        }
        publish()
    }

    fun abandon(bytes: Int) {
        if (bytes > 0) queuedBytes.updateAndGet { current -> (current - bytes).coerceAtLeast(0L) }
        publish()
    }

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
            val duration = ceil(bytes.toDouble() * 8.0 * NANOS_PER_SECOND / bitsPerSecond.toDouble()).toLong()
            nextAvailableNanos = start + duration
            return (nextAvailableNanos - now).coerceAtLeast(0L)
        }
    }

    companion object {
        const val DEFAULT_MAXIMUM_QUEUED_BYTES: Long = 64L * 1_024L * 1_024L
        private const val DEFAULT_JITTER_SEED = 0x4b4e4554L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000L

        private fun mix(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
            mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
            return mixed xor (mixed ushr 31)
        }
    }
}
