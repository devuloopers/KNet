package com.devuloopers.knet.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a single intercepted HTTP transaction.
 * Stores transaction metadata and file system paths for raw byte payloads.
 *
 * @property id The unique identifier of the transaction.
 * @property url The full target URL of the HTTP transaction.
 * @property method The HTTP method (e.g. GET, POST).
 * @property requestHeadersJson Serialized JSON list of request headers.
 * @property requestBodyPath Relative path to the cached request body payload on disk, or null.
 * @property responseStatusCode The HTTP response status code, or null if transaction failed or was dropped.
 * @property responseStatusText The HTTP response status text message, or null.
 * @property responseHeadersJson Serialized JSON list of response headers, or null.
 * @property responseBodyPath Relative path to the cached response body payload on disk, or null.
 * @property durationMs The latency of the request/response cycle in milliseconds.
 * @property timestamp Epoch millisecond timestamp of transaction initiation.
 */
@Entity
data class HttpTransactionEntity(
    @PrimaryKey val id: String,
    val url: String,
    val method: String,
    val requestHeadersJson: String,
    val requestBodyPath: String?,
    val responseStatusCode: Int?,
    val responseStatusText: String?,
    val responseHeadersJson: String?,
    val responseBodyPath: String?,
    val durationMs: Long,
    val timestamp: Long
)
