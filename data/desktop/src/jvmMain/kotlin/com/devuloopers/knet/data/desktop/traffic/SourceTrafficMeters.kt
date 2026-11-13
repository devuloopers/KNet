package com.devuloopers.knet.data.desktop.traffic

import com.devuloopers.knet.application.contract.device.SourceTrafficSnapshot
import com.devuloopers.knet.application.contract.device.SourceTrafficTelemetry
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.TrafficTransferCounter
import com.devuloopers.knet.traffic.model.TrafficTransferObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded primitive counters for actual admitted socket bytes. There is no observer/coroutine/payload per chunk.
 * Inactive sources are evicted only when a new source needs capacity; an evicted device starts a fresh baseline.
 */
class SourceTrafficMeters(private val nowNanos: () -> Long, private val maximumSources: Int = 256) :
    SourceTrafficTelemetry, TrafficTransferObserver {
    private class Meter { val up = AtomicLong(); val down = AtomicLong(); val active = AtomicInteger() }
    private val meters = linkedMapOf<TrafficSource, Meter>()
    private val lock = Any()
    init { require(maximumSources in 1..4096) }

    /** Admits a small counter owner for this verified source; capacity failure cannot interrupt forwarding. */
    override fun open(ingress: IngressContext): TrafficTransferCounter {
        val source = TrafficSource.fromIngress(ingress)
        val meter = synchronized(lock) {
            (meters[source] ?: run {
                if (meters.size >= maximumSources) meters.entries.firstOrNull { (_, value) -> value.active.get() == 0 }
                    ?.let { entry -> meters.remove(entry.key) }
                if (meters.size >= maximumSources) return NoCounter
                Meter().also { created -> meters[source] = created }
            }).also { selected -> selected.active.incrementAndGet() }
        }
        return object : TrafficTransferCounter {
            private val closed = AtomicBoolean()
            override fun uploaded(bytes: Long) { require(bytes >= 0); if (!closed.get()) meter.up.addAndGet(bytes) }
            override fun downloaded(bytes: Long) { require(bytes >= 0); if (!closed.get()) meter.down.addAndGet(bytes) }
            override fun close() { if (closed.compareAndSet(false, true)) meter.active.decrementAndGet() }
        }
    }

    /** Reads cumulative totals without observing captured rows or changing the sampling cadence. */
    override fun snapshot(source: TrafficSource): SourceTrafficSnapshot = synchronized(lock) {
        val selected = if (source == TrafficSource.All) meters.values.toList() else listOfNotNull(meters[source])
        SourceTrafficSnapshot(nowNanos(), selected.sumOf { meter -> meter.up.get() },
            selected.sumOf { meter -> meter.down.get() }, selected.sumOf { meter -> meter.active.get() })
    }

    private object NoCounter : TrafficTransferCounter {
        override fun uploaded(bytes: Long) = Unit
        override fun downloaded(bytes: Long) = Unit
        override fun close() = Unit
    }
}
