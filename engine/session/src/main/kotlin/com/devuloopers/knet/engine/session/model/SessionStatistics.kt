package com.devuloopers.knet.engine.session.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe runtime statistics tracking recorded session transactions and payload storage metrics.
 */
class SessionStatistics {

    private val _totalRequests = AtomicLong(0)
    private val _totalResponses = AtomicLong(0)
    private val _bytesCaptured = AtomicLong(0)
    private val _bytesStored = AtomicLong(0)

    val totalRequests: Long get() = _totalRequests.get()
    val totalResponses: Long get() = _totalResponses.get()
    val bytesCaptured: Long get() = _bytesCaptured.get()
    val bytesStored: Long get() = _bytesStored.get()

    fun incrementRequests() { _totalRequests.incrementAndGet() }
    fun incrementResponses() { _totalResponses.incrementAndGet() }
    fun addBytesCaptured(bytes: Long) { _bytesCaptured.addAndGet(bytes) }
    fun addBytesStored(bytes: Long) { _bytesStored.addAndGet(bytes) }

    fun reset() {
        _totalRequests.set(0)
        _totalResponses.set(0)
        _bytesCaptured.set(0)
        _bytesStored.set(0)
    }
}
