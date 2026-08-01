package com.devuloopers.knet.storage.traffic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a single intercepted HTTP transaction.
 */
@Entity
public data class HttpTransactionEntity(
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
    val timestamp: Long,
    val timingDnsMs: Long = 0L,
    val timingTcpMs: Long = 0L,
    val timingTlsMs: Long = 0L,
    val timingTtfbMs: Long = 0L,
    val timingDownloadMs: Long = 0L
)
