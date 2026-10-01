package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.TrafficGeneration
import com.devuloopers.knet.application.contract.traffic.OpaqueTrafficPageItem
import com.devuloopers.knet.application.contract.traffic.TrafficFacetCounts
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.application.contract.traffic.TrafficFacetReader
import com.devuloopers.knet.application.contract.traffic.TrafficCaptureSequence
import com.devuloopers.knet.application.contract.traffic.TrafficHistorySequence
import com.devuloopers.knet.application.contract.traffic.TrafficPage
import com.devuloopers.knet.application.contract.traffic.TrafficPageCursor
import com.devuloopers.knet.application.contract.traffic.TrafficPageItem
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficRecordFilter
import com.devuloopers.knet.application.contract.traffic.TrafficSortDirection
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.model.CanonicalExchangePageRow
import com.devuloopers.knet.storage.capture.model.CanonicalOpaqueFlowPageRow
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.OpaqueFlowSnapshot
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Indexed canonical [TrafficQuery] over current exchange/body metadata.
 *
 * A null configured session permits a query across every retained capture session. This remains an
 * internal storage implementation detail; application callers express scope through [TrafficPageQuery].
 */
internal class CanonicalTrafficQueryAdapter(
    private val sessionId: CaptureSessionId?,
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStore,
    private val currentGeneration: () -> Long = { 0L },
) : TrafficQuery, TrafficFacetReader {
    private val observedGeneration = MutableStateFlow(0L)

    override val generations: Flow<TrafficGeneration> = sessionId?.let { configuredSessionId ->
        flow {
            combine(
                dao.observeExchangeChangeScalar(configuredSessionId.value),
                dao.observeOpaqueFlowChangeScalar(configuredSessionId.value),
            ) { exchangeScalar, opaqueScalar -> exchangeScalar + opaqueScalar }.collect {
                emit(TrafficGeneration(configuredSessionId, observedGeneration.updateAndGet { value -> value + 1L }))
            }
        }
    } ?: emptyFlow()

    override suspend fun query(query: TrafficPageQuery): TrafficPage = withContext(Dispatchers.IO) {
        if (sessionId != null && query.sessionId != null && query.sessionId != sessionId) {
            return@withContext TrafficPage(
                items = emptyList(),
                nextCursor = null,
                totalCount = 0L,
                generation = currentGenerationValue(),
            )
        }
        val selectedSessionId = query.sessionId ?: sessionId
        val cursor = query.cursor?.let { CanonicalTrafficCursorCodec.decode(it, query.direction) }
        val methods = query.methods.map { it.token }.ifEmpty { listOf(UNUSED_METHOD) }
        val statuses = query.statuses.map { it.code }.ifEmpty { listOf(UNUSED_STATUS) }
        val schemes = query.schemes.map { it.token }.ifEmpty { listOf(UNUSED_SCHEME) }
        val protocols = query.protocols.map { it.token }.ifEmpty { listOf(UNUSED_PROTOCOL) }
        val searchPattern = query.searchContains?.takeIf { it.isNotBlank() }?.let(::escapedContainsPattern)
        val filterMethods = if (query.methods.isEmpty()) 0 else 1
        val filterStatuses = if (query.statuses.isEmpty()) 0 else 1
        val filterSchemes = if (query.schemes.isEmpty()) 0 else 1
        val filterProtocols = if (query.protocols.isEmpty()) 0 else 1
        val includeHttpExchanges = query.recordFilter == TrafficRecordFilter.ALL ||
            query.recordFilter == TrafficRecordFilter.DECRYPTED ||
            query.recordFilter == TrafficRecordFilter.FAILED
        val includeOpaqueFlows = query.recordFilter != TrafficRecordFilter.DECRYPTED &&
            query.methods.isEmpty() && query.statuses.isEmpty() && query.protocols.isEmpty() &&
            (query.schemes.isEmpty() || query.schemes.any { it.token.equals("https", ignoreCase = true) })
        val opaqueRecordFilter = query.recordFilter.name
        val totalCount = cursor?.totalCount ?: run {
            val httpCount = if (includeHttpExchanges) {
                dao.countExchangePageMatches(
                    sessionId = selectedSessionId?.value,
                    searchPattern = searchPattern,
                    filterMethods = filterMethods,
                    methods = methods,
                    filterStatuses = filterStatuses,
                    statuses = statuses,
                    filterSchemes = filterSchemes,
                    schemes = schemes,
                    filterProtocols = filterProtocols,
                    protocols = protocols,
                    failedOnly = if (query.recordFilter == TrafficRecordFilter.FAILED) 1 else 0,
                )
            } else {
                0L
            }
            val opaqueCount = if (includeOpaqueFlows) {
                dao.countOpaqueFlowPageMatches(
                    selectedSessionId?.value,
                    searchPattern,
                    opaqueRecordFilter,
                )
            } else {
                0L
            }
            httpCount + opaqueCount
        }
        val exchangeRows = if (!includeHttpExchanges) emptyList() else when (query.direction) {
            TrafficSortDirection.NEWEST_FIRST -> dao.getNewestExchangeChronologicalPage(
                sessionId = selectedSessionId?.value,
                cursorStartedAt = cursor?.startedAtEpochMillis,
                cursorStableKey = cursor?.stableKey,
                searchPattern = searchPattern,
                filterMethods = filterMethods,
                methods = methods,
                filterStatuses = filterStatuses,
                statuses = statuses,
                filterSchemes = filterSchemes,
                schemes = schemes,
                filterProtocols = filterProtocols,
                protocols = protocols,
                failedOnly = if (query.recordFilter == TrafficRecordFilter.FAILED) 1 else 0,
                limit = query.limit + 1,
            )
            TrafficSortDirection.OLDEST_FIRST -> dao.getOldestExchangeChronologicalPage(
                sessionId = selectedSessionId?.value,
                cursorStartedAt = cursor?.startedAtEpochMillis,
                cursorStableKey = cursor?.stableKey,
                searchPattern = searchPattern,
                filterMethods = filterMethods,
                methods = methods,
                filterStatuses = filterStatuses,
                statuses = statuses,
                filterSchemes = filterSchemes,
                schemes = schemes,
                filterProtocols = filterProtocols,
                protocols = protocols,
                failedOnly = if (query.recordFilter == TrafficRecordFilter.FAILED) 1 else 0,
                limit = query.limit + 1,
            )
        }
        val opaqueRows = if (includeOpaqueFlows) {
            when (query.direction) {
                TrafficSortDirection.NEWEST_FIRST -> dao.getNewestOpaqueFlowChronologicalPage(
                    sessionId = selectedSessionId?.value,
                    cursorStartedAt = cursor?.startedAtEpochMillis,
                    cursorStableKey = cursor?.stableKey,
                    searchPattern = searchPattern,
                    recordFilter = opaqueRecordFilter,
                    limit = query.limit + 1,
                )
                TrafficSortDirection.OLDEST_FIRST -> dao.getOldestOpaqueFlowChronologicalPage(
                    sessionId = selectedSessionId?.value,
                    cursorStartedAt = cursor?.startedAtEpochMillis,
                    cursorStableKey = cursor?.stableKey,
                    searchPattern = searchPattern,
                    recordFilter = opaqueRecordFilter,
                    limit = query.limit + 1,
                )
            }
        } else {
            emptyList()
        }
        val comparator = compareBy<CanonicalMergedTrafficRow>(
            CanonicalMergedTrafficRow::startedAtEpochMillis,
            CanonicalMergedTrafficRow::stableKey,
        ).let { base ->
            if (query.direction == TrafficSortDirection.NEWEST_FIRST) base.reversed() else base
        }
        val mergedRows = (
            exchangeRows.map(CanonicalMergedTrafficRow::Exchange) +
                opaqueRows.map(CanonicalMergedTrafficRow::Opaque)
            ).sortedWith(comparator)
        val selectedRows = mergedRows.take(query.limit)
        val historySequences = selectedRows
            .map(CanonicalMergedTrafficRow::stableKey)
            .takeIf { stableKeys -> stableKeys.isNotEmpty() }
            ?.let { stableKeys -> dao.getTrafficHistorySequences(stableKeys) }
            ?.associate { row -> row.stableKey to row.historySequence }
            .orEmpty()
        val pageExchangeRows = selectedRows.mapNotNull { row ->
            (row as? CanonicalMergedTrafficRow.Exchange)?.row
        }
        val pageOpaqueRows = selectedRows.mapNotNull { row ->
            (row as? CanonicalMergedTrafficRow.Opaque)?.row
        }
        val pageEntities = pageExchangeRows.map { row -> row.exchange }
        val bodies = loadBodies(pageEntities)
        val hasMore = mergedRows.size > query.limit
        TrafficPage(
            items = pageExchangeRows.map { row ->
                val entity = row.exchange
                TrafficPageItem(
                    captureSequence = TrafficCaptureSequence(entity.captureSequence),
                    historySequence = TrafficHistorySequence(
                        checkNotNull(historySequences["http:${entity.id}"]) {
                            "Missing merged Traffic history sequence for HTTP exchange ${entity.id}."
                        },
                    ),
                    exchange = CanonicalCaptureEntityMapper.snapshot(entity, bodies),
                )
            },
            opaqueItems = pageOpaqueRows.map { row ->
                OpaqueTrafficPageItem(
                    captureSequence = TrafficCaptureSequence(row.flow.captureSequence),
                    historySequence = TrafficHistorySequence(
                        checkNotNull(historySequences["opaque:${row.flow.id}"]) {
                            "Missing merged Traffic history sequence for opaque flow ${row.flow.id}."
                        },
                    ),
                    flow = CanonicalCaptureEntityMapper.opaqueFlowSnapshot(row.flow),
                )
            },
            nextCursor = selectedRows.lastOrNull()?.takeIf { hasMore }?.let { row ->
                CanonicalTrafficCursorCodec.encode(
                    CanonicalPageKey(
                        startedAtEpochMillis = row.startedAtEpochMillis,
                        stableKey = row.stableKey,
                        totalCount = totalCount,
                        direction = query.direction,
                    ),
                )
            },
            totalCount = totalCount,
            generation = currentGenerationValue(),
        )
    }

    override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getExchange(exchangeId.value) ?: return@withContext null
        CanonicalCaptureEntityMapper.snapshot(entity, loadBodies(listOf(entity)))
    }

    override suspend fun getOpaqueFlow(flowId: OpaqueFlowId): OpaqueFlowSnapshot? = withContext(Dispatchers.IO) {
        dao.getOpaqueFlow(flowId.value)?.let(CanonicalCaptureEntityMapper::opaqueFlowSnapshot)
    }

    override suspend fun queryFacets(query: TrafficFacetQuery): TrafficFacetCounts = withContext(Dispatchers.IO) {
        if (sessionId != null && query.sessionId != null && query.sessionId != sessionId) {
            return@withContext TrafficFacetCounts()
        }
        val methods = query.methods.map { method -> method.token }.ifEmpty { listOf(UNUSED_METHOD) }
        val statuses = query.statuses.map { status -> status.code }.ifEmpty { listOf(UNUSED_STATUS) }
        val protocols = query.protocols.map { protocol -> protocol.token }.ifEmpty { listOf(UNUSED_PROTOCOL) }
        val row = dao.getTrafficFacetCounts(
            sessionId = (query.sessionId ?: sessionId)?.value,
            searchPattern = query.searchContains?.takeIf { value -> value.isNotBlank() }
                ?.let(::escapedContainsPattern),
            filterMethods = if (query.methods.isEmpty()) 0 else 1,
            methods = methods,
            filterStatuses = if (query.statuses.isEmpty()) 0 else 1,
            statuses = statuses,
            filterProtocols = if (query.protocols.isEmpty()) 0 else 1,
            protocols = protocols,
        )
        val includeOpaqueFlows = query.methods.isEmpty() && query.statuses.isEmpty() && query.protocols.isEmpty()
        val opaqueCount = if (includeOpaqueFlows) {
            dao.countOpaqueFlowPageMatches(
                sessionId = (query.sessionId ?: sessionId)?.value,
                searchPattern = query.searchContains?.takeIf(String::isNotBlank)?.let(::escapedContainsPattern),
                recordFilter = TrafficRecordFilter.ALL.name,
            )
        } else {
            0L
        }
        TrafficFacetCounts(
            totalCount = row.totalCount + opaqueCount,
            httpCount = row.httpCount,
            httpsCount = row.httpsCount + opaqueCount,
        )
    }

    override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = bodyStore.readBody(bodyId, range)

    /** Loads all request/response body metadata for a bounded exchange page in one query. */
    private suspend fun loadBodies(entities: List<CanonicalExchangeEntity>) = entities
        .flatMap { entity -> listOfNotNull(entity.requestBodyId, entity.responseBodyId) }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.let { bodyIds -> dao.getBodies(bodyIds).associateBy { body -> body.id } }
        ?: emptyMap()

    private fun currentGenerationValue(): Long = maxOf(observedGeneration.value, currentGeneration())

    /** Escapes SQL LIKE metacharacters before adding a contains pattern. */
    private fun escapedContainsPattern(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private companion object {
        private const val UNUSED_METHOD = "__KNET_NO_METHOD__"
        private const val UNUSED_STATUS = -1
        private const val UNUSED_SCHEME = "__KNET_NO_SCHEME__"
        private const val UNUSED_PROTOCOL = "__KNET_NO_PROTOCOL__"
    }
}

