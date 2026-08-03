package com.devuloopers.knet.engine.proxy.timing

import com.devuloopers.knet.domain.network.model.HttpTimings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe collector for precise sequential network timing metrics across DNS, TCP, TLS, TTFB, and Download phases
 * using monotonic nanosecond timestamps ([System.nanoTime]).
 */
class NetworkTimingCollector {

    private val dnsStartNanos = AtomicLong(0L)
    private val dnsEndNanos = AtomicLong(0L)

    private val tcpStartNanos = AtomicLong(0L)
    private val tcpEndNanos = AtomicLong(0L)

    private val tlsStartNanos = AtomicLong(0L)
    private val tlsEndNanos = AtomicLong(0L)

    private val requestSentNanos = AtomicLong(0L)
    private val firstByteNanos = AtomicLong(0L)
    private val lastByteNanos = AtomicLong(0L)

    private val isReusedSocketFlag = AtomicBoolean(false)

    /** Marks the start of DNS resolution. */
    fun markDnsStart() {
        dnsStartNanos.set(System.nanoTime())
    }

    /** Marks the completion of DNS resolution. */
    fun markDnsEnd() {
        dnsEndNanos.set(System.nanoTime())
    }

    /** Marks the start of TCP connection establishment. */
    fun markTcpStart() {
        tcpStartNanos.set(System.nanoTime())
    }

    /** Marks the completion of TCP connection establishment. */
    fun markTcpEnd() {
        tcpEndNanos.set(System.nanoTime())
    }

    /** Marks the start of TLS/SSL handshake. */
    fun markTlsStart() {
        tlsStartNanos.set(System.nanoTime())
    }

    /** Marks the completion of TLS/SSL handshake. */
    fun markTlsEnd() {
        tlsEndNanos.set(System.nanoTime())
    }

    /** Marks the exact moment the HTTP request headers/body are flushed to the wire. */
    fun markRequestSent() {
        requestSentNanos.set(System.nanoTime())
    }

    /** Marks the arrival of the first response header byte (TTFB). */
    fun markFirstByteReceived() {
        firstByteNanos.set(System.nanoTime())
    }

    /** Marks the arrival of the last response payload content chunk. */
    fun markLastByteReceived() {
        lastByteNanos.set(System.nanoTime())
    }

    /** Marks whether the connection reused an existing socket or TLS session. */
    fun setReusedSocket(isReused: Boolean) {
        isReusedSocketFlag.set(isReused)
    }

    /** Direct millisecond setter fallbacks for compatibility. */
    fun setDnsDuration(durationMs: Long) {
        val start = System.nanoTime()
        dnsStartNanos.set(start)
        dnsEndNanos.set(start + durationMs * 1_000_000L)
    }

    fun setTcpDuration(durationMs: Long) {
        val start = System.nanoTime()
        tcpStartNanos.set(start)
        tcpEndNanos.set(start + durationMs * 1_000_000L)
    }

    fun setSslDuration(durationMs: Long) {
        val start = System.nanoTime()
        tlsStartNanos.set(start)
        tlsEndNanos.set(start + durationMs * 1_000_000L)
    }

    fun setTtfbDuration(durationMs: Long) {
        val start = System.nanoTime()
        requestSentNanos.set(start)
        firstByteNanos.set(start + durationMs * 1_000_000L)
    }

    fun setDownloadDuration(durationMs: Long) {
        val start = firstByteNanos.get().let { if (it > 0) it else System.nanoTime() }
        firstByteNanos.set(start)
        lastByteNanos.set(start + durationMs * 1_000_000L)
    }

    /** Reads the collected timing metrics translated to milliseconds. */
    fun getTimings(): HttpTimings {
        val dnsNanos = (dnsEndNanos.get() - dnsStartNanos.get()).coerceAtLeast(0L)
        val tcpNanos = (tcpEndNanos.get() - tcpStartNanos.get()).coerceAtLeast(0L)
        val tlsNanos = (tlsEndNanos.get() - tlsStartNanos.get()).coerceAtLeast(0L)
        val ttfbNanos = (firstByteNanos.get() - requestSentNanos.get()).coerceAtLeast(0L)
        val downloadNanos = (lastByteNanos.get() - firstByteNanos.get()).coerceAtLeast(0L)

        return HttpTimings(
            dnsMs = nanosToMillis(dnsNanos),
            tcpMs = nanosToMillis(tcpNanos),
            tlsMs = nanosToMillis(tlsNanos),
            ttfbMs = nanosToMillis(ttfbNanos),
            downloadMs = nanosToMillis(downloadNanos),
            isReusedConnection = isReusedSocketFlag.get()
        )
    }

    /** Returns total sum of phase durations in milliseconds. */
    fun getTotalDuration(): Long {
        val timings = getTimings()
        return timings.totalTimeMs
    }

    private fun nanosToMillis(nanos: Long): Long {
        return if (nanos > 0L) (nanos + 500_000L) / 1_000_000L else 0L
    }
}
