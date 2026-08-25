package com.devuloopers.knet.engine.proxy.capture

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState

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
        streamId: StreamId? = null,
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

    /** Starts one framed child message, or returns null while forwarding remains unaffected. */
    fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture? = null

    /** Publishes response metadata without body bytes. */
    fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long)

    /** Publishes ordered request or response trailers without treating them as ordinary headers. */
    fun observeTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) = Unit

    /** Publishes exactly one terminal exchange state. */
    fun terminate(
        state: ExchangeState,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
        errorCode: String? = null,
    )
}

/** Transport-neutral metadata supplied when a framed child message starts. */
data class ProxyMessageCaptureMetadata(
    val messageId: ProtocolMessageId,
    val streamId: StreamId?,
    val protocol: MessageProtocolId,
    val kind: ProtocolMessageKind,
    val direction: TrafficDirection,
    val messageSequence: Long,
    val declaredBytes: Long?,
    val compressed: Boolean,
    val compressionEncoding: String?,
    val occurredAtEpochMillis: Long,
)

/** Message-scoped bounded capture side output independent from any protocol implementation. */
interface ProxyMessageCapture {
    val messageId: ProtocolMessageId

    /** Reserves owned payload bytes before copying from a transport buffer. */
    fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation?

    /** Finalizes a successfully observed framed message. */
    fun complete(observedBytes: Long, occurredAtEpochMillis: Long)

    /** Finalizes a truncated, malformed, failed, or cancelled framed message. */
    fun terminate(
        observedBytes: Long,
        state: ProtocolMessageState,
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
