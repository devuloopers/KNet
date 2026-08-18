package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead

/**
 * Immutable, application-owned body content supplied to canonical traffic recording.
 *
 * The constructor takes a defensive copy, and [copyInto] is the only byte-transfer operation.
 * This keeps mutable arrays out of shared HTTP request/response models and makes ownership explicit:
 * the caller retains its array, this value owns its copy, and a capture adapter must reserve its own
 * bounded destination before copying.
 *
 * @param bytes Complete currently available body representation.
 * @property contentEncoding Optional encoding of [bytes], when they still use the wire encoding.
 */
public class TrafficBodyPayload(
    bytes: ByteArray,
    public val contentEncoding: ContentEncoding? = null,
) {
    private val content: ByteArray = bytes.copyOf()

    /** Number of bytes owned by this payload. */
    public val sizeBytes: Int
        get() = content.size

    /**
     * Copies a bounded range into storage reserved by the receiving capture adapter.
     *
     * @param destination Destination owned by the receiver.
     * @param sourceOffset First source byte to copy.
     * @param length Number of bytes to copy.
     */
    public fun copyInto(destination: ByteArray, sourceOffset: Int, length: Int) {
        require(sourceOffset >= 0) { "Traffic body source offset must not be negative." }
        require(length >= 0) { "Traffic body copy length must not be negative." }
        require(sourceOffset + length <= content.size) { "Traffic body copy exceeds the source payload." }
        require(length <= destination.size) { "Traffic body copy exceeds the destination capacity." }
        content.copyInto(
            destination = destination,
            destinationOffset = 0,
            startIndex = sourceOffset,
            endIndex = sourceOffset + length,
        )
    }

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrafficBodyPayload) return false
        return contentEncoding == other.contentEncoding && content.contentEquals(other.content)
    }

    public override fun hashCode(): Int = 31 * content.contentHashCode() + (contentEncoding?.hashCode() ?: 0)

    public override fun toString(): String =
        "TrafficBodyPayload(sizeBytes=$sizeBytes, contentEncoding=$contentEncoding)"
}

/**
 * One complete semantic HTTP exchange submitted by an in-process producer such as API Studio.
 *
 * Request and response metadata use the same canonical heads consumed by Traffic, breakpoints,
 * replay, inspectors, and export. Body content is separate because metadata is immutable/shareable,
 * while bytes require explicit bounded ownership transfer.
 *
 * @property exchangeId Stable producer-assigned exchange identity.
 * @property streamId Optional multiplexed stream identity.
 * @property request Canonical request metadata shared across KNet features.
 * @property requestBody Optional explicitly owned request content.
 * @property response Canonical response metadata when an HTTP response was observed.
 * @property responseBody Optional explicitly owned response content.
 * @property state Terminal exchange lifecycle state.
 * @property timings Observed request timings.
 * @property startedAtEpochMillis Request start timestamp.
 * @property completedAtEpochMillis Terminal timestamp.
 * @property errorCode Optional stable diagnostic code without exception or secret text.
 */
public data class RecordHttpExchangeCommand(
    public val exchangeId: ExchangeId,
    public val streamId: StreamId? = null,
    public val request: RequestHead,
    public val requestBody: TrafficBodyPayload? = null,
    public val response: ResponseHead? = null,
    public val responseBody: TrafficBodyPayload? = null,
    public val state: ExchangeState,
    public val timings: ExchangeTimings = ExchangeTimings(),
    public val startedAtEpochMillis: Long,
    public val completedAtEpochMillis: Long,
    public val errorCode: String? = null,
) {
    init {
        require(state in TERMINAL_STATES) { "Recorded HTTP exchange state must be terminal." }
        require(startedAtEpochMillis >= 0L) { "Recorded HTTP exchange start must not be negative." }
        require(completedAtEpochMillis >= startedAtEpochMillis) {
            "Recorded HTTP exchange completion must not precede its start."
        }
        require(response != null || responseBody == null) { "A response body requires response metadata." }
        require(state != ExchangeState.COMPLETED || response != null) {
            "A completed HTTP exchange requires response metadata."
        }
        require(errorCode == null || errorCode.isNotBlank()) { "Recorded HTTP error code must not be blank." }
    }

    private companion object {
        private val TERMINAL_STATES: Set<ExchangeState> = setOf(
            ExchangeState.COMPLETED,
            ExchangeState.FAILED,
            ExchangeState.DROPPED,
            ExchangeState.CANCELLED,
        )
    }
}

/**
 * Durable identity returned after an application-authored exchange has been recorded.
 *
 * @property sessionId Canonical session that owns the exchange.
 * @property exchangeId Recorded exchange identity.
 */
public data class TrafficRecordReceipt(
    public val sessionId: CaptureSessionId,
    public val exchangeId: ExchangeId,
)

/**
 * Application boundary for recording complete in-process HTTP exchanges.
 *
 * Streaming proxy transports use [CaptureIngressPort] directly. This higher-level port is for
 * bounded producers that already own a complete result and must not know Room, files, sessions,
 * or proxy-engine callback types.
 */
public interface TrafficRecordPort {
    /** Records and flushes one exchange before returning its durable canonical identity. */
    public suspend fun record(command: RecordHttpExchangeCommand): TrafficRecordReceipt
}
