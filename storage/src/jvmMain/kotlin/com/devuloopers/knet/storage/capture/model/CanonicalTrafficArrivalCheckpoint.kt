package com.devuloopers.knet.storage.capture.model

/** Scalar insertion boundaries for the two independently sequenced canonical traffic tables. */
data class CanonicalTrafficArrivalCheckpoint(
    /** Highest retained HTTP insertion identity, or zero for an empty table. */
    val exchanges: Long,
    /** Highest retained opaque-flow insertion identity, or zero for an empty table. */
    val opaqueFlows: Long,
)
