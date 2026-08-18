package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageCursor
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64

/**
 * Indexed canonical [TrafficQueryPort] over current exchange/body metadata.
 *
 * @property sessionId Session exclusively queried by this adapter.
 * @property dao Canonical Room DAO.
 * @property bodyStore Opaque bounded body access implementation.
 */
class CanonicalTrafficQueryAdapter(
    private val sessionId: CaptureSessionId,
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStorePort,
) : TrafficQueryPort {
    private val observedGeneration = AtomicLong(0L)

    override val generations: Flow<TrafficGeneration> = flow {
        dao.observeExchangeChangeScalar(sessionId.value).collect {
            emit(TrafficGeneration(sessionId, observedGeneration.incrementAndGet()))
        }
    }

    override suspend fun query(query: TrafficPageQuery): TrafficPage = withContext(Dispatchers.IO) {
        if (query.sessionId != sessionId) {
            return@withContext TrafficPage(emptyList(), null, observedGeneration.get())
        }
        val cursor = query.cursor?.let { CanonicalTrafficCursorCodec.decode(it, query.direction) }
        val methods = query.methods.map { it.token }.ifEmpty { listOf(UNUSED_METHOD) }
        val statuses = query.statuses.map { it.code }.ifEmpty { listOf(UNUSED_STATUS) }
        val hostPattern = query.hostContains?.takeIf { it.isNotBlank() }?.let(::escapedContainsPattern)
        val entities = when (query.direction) {
            TrafficSortDirection.NEWEST_FIRST -> dao.getNewestExchangePage(
                sessionId = sessionId.value,
                cursorTimestamp = cursor?.startedAtEpochMillis,
                cursorId = cursor?.exchangeId,
                hostPattern = hostPattern,
                filterMethods = if (query.methods.isEmpty()) 0 else 1,
                methods = methods,
                filterStatuses = if (query.statuses.isEmpty()) 0 else 1,
                statuses = statuses,
                limit = query.limit + 1,
            )
            TrafficSortDirection.OLDEST_FIRST -> dao.getOldestExchangePage(
                sessionId = sessionId.value,
                cursorTimestamp = cursor?.startedAtEpochMillis,
                cursorId = cursor?.exchangeId,
                hostPattern = hostPattern,
                filterMethods = if (query.methods.isEmpty()) 0 else 1,
                methods = methods,
                filterStatuses = if (query.statuses.isEmpty()) 0 else 1,
                statuses = statuses,
                limit = query.limit + 1,
            )
        }
        val pageEntities = entities.take(query.limit)
        val bodies = loadBodies(pageEntities)
        val hasMore = entities.size > query.limit
        TrafficPage(
            items = pageEntities.map { entity -> CanonicalCaptureEntityMapper.snapshot(entity, bodies) },
            nextCursor = pageEntities.lastOrNull()?.takeIf { hasMore }?.let { entity ->
                CanonicalTrafficCursorCodec.encode(
                    CanonicalPageKey(entity.startedAtEpochMillis, entity.id, query.direction)
                )
            },
            generation = observedGeneration.get(),
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
    }
}

/** Cursor payload retained only inside the canonical data adapter. */
private data class CanonicalPageKey(
    val startedAtEpochMillis: Long,
    val exchangeId: String,
    val direction: TrafficSortDirection,
)

/** Versioned opaque cursor codec for canonical keyset pages. */
private object CanonicalTrafficCursorCodec {
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Encodes a page key without leaking its fields through the application API. */
    fun encode(key: CanonicalPageKey): TrafficPageCursor {
        val payload = "$CURSOR_VERSION|${key.direction.name}|${key.startedAtEpochMillis}|${key.exchangeId}"
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
        val timestamp = components[2].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid canonical traffic cursor timestamp.")
        require(components[3].isNotBlank()) { "Canonical traffic cursor exchange ID is blank." }
        return CanonicalPageKey(timestamp, components[3], encodedDirection)
    }

    private const val CURSOR_VERSION = "c1"
}
