package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Bounded capture-ingress limits for one session writer.
 *
 * @property metadataEventsInFlight Maximum queued metadata commands.
 * @property bodyBytesInFlight Maximum reserved, not-yet-consumed body bytes.
 * @property perBodyStoredBytes Maximum stored bytes for one request or response body.
 * @property maximumChunkBytes Maximum single reservation/copy size.
 */
public data class CaptureIngressLimits(
    public val metadataEventsInFlight: Int,
    public val bodyBytesInFlight: Long,
    public val perBodyStoredBytes: Long,
    public val maximumChunkBytes: Int = 1_048_576,
) {
    init {
        require(metadataEventsInFlight > 0) { "Metadata event limit must be positive." }
        require(bodyBytesInFlight > 0L) { "In-flight body byte limit must be positive." }
        require(perBodyStoredBytes >= 0L) { "Per-body storage limit must not be negative." }
        require(maximumChunkBytes in 1..1_048_576) { "Maximum capture chunk must be between 1 and 1048576 bytes." }
        require(maximumChunkBytes.toLong() <= bodyBytesInFlight) {
            "Maximum chunk cannot exceed the in-flight body byte limit."
        }
    }
}

/** Session-owned capture ingress health. */
public sealed interface CaptureIngressHealth {
    /** Ingress accepts metadata and body reservations normally. */
    public data object Healthy : CaptureIngressHealth

    /**
     * Ingress preserves forwarding but is dropping/truncating capture work.
     *
     * @property reason Stable overload/failure code.
     */
    public data class Degraded(public val reason: String) : CaptureIngressHealth {
        init {
            require(reason.isNotBlank()) { "Capture degradation reason must not be blank." }
        }
    }

    /** Ingress has closed permanently and accepts no new work. */
    public data object Closed : CaptureIngressHealth
}

/** Result of non-blocking metadata publication. */
public sealed interface CapturePublishResult {
    /** Event ownership transferred to the session writer. */
    public data object Accepted : CapturePublishResult

    /**
     * Event was not accepted and remains owned by the caller.
     *
     * @property reason Stable rejection code.
     */
    public data class Rejected(public val reason: String) : CapturePublishResult {
        init {
            require(reason.isNotBlank()) { "Capture rejection reason must not be blank." }
        }
    }
}

/**
 * Exclusive capture-byte reservation made before copying from a transport buffer.
 *
 * After [publish] or [cancel], the caller must never access [writableBytes] again.
 */
public interface BodyChunkReservation {
    /** Right-sized mutable storage owned by the reservation until terminal transfer. */
    public val writableBytes: ByteArray

    /**
     * Transfers this reservation to the writer without blocking the transport thread.
     *
     * @param sequence Monotonic per-connection event sequence.
     * @param occurredAtEpochMillis Wall-clock observation time.
     * @param endOfBody Whether this is the final observed chunk.
     * @return Accepted or explicit rejected result; rejection releases the reservation.
     */
    public fun publish(
        sequence: Long,
        occurredAtEpochMillis: Long,
        endOfBody: Boolean,
    ): CapturePublishResult

    /** Releases the reservation without publication. */
    public fun cancel()
}

/**
 * Non-blocking session-owned boundary between transport forwarding and canonical persistence.
 */
public interface CaptureIngressPort {
    /** Current bounded-capture health. */
    public val health: StateFlow<CaptureIngressHealth>

    /** Attempts to transfer one immutable metadata event to the writer. */
    public fun tryPublish(event: CaptureEvent): CapturePublishResult

    /**
     * Reserves body capacity before a transport copies bytes.
     *
     * @return Right-sized owned reservation, or `null` when capture must truncate without copying.
     */
    public fun tryReserveBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        exchangeVersion: Long,
        direction: TrafficDirection,
        bodyId: BodyId,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): BodyChunkReservation?

    /**
     * Terminates a streaming body after all accepted chunks have been published.
     *
     * This separate terminal command lets a transport stop copying after its capture budget or an
     * ingress rejection while still reporting the full observed wire size and explicit truncation.
     * A body with no accepted chunks remains represented by capture gaps rather than a fabricated
     * empty object.
     */
    public fun tryCompleteBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        exchangeVersion: Long,
        direction: TrafficDirection,
        bodyId: BodyId,
        observedBytes: Long,
        outcome: BodyCaptureOutcome,
        sequence: Long,
        occurredAtEpochMillis: Long,
    ): CapturePublishResult

    /** Waits until every command accepted before this call is durably processed. */
    public suspend fun flush()

    /** Permanently closes the ingress after draining accepted work. */
    public suspend fun close()
}
