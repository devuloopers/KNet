package com.devuloopers.knet.domain.inspector.model

import com.devuloopers.knet.domain.utils.detectContentTypeLabel
import com.devuloopers.knet.model.HttpTimings

/**
 * Represents an HTTP transaction UI presentation model captured by KNet.
 */
data class TransactionUiModel(
    val id: Int,
    val method: String,
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val time: String,
    val size: String,
    val dateGroup: String,
    val requestBody: String,
    val responseBody: String,
    val queryParams: Map<String, Any>,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val timings: HttpTimings = HttpTimings()
) {
    /** High-resolution timing phase shortcuts. */
    val timingDnsMs: Long get() = timings.dnsMs
    val timingTcpMs: Long get() = timings.tcpMs
    val timingTlsMs: Long get() = timings.tlsMs
    val timingTtfbMs: Long get() = timings.ttfbMs
    val timingDownloadMs: Long get() = timings.downloadMs

    /** The total execution duration of the transaction in milliseconds. */
    val totalTimeMs: Long
        get() = timings.totalTimeMs
}

/** Human-readable content type badge label for request payloads. */
val TransactionUiModel.requestContentTypeBadge: String
    get() = detectContentTypeLabel(requestHeaders, requestBody)

/** Human-readable content type badge label for response payloads. */
val TransactionUiModel.responseContentTypeBadge: String
    get() = detectContentTypeLabel(responseHeaders, responseBody)