/** Cursor payload retained only inside the canonical data adapter. */
private data class CanonicalPageKey(
    val startedAtEpochMillis: Long,
    val stableKey: String,
    val totalCount: Long,
    val direction: TrafficSortDirection,
)

/** One type-safe row participating in a shared chronological HTTP/opaque merge. */
private sealed interface CanonicalMergedTrafficRow {
    val startedAtEpochMillis: Long
    val stableKey: String

    data class Exchange(val row: CanonicalExchangePageRow) : CanonicalMergedTrafficRow {
        override val startedAtEpochMillis: Long = row.exchange.startedAtEpochMillis
        override val stableKey: String = "http:${row.exchange.id}"
    }

    data class Opaque(val row: CanonicalOpaqueFlowPageRow) : CanonicalMergedTrafficRow {
        override val startedAtEpochMillis: Long = row.flow.startedAtEpochMillis
        override val stableKey: String = "opaque:${row.flow.id}"
    }
}

/** Versioned opaque cursor codec for canonical keyset pages. */
private object CanonicalTrafficCursorCodec {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Encodes a page key without leaking its fields through the application API. */
    fun encode(key: CanonicalPageKey): TrafficPageCursor {
        val payload = "$CURSOR_VERSION|${key.direction.name}|${key.startedAtEpochMillis}|" +
            "${key.stableKey}|${key.totalCount}"
        return TrafficPageCursor(
            base64.encode(payload.encodeToByteArray())
        )
    }

