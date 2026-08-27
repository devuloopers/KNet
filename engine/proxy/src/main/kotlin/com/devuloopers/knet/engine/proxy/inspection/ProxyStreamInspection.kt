package com.devuloopers.knet.engine.proxy.inspection

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead

/**
 * Borrowed transport payload visible only for the duration of an inspection callback.
 *
 * Implementations must never retain this object or assume it is backed by a JVM or Netty-specific
 * buffer. A protocol inspector may copy into a fixed-capacity, configured scratch buffer required
 * for incremental decoding. Any bytes transferred to durable capture must still be reserved first
 * through canonical capture ownership.
 */
interface ProxyPayloadSlice {
    /** Number of currently readable bytes. */
    val size: Int

    /**
     * Finds [value] without transferring ownership or copying the borrowed payload.
     *
     * @param value Byte to locate.
     * @param startIndex First readable index included in the search.
     * @return the matching index, or `-1` when no match exists.
     */
    fun indexOf(value: Byte, startIndex: Int = 0): Int

    /** Copies a bounded range into caller-owned storage. */
    fun copyTo(
        destination: ByteArray,
        destinationOffset: Int = 0,
        sourceOffset: Int = 0,
        length: Int = size,
    )
}

/** Creates an optional protocol observer for one canonical HTTP exchange. */
fun interface ProxyStreamInspectorFactory {
    /** Returns null when this inspector does not recognize the request. */
    fun create(
        request: RequestHead,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamInspector?
}

/**
 * Synchronous, non-blocking observation hooks for streaming application-protocol inspectors.
 *
 * The proxy owns forwarding and lifecycle. Implementations may perform bounded state transitions
 * and canonical capture reservations only; persistence and expensive decoding remain elsewhere.
 */
interface ProxyStreamInspector {
    /** Observes final response metadata. */
    fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

    /** Observes one borrowed payload slice in wire order. */
    fun onPayload(
        direction: TrafficDirection,
        payload: ProxyPayloadSlice,
        occurredAtEpochMillis: Long,
    )

    /** Observes ordered HTTP trailers for the selected direction. */
    fun onTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) = Unit

    /** Observes a clean end of the selected HTTP message body. */
    fun onDirectionEnd(direction: TrafficDirection, occurredAtEpochMillis: Long) = Unit

    /** Observes the parent exchange terminal state. */
    fun onExchangeTerminated(
        outcome: ExchangeTerminalOutcome,
        occurredAtEpochMillis: Long,
    ) = Unit
}
