package com.devuloopers.knet.engine.proxy

import java.util.concurrent.atomic.AtomicLong

/** Immutable operational metrics sampled from one proxy runtime. */
data class ProxyRuntimeMetricsSnapshot(
    /** Number of event-loop delay samples recorded since runtime construction. */
    val eventLoopLagSamples: Long,
    /** Largest observed scheduling delay in nanoseconds. */
    val maximumEventLoopLagNanos: Long,
    /** Arithmetic mean of observed scheduling delays in nanoseconds. */
    val averageEventLoopLagNanos: Long,
)

/**
 * Lock-free metrics owner for one proxy runtime.
 *
 * Recording is constant-time and never performs logging, storage, or user callbacks on event loops.
 */
class ProxyRuntimeMetrics {
    private val eventLoopLagSamples = AtomicLong(0L)
    private val eventLoopLagTotalNanos = AtomicLong(0L)
    private val maximumEventLoopLagNanos = AtomicLong(0L)

    /** Returns a coherent-enough non-blocking operational snapshot. */
    fun snapshot(): ProxyRuntimeMetricsSnapshot {
        val samples = eventLoopLagSamples.get()
        val total = eventLoopLagTotalNanos.get()
        return ProxyRuntimeMetricsSnapshot(
            eventLoopLagSamples = samples,
            maximumEventLoopLagNanos = maximumEventLoopLagNanos.get(),
            averageEventLoopLagNanos = if (samples == 0L) 0L else total / samples,
        )
    }

    /** Records one non-negative event-loop scheduling delay. */
    internal fun recordEventLoopLagNanos(delayNanos: Long) {
        val boundedDelay = delayNanos.coerceAtLeast(0L)
        eventLoopLagSamples.incrementAndGet()
        eventLoopLagTotalNanos.addAndGet(boundedDelay)
        maximumEventLoopLagNanos.accumulateAndGet(boundedDelay, ::maxOf)
    }
}
