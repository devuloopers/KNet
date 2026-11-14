package com.devuloopers.knet.application.contract.traffic

/**
 * Independent durable insertion high-water marks for HTTP exchanges and opaque transports.
 * Counts cannot use timestamps, loaded row counts, or presentation ordinals, which can collide or change.
 * @property exchanges Highest observed exchange capture sequence; zero denotes no exchanges.
 * @property opaqueFlows Highest observed opaque-flow capture sequence; zero denotes no opaque flows.
 */
public data class TrafficArrivalCheckpoint(public val exchanges: Long = 0, public val opaqueFlows: Long = 0) {
    init { require(exchanges >= 0 && opaqueFlows >= 0) }
}

/**
 * Metadata-only arrival summary evaluated with the page's source and filters.
 * @property checkpoint Insertion boundary sampled before counting, independent of lifecycle versions.
 * @property count Retained matching rows inserted after the caller's checkpoint, or zero on first observation.
 */
public data class TrafficArrivals(public val checkpoint: TrafficArrivalCheckpoint, public val count: Long) {
    init { require(count >= 0) }
}

/** Type-safe canonical identity in the merged HTTP/opaque Traffic ordering. */
public sealed interface TrafficRecordId {
    /** One decrypted exchange, including protocol-upgraded HTTP transports. */
    public data class Exchange(public val id: com.devuloopers.knet.traffic.id.ExchangeId) : TrafficRecordId
    /** One payload-opaque transport record. */
    public data class Opaque(public val id: com.devuloopers.knet.traffic.id.OpaqueFlowId) : TrafficRecordId
}

/**
 * Stable position in Traffic's chronological/keyset ordering, independent of display row numbers.
 * @property recordId Canonical record identity, including its table namespace.
 * @property startedAtEpochMillis Captured wall-clock order, with identity breaking equal timestamps.
 * @property captureSequence Independent table insertion sequence used to identify newly admitted records.
 */
public data class TrafficReadPoint(
    public val recordId: TrafficRecordId,
    public val startedAtEpochMillis: Long,
    public val captureSequence: TrafficCaptureSequence,
) : Comparable<TrafficReadPoint> {
    init { require(startedAtEpochMillis >= 0) }

    /** Compares the same timestamp and namespaced identity order used by canonical keyset queries. */
    override fun compareTo(other: TrafficReadPoint): Int = compareValuesBy(this, other,
        { it.startedAtEpochMillis }, { if (it.recordId is TrafficRecordId.Exchange) 0 else 1 },
        { when (val record = it.recordId) { is TrafficRecordId.Exchange -> record.id.value; is TrafficRecordId.Opaque -> record.id.value } })

    /** Returns whether this record was admitted after the supplied whole-batch acknowledgement. */
    public fun isAfter(checkpoint: TrafficArrivalCheckpoint): Boolean = captureSequence.value > when (recordId) {
        is TrafficRecordId.Exchange -> checkpoint.exchanges
        is TrafficRecordId.Opaque -> checkpoint.opaqueFlows
    }
}

/**
 * Progressive last-seen boundary plus the insertion snapshot known when it was viewed.
 * Later captures remain unread even if their timestamps are backdated below the viewed position.
 * @property position Latest fully visible canonical row; viewing it acknowledges preceding matching rows.
 * @property observedAt Insertion boundary sampled before the row was displayed.
 */
public data class TrafficSeenCheckpoint(public val position: TrafficReadPoint, public val observedAt: TrafficArrivalCheckpoint)
