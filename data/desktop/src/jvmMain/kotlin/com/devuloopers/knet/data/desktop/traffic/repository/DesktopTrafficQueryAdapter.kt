package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.traffic.model.ClientIdentity

import com.devuloopers.knet.traffic.model.IngressContext

import com.devuloopers.knet.traffic.model.IngressKind

import com.devuloopers.knet.application.contract.traffic.TrafficConnectionIngress

import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot

import com.devuloopers.knet.traffic.id.ConnectionId

import com.devuloopers.knet.traffic.id.OpaqueFlowId

import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.TrafficFacetCounts
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.application.contract.traffic.TrafficFacetReader
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePageCursor
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePageQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficSessionCatalog
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Desktop canonical traffic reader and session catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrafficQueryAdapter(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStore,
) : TrafficQuery, TrafficFacetReader, TrafficSessionCatalog, ProtocolMessageQuery {
    private val generation = MutableStateFlow(0L)

    override val generations: Flow<TrafficGeneration> = dao.observeLatestSessionId()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            combine(
                dao.observeExchangeChangeScalar(sessionId),
                dao.observeOpaqueFlowChangeScalar(sessionId),
            ) { exchangeScalar, opaqueScalar -> exchangeScalar + opaqueScalar }.map {
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

    override suspend fun queryFacets(query: TrafficFacetQuery): TrafficFacetCounts =
        adapter(query.sessionId).queryFacets(query)

    override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? {
        val exchange = dao.getExchange(exchangeId.value) ?: return null
        return adapter(CaptureSessionId(exchange.sessionId)).getExchange(exchangeId)
    }

    override suspend fun getOpaqueFlow(flowId: OpaqueFlowId): OpaqueFlowSnapshot? =
        dao.getOpaqueFlow(flowId.value)?.let(CanonicalCaptureEntityMapper::opaqueFlowSnapshot)

    override suspend fun getConnectionIngress(connectionId: ConnectionId): TrafficConnectionIngress? {
        val connection = dao.getConnection(connectionId.value) ?: return null
        val kind = when (connection.ingressKind) {
            "LOCAL" -> IngressKind.Local
            "LAN_PAIRED_DEVICE" -> IngressKind.LanPairedDevice
            "WIFI_LAN_CLIENT" -> IngressKind.WifiLanClient
            "ADB_DEVICE" -> IngressKind.AdbDevice
            "COMPANION_DIRECT" -> IngressKind.CompanionDirect
            "COMPANION_RELAY" -> IngressKind.CompanionRelay
            else -> return null
        }
        val address = connection.downstreamHost
        val loopback = address == "::1" || address == "0:0:0:0:0:0:0:1" ||
            address?.split('.')?.let { parts ->
                parts.size == 4 && parts.first() == "127" && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
            } == true
        return TrafficConnectionIngress(
            IngressContext(kind, connection.clientIdentity?.let { ClientIdentity(it) }),
            address, loopback, CaptureSessionId(connection.sessionId),
        )
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
