package com.devuloopers.knet.engine.proxy.inspection

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.util.concurrent.CompletionStage

/** Creates an optional, stream-confined payload gate for one canonical HTTP exchange. */
fun interface ProxyStreamTransformerFactory {
    /** Returns null when the request must continue through the zero-copy streaming path. */
    fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamTransformer?
}

/**
 * Asynchronous transformation boundary for protocol messages that require an intentional pause.
 *
 * The proxy transfers ownership of [payload] to the transformer. Calls are serialized per
 * direction. An implementation may hold bytes only within its documented bounded policy and must
 * eventually return exactly one terminal result. A non-matching request never creates a transformer.
 */
interface ProxyStreamTransformer {
    fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

    fun onTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) = Unit

    fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyStreamTransformResult>

    /** Releases held bytes and pending decisions after stream termination. */
    fun cancel(reason: TrafficTerminationReason?) = Unit
}

/** Result of transforming one ordered transport payload input. */
sealed interface ProxyStreamTransformResult {
    /** Bytes to forward now. Empty bytes intentionally withhold a partial framed message. */
    data class Forward(val payload: ByteArray) : ProxyStreamTransformResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Forward

            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            return payload.contentHashCode()
        }
    }

    /** Cancels only the current child stream/exchange. */
    data class DropStream(val reason: TrafficTerminationReason) : ProxyStreamTransformResult
}
