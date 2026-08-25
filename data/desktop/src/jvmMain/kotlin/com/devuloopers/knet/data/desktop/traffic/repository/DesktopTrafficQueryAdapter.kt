package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePageCursor
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.port.traffic.ProtocolMessageQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSessionCatalogPort
import com.devuloopers.knet.data.desktop.capture.CanonicalTrafficQueryAdapter
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureEntityMapper
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Desktop canonical traffic reader and session catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrafficQueryAdapter(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStorePort,
) : TrafficQueryPort, TrafficSessionCatalogPort, ProtocolMessageQueryPort {
    private val generation = MutableStateFlow(0L)

    override val generations: Flow<TrafficGeneration> = dao.observeLatestSessionId()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            dao.observeExchangeChangeScalar(sessionId).map {
                TrafficGeneration(
                    sessionId = CaptureSessionId(sessionId),
                    generation = generation.updateAndGet { value -> value + 1L },
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

    override fun observeChanges(exchangeId: ExchangeId): Flow<Long> =
        dao.observeDuplexMessageChangeScalar(exchangeId.value).distinctUntilChanged()

    override suspend fun queryMessages(query: ProtocolMessagePageQuery): ProtocolMessagePage {
        val cursorValue = query.cursor?.value
        val cursorSequence = cursorValue?.substringBefore(':')?.toLongOrNull()?.also {
            require(it > 0L) { "Protocol message cursor must be positive." }
        }
        require(cursorValue == null || cursorSequence != null) { "Invalid protocol message cursor." }
        val totalCount = if (cursorValue == null) {
            dao.countDuplexMessages(query.exchangeId.value)
        } else {
            cursorValue.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
                ?: dao.countDuplexMessages(query.exchangeId.value)
        }
        val entities = dao.getDuplexMessagePage(
            exchangeId = query.exchangeId.value,
            afterCaptureSequence = cursorSequence,
            limit = query.limit + 1,
        )
        val pageEntities = entities.take(query.limit)
        val bodies = pageEntities.mapNotNull { it.bodyId }.distinct().takeIf { it.isNotEmpty() }
            ?.let { dao.getBodies(it).associateBy { body -> body.id } }
            ?: emptyMap()
        return ProtocolMessagePage(
            items = pageEntities.map { CanonicalCaptureEntityMapper.messageSnapshot(it, bodies) },
            nextCursor = pageEntities.lastOrNull()?.takeIf { entities.size > query.limit }?.let {
                ProtocolMessagePageCursor("${it.captureSequence}:$totalCount")
            },
            totalCount = totalCount,
        )
    }

    private fun adapter(sessionId: CaptureSessionId?): CanonicalTrafficQueryAdapter =
        CanonicalTrafficQueryAdapter(
            sessionId = sessionId,
            dao = dao,
            bodyStore = bodyStore,
            currentGeneration = { generation.value },
        )
}
