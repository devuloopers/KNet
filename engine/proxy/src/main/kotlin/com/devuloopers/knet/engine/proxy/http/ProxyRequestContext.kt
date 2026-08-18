package com.devuloopers.knet.engine.proxy.http

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot

/** Transport lifecycle envelope around one canonical request snapshot. */
data class ProxyRequestContext(
    val exchangeId: ExchangeId,
    val request: HttpRequestSnapshot,
    val startedAtEpochMillis: Long,
) {
    init {
        require(startedAtEpochMillis >= 0L) { "Request start timestamp must not be negative." }
    }
}
