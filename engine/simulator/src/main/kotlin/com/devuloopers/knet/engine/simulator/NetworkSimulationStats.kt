package com.devuloopers.knet.engine.simulator

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe runtime metrics tracking active network simulation statistics.
 */
class NetworkSimulationStats {

    private val _packetsDelayed = AtomicLong(0)
    private val _packetsDropped = AtomicLong(0)
    private val _bytesDelayed = AtomicLong(0)
    private val _bytesThrottled = AtomicLong(0)

    val packetsDelayed: Long get() = _packetsDelayed.get()
    val packetsDropped: Long get() = _packetsDropped.get()
    val bytesDelayed: Long get() = _bytesDelayed.get()
    val bytesThrottled: Long get() = _bytesThrottled.get()

    fun incrementPacketsDelayed() { _packetsDelayed.incrementAndGet() }
    fun incrementPacketsDropped() { _packetsDropped.incrementAndGet() }
    fun addBytesDelayed(bytes: Long) { _bytesDelayed.addAndGet(bytes) }
    fun addBytesThrottled(bytes: Long) { _bytesThrottled.addAndGet(bytes) }

    fun reset() {
        _packetsDelayed.set(0)
        _packetsDropped.set(0)
        _bytesDelayed.set(0)
        _bytesThrottled.set(0)
    }
}
