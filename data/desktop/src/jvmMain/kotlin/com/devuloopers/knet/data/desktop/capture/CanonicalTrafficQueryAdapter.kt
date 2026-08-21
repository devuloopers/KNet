package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficCaptureSequence
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageCursor
import com.devuloopers.knet.application.port.traffic.TrafficPageItem
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSortDirection
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Indexed canonical [TrafficQueryPort] over current exchange/body metadata.
 *
 * A null configured session permits a query across every retained capture session. This remains an
 * internal storage implementation detail; application callers express scope through [TrafficPageQuery].
 */
internal class CanonicalTrafficQueryAdapter(
    private val sessionId: CaptureSessionId?,
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStorePort,
    private val currentGeneration: () -> Long = { 0L },
) : TrafficQueryPort {
    private val observedGeneration = MutableStateFlow(0L)

    override val generations: Flow<TrafficGeneration> = sessionId?.let { configuredSessionId ->
        flow {
            dao.observeExchangeChangeScalar(configuredSessionId.value).collect {
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
        val totalCount = cursor?.totalCount ?: dao.countExchangePageMatches(
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
        )
        val entities = when (query.direction) {
            TrafficSortDirection.NEWEST_FIRST -> dao.getNewestExchangePage(
                sessionId = selectedSessionId?.value,
                cursorSequence = cursor?.captureSequence,
                searchPattern = searchPattern,
                filterMethods = filterMethods,
                methods = methods,
                filterStatuses = filterStatuses,
                statuses = statuses,
                filterSchemes = filterSchemes,
                schemes = schemes,
                filterProtocols = filterProtocols,
                protocols = protocols,
                limit = query.limit + 1,
            )
            TrafficSortDirection.OLDEST_FIRST -> dao.getOldestExchangePage(
                sessionId = selectedSessionId?.value,
                cursorSequence = cursor?.captureSequence,
                searchPattern = searchPattern,
                filterMethods = filterMethods,
                methods = methods,
                filterStatuses = filterStatuses,
                statuses = statuses,
                filterSchemes = filterSchemes,
                schemes = schemes,
                filterProtocols = filterProtocols,
                protocols = protocols,
                limit = query.limit + 1,
            )
        }
        val pageEntities = entities.take(query.limit)
        val bodies = loadBodies(pageEntities)
        val hasMore = entities.size > query.limit
        TrafficPage(
            items = pageEntities.map { entity ->
                TrafficPageItem(
                    captureSequence = TrafficCaptureSequence(entity.captureSequence),
                    exchange = CanonicalCaptureEntityMapper.snapshot(entity, bodies),
                )
            },
            nextCursor = pageEntities.lastOrNull()?.takeIf { hasMore }?.let { entity ->
                CanonicalTrafficCursorCodec.encode(
                    CanonicalPageKey(
                        captureSequence = entity.captureSequence,
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
    val captureSequence: Long,
    val totalCount: Long,
    val direction: TrafficSortDirection,
)

/** Versioned opaque cursor codec for canonical keyset pages. */
private object CanonicalTrafficCursorCodec {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Encodes a page key without leaking its fields through the application API. */
    fun encode(key: CanonicalPageKey): TrafficPageCursor {
        val payload = "$CURSOR_VERSION|${key.direction.name}|${key.captureSequence}|${key.totalCount}"
        return TrafficPageCursor(
            base64.encode(payload.encodeToByteArray())
        )
    }

    /** Decodes and validates a cursor against the requested direction. */
    fun decode(cursor: TrafficPageCursor, direction: TrafficSortDirection): CanonicalPageKey {
        val payload = runCatching {
            base64.decode(cursor.value).decodeToString()
        }.getOrElse { throw IllegalArgumentException("Invalid canonical traffic cursor.") }
        val components = payload.split('|', limit = 4)
        require(components.size == 4 && components[0] == CURSOR_VERSION) {
            "Unsupported canonical traffic cursor."
        }
        val encodedDirection = runCatching { TrafficSortDirection.valueOf(components[1]) }
            .getOrElse { throw IllegalArgumentException("Invalid canonical traffic cursor direction.") }
        require(encodedDirection == direction) { "Traffic cursor direction does not match the query." }
        val captureSequence = components[2].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid canonical traffic cursor sequence.")
        require(captureSequence > 0L) { "Canonical traffic cursor sequence must be positive." }
        val totalCount = components[3].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid canonical traffic cursor total count.")
        require(totalCount >= 0L) { "Canonical traffic cursor total count must not be negative." }
        return CanonicalPageKey(captureSequence, totalCount, encodedDirection)
    }

    private const val CURSOR_VERSION = "c2"
}
