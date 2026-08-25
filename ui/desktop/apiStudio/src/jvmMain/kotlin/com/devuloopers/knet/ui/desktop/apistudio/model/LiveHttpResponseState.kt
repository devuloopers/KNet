package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.application.port.apistudio.HttpLiveResponseRecord

/** Bounded presentation state for one live semantic HTTP response. */
data class LiveHttpResponseState(
    val protocolLabel: String,
    val records: List<HttpLiveResponseRecord> = emptyList(),
    val selectedSequence: Long? = null,
    val receivedBytes: Long = 0L,
    val gapCount: Long = 0L,
    val lastGapReason: String? = null,
    val droppedRecordCount: Long = 0L,
    val terminalReason: String? = null,
)
