package com.devuloopers.knet.engine.proxy.inspection

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.util.concurrent.CompletionStage

/** Creates an optional observer for a connection that may switch away from HTTP framing. */
fun interface ProxyDuplexInspectorFactory {
    /**
     * Selects an observer from canonical request metadata.
     *
     * Returning `null` leaves the upgraded connection on the proxy's transparent raw relay path.
     */
    fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyDuplexInspector?
}

/**
 * Synchronous non-blocking observer for bytes carried after a successful HTTP protocol switch.
 *
 * Payload slices are borrowed only for the callback. Implementations must reserve and copy any
 * retained capture bytes and must never keep a transport-owned slice.
 */
interface ProxyDuplexInspector {
    /** Supplies the successful switching response before any duplex payload is observed. */
    fun onEstablished(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

    /** Observes one ordered borrowed payload slice in the selected direction. */
    fun onPayload(
        direction: TrafficDirection,
        payload: ProxyPayloadSlice,
        occurredAtEpochMillis: Long,
    )

    /** Releases protocol state after the upgraded connection reaches a terminal state. */
    fun onTerminated(
        outcome: ExchangeTerminalOutcome,
        occurredAtEpochMillis: Long,
    ) = Unit
}

/** Creates an optional breakpoint transformer for a selected upgraded connection. */
fun interface ProxyDuplexTransformerFactory {
    /** Returns `null` when the raw connection must remain on the zero-copy forwarding path. */
    fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyDuplexTransformer?
}

/**
 * Asynchronous ordered transformation boundary for post-upgrade bytes.
 *
 * The proxy transfers ownership of each input array. Calls are serialized independently in both
 * directions and transport reads remain paused until the returned stage completes.
 */
interface ProxyDuplexTransformer {
    /** Supplies the successful switching response before the first transformation. */
    fun onEstablished(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

    /** Transforms one owned ordered transport payload. */
    fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyDuplexTransformResult>

    /** Releases held bytes and pending decisions after the connection terminates. */
    fun cancel(reason: TrafficTerminationReason?) = Unit
}

/** Result of transforming one post-upgrade transport payload. */
sealed interface ProxyDuplexTransformResult {
    /** Bytes to forward now; an empty array intentionally withholds an incomplete message. */
    class Forward(payload: ByteArray) : ProxyDuplexTransformResult {
        private val bytes = payload.copyOf()

        /** Returns independently owned bytes for the proxy write. */
        fun copyPayload(): ByteArray = bytes.copyOf()

        override fun equals(other: Any?): Boolean =
            other is Forward && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Terminates both directions of the upgraded connection with a stable error code. */
    data class DropConnection(val reason: TrafficTerminationReason) : ProxyDuplexTransformResult
}
