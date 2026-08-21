package com.devuloopers.knet.engine.proxy.capture

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead

/** Metadata supplied when one downstream transport connection is admitted. */
data class ProxyCaptureConnectionMetadata(
    val ingress: IngressContext,
    val downstream: TrafficEndpoint?,
    val localListener: TrafficEndpoint,
    val transportProtocol: String = "tcp",
)

/**
 * Non-blocking side-output boundary used by the proxy transport.
 *
 * Implementations may reserve/copy bounded bytes and enqueue metadata, but must never perform
 * storage, parsing, or other blocking work on the caller's event loop.
 */
fun interface ProxyCaptureSink {
    /** Admits capture for one transport connection, or returns `null` without affecting forwarding. */
    fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture?
}

/** Connection-scoped ordered capture ownership. */
interface ProxyConnectionCapture : AutoCloseable {
    /** Starts one exchange using the transport-assigned stable identifier. */
    fun startExchange(
        exchangeId: ExchangeId,
        request: RequestHead,
        occurredAtEpochMillis: Long,
        origin: TrafficOrigin = TrafficOrigin.ProxyClient,
    ): ProxyExchangeCapture?

    /** Closes the capture side output without closing the transport. */
    fun close(errorCode: String?)

    /** Closes normally. */
    override fun close(): Unit = close(errorCode = null)
}

/** Exchange-scoped canonical capture side output. */
interface ProxyExchangeCapture {
    val exchangeId: ExchangeId

    /** Reserves owned bytes before the transport copies from a reference-counted buffer. */
    fun tryReserveBody(
        direction: TrafficDirection,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): ProxyBodyReservation?

    /** Finalizes one direction with its complete observed wire-byte count. */
    fun completeBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
    )

    /** Terminates an incomplete body with a stable transport failure code. */
    fun cancelBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
        errorCode: String,
    )

    /** Publishes response metadata without body bytes. */
    fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long)

    /** Publishes exactly one terminal exchange state. */
    fun terminate(
        state: ExchangeState,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
        errorCode: String? = null,
    )
}

/** Exclusive bounded capture allocation. Ownership transfers exactly once through [publish] or [cancel]. */
interface ProxyBodyReservation {
    val writableBytes: ByteArray

    /** Enqueues the filled allocation and transfers ownership. */
    fun publish(occurredAtEpochMillis: Long): Boolean

    /** Releases an unused allocation. */
    fun cancel()
}
