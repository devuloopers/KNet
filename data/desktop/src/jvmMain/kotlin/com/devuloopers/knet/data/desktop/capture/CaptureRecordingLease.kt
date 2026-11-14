package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.ResponseHead

/**
 * Fences every side-output handle belonging to one capture binding, including already reserved frame
 * payloads. A pause closes this lease before canonical termination is enqueued. A concurrent publish
 * therefore either completes before the cut or cancels its reservation without writing afterward.
 * Locks cover only bounded in-memory reservation/event admission, never disk IO or transport writes.
 */
internal class CaptureRecordingLease {
    private val lock = Any()
    private var active = true
    private val reservations = mutableSetOf<LeasedReservation>()

    /** Runs a capture callback only while this recording binding still owns admission. */
    fun <Result> record(block: () -> Result): Result? = synchronized(lock) { if (active) block() else null }

    /** Reserves bounded bytes and retains cancellation ownership until publish or cancellation. */
    fun reserve(create: () -> ProxyBodyReservation?): ProxyBodyReservation? = synchronized(lock) {
        if (!active) return null
        val delegate = create() ?: return null
        LeasedReservation(delegate).also(reservations::add)
    }

    /** Cancels outstanding HTTP/frame allocations immediately and prevents all later callbacks. */
    fun close() = synchronized(lock) {
        active = false
        reservations.toList().forEach { it.cancel() }
    }

    private inner class LeasedReservation(private val delegate: ProxyBodyReservation) : ProxyBodyReservation {
        private var terminal = false
        override val writableBytes: ByteArray get() = delegate.writableBytes
        override fun publish(occurredAtEpochMillis: Long): Boolean = synchronized(lock) {
            if (terminal || !active) return false
            terminal = true
            reservations.remove(this)
            delegate.publish(occurredAtEpochMillis)
        }
        override fun cancel() = synchronized(lock) {
            if (!terminal) {
                terminal = true
                reservations.remove(this)
                delegate.cancel()
            }
        }
    }
}

/** Applies the recording lease uniformly to HTTP, H2/gRPC, SSE and WebSocket capture callbacks. */
internal class LeasedExchangeCapture(
    private val delegate: ProxyExchangeCapture,
    private val lease: CaptureRecordingLease,
) : ProxyExchangeCapture by delegate {
    override fun tryReserveBody(direction: TrafficDirection, contentEncoding: ContentEncoding?, requestedBytes: Int) =
        lease.reserve { delegate.tryReserveBody(direction, contentEncoding, requestedBytes) }
    override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) {
        lease.record { delegate.completeBody(direction, observedBytes, occurredAtEpochMillis) }
    }
    override fun cancelBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long, reason: TrafficTerminationReason) {
        lease.record { delegate.cancelBody(direction, observedBytes, occurredAtEpochMillis, reason) }
    }
    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        lease.record { delegate.observeResponse(response, occurredAtEpochMillis) }
    }
    override fun observeTrailers(direction: TrafficDirection, trailers: List<HeaderField>, occurredAtEpochMillis: Long) {
        lease.record { delegate.observeTrailers(direction, trailers, occurredAtEpochMillis) }
    }
    override fun terminate(outcome: ExchangeTerminalOutcome, timings: ExchangeTimings, occurredAtEpochMillis: Long) {
        lease.record { delegate.terminate(outcome, timings, occurredAtEpochMillis) }
    }
    override fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture? = lease.record {
        delegate.startMessage(metadata)?.let { LeasedMessageCapture(it, lease) }
    }
}

private class LeasedMessageCapture(
    private val delegate: ProxyMessageCapture,
    private val lease: CaptureRecordingLease,
) : ProxyMessageCapture by delegate {
    override fun tryReservePayload(requestedBytes: Int) = lease.reserve { delegate.tryReservePayload(requestedBytes) }
    override fun complete(observedBytes: Long, occurredAtEpochMillis: Long) {
        lease.record { delegate.complete(observedBytes, occurredAtEpochMillis) }
    }
    override fun terminate(observedBytes: Long, state: com.devuloopers.knet.traffic.model.message.ProtocolMessageState,
        occurredAtEpochMillis: Long, reason: TrafficTerminationReason?) {
        lease.record { delegate.terminate(observedBytes, state, occurredAtEpochMillis, reason) }
    }
}