    /** Decodes and validates a cursor against the requested direction. */
    fun decode(cursor: TrafficPageCursor, direction: TrafficSortDirection): CanonicalPageKey {
        val payload = runCatching {
            base64.decode(cursor.value).decodeToString()
        }.getOrElse { throw IllegalArgumentException("Invalid canonical traffic cursor.") }
        val components = payload.split('|', limit = 5)
        require(components.size == 5 && components[0] == CURSOR_VERSION) {
            "Unsupported canonical traffic cursor."
        }
        val encodedDirection = runCatching { TrafficSortDirection.valueOf(components[1]) }
            .getOrElse { throw IllegalArgumentException("Invalid canonical traffic cursor direction.") }
        require(encodedDirection == direction) { "Traffic cursor direction does not match the query." }
        val startedAtEpochMillis = components[2].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid canonical traffic cursor timestamp.")
        require(startedAtEpochMillis >= 0L) { "Canonical traffic cursor timestamp must not be negative." }
        val stableKey = components[3]
        require(stableKey.startsWith("http:") || stableKey.startsWith("opaque:")) {
            "Canonical traffic cursor stable key is invalid."
        }
        val totalCount = components[4].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid canonical traffic cursor total count.")
        require(totalCount >= 0L) { "Canonical traffic cursor total count must not be negative." }
        return CanonicalPageKey(startedAtEpochMillis, stableKey, totalCount, encodedDirection)
    }

    private const val CURSOR_VERSION = "c3"
}
