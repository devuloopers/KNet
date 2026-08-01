package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.model.SessionStatistics
import com.devuloopers.knet.domain.network.model.HttpRequest
import com.devuloopers.knet.domain.network.model.HttpResponse
import com.devuloopers.knet.domain.network.model.HttpTimings
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.storage.traffic.dao.HttpTransactionDao
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity

/**
 * Dedicated component handling request/response database insertion and payload file persistence.
 */
class TransactionRecorder(
    private val transactionDao: HttpTransactionDao,
    private val payloadStore: FilePayloadStore,
    private val stats: SessionStatistics = SessionStatistics()
) {

    suspend fun recordRequest(request: HttpRequest): HttpTransaction {
        val requestBodyPath = payloadStore.savePayload(request.id, "req", request.body)
        val entity = HttpTransactionEntity(
            id = request.id,
            url = request.url,
            method = request.method,
            requestHeadersJson = HttpTransactionMapper.serializeHeaders(request.headers),
            requestBodyPath = requestBodyPath,
            responseStatusCode = null,
            responseStatusText = null,
            responseHeadersJson = null,
            responseBodyPath = null,
            durationMs = 0,
            timestamp = request.timestamp
        )
        transactionDao.insert(entity)

        stats.incrementRequests()
        request.body?.let {
            stats.addBytesCaptured(it.size.toLong())
            stats.addBytesStored(it.size.toLong())
        }

        return HttpTransactionMapper.toDomainModel(entity, payloadStore)
    }

    suspend fun recordResponse(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: HttpTimings = HttpTimings()
    ): Boolean {
        val entity = transactionDao.getTransactionById(transactionId) ?: return false
        val responseBodyPath = payloadStore.savePayload(transactionId, "res", response.body)

        val updated = HttpTransactionEntity(
            id = entity.id,
            url = entity.url,
            method = entity.method,
            requestHeadersJson = entity.requestHeadersJson,
            requestBodyPath = entity.requestBodyPath,
            responseStatusCode = response.statusCode,
            responseStatusText = response.statusText,
            responseHeadersJson = HttpTransactionMapper.serializeHeaders(response.headers),
            responseBodyPath = responseBodyPath,
            durationMs = durationMs,
            timestamp = entity.timestamp,
            timingDnsMs = timings.dnsMs,
            timingTcpMs = timings.tcpMs,
            timingTlsMs = timings.tlsMs,
            timingTtfbMs = timings.ttfbMs,
            timingDownloadMs = timings.downloadMs
        )
        transactionDao.insert(updated)

        stats.incrementResponses()
        response.body?.let {
            stats.addBytesCaptured(it.size.toLong())
            stats.addBytesStored(it.size.toLong())
        }

        return true
    }
}
