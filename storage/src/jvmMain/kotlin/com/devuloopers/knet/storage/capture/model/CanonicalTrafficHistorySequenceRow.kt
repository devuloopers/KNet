package com.devuloopers.knet.storage.capture.model

/** One stable merged Traffic key and its ordinal across all retained HTTP and opaque records. */
data class CanonicalTrafficHistorySequenceRow(
    val stableKey: String,
    val historySequence: Long,
)
