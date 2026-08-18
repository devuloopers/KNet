package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSessionCatalogPort
import com.devuloopers.knet.data.desktop.capture.CanonicalTrafficQueryAdapter
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Desktop canonical traffic reader and session catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrafficQueryAdapter(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStorePort,
) : TrafficQueryPort, TrafficSessionCatalogPort {
    private val generation = AtomicLong(0L)

    override val generations: Flow<TrafficGeneration> = dao.observeLatestSessionId()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            dao.observeExchangeChangeScalar(sessionId).map {
                TrafficGeneration(
                    sessionId = CaptureSessionId(sessionId),
                    generation = generation.incrementAndGet(),
                )
            }
        }

    override val latestSessionId: Flow<CaptureSessionId?> = dao.observeLatestSessionId()
        .map { sessionId -> sessionId?.let(::CaptureSessionId) }
        .distinctUntilChanged()

    override suspend fun query(query: TrafficPageQuery): TrafficPage =
        adapter(query.sessionId).query(query)

    override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? {
        val exchange = dao.getExchange(exchangeId.value) ?: return null
        return adapter(CaptureSessionId(exchange.sessionId)).getExchange(exchangeId)
    }

    override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
        bodyStore.readBody(bodyId, range)

    private fun adapter(sessionId: CaptureSessionId): CanonicalTrafficQueryAdapter =
        CanonicalTrafficQueryAdapter(sessionId, dao, bodyStore)
}
