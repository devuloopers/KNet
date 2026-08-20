package com.devuloopers.knet.storage.capture.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.devuloopers.knet.storage.capture.entity.BodyObjectEntity
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureGapEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.storage.capture.entity.InspectionAnnotationEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.capture.model.CanonicalSessionStorageSummary
import kotlinx.coroutines.flow.Flow

/** Ordered persistence operations used by one canonical session writer. */
@Dao
interface CanonicalCaptureDao {
    /** Creates a capture session without replacing an existing lifecycle. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: CaptureSessionEntity): Long

    /** Returns one canonical session for lifecycle verification. */
    @Query("SELECT * FROM capture_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): CaptureSessionEntity?

    /** Observes the newest canonical session identity for live query invalidation. */
    @Query("SELECT id FROM capture_sessions ORDER BY startedAtEpochMillis DESC, id DESC LIMIT 1")
    fun observeLatestSessionId(): Flow<String?>

    /** Counts active sessions for lifecycle invariants and diagnostics. */
    @Query("SELECT COUNT(*) FROM capture_sessions WHERE state = 'ACTIVE'")
    suspend fun countActiveSessions(): Int

    /** Moves an active session to a terminal state only when its version advances. */
    @Query(
        "UPDATE capture_sessions SET endedAtEpochMillis = :endedAt, state = :state, version = :version " +
            "WHERE id = :sessionId AND version < :version",
    )
    suspend fun closeSession(sessionId: String, endedAt: Long, state: String, version: Long): Int

    /** Recovers sessions left active by a previous process before a new writer is opened. */
    @Query(
        "UPDATE capture_sessions SET endedAtEpochMillis = :endedAt, state = 'RECOVERED_AFTER_CRASH', " +
            "version = version + 1 WHERE state = 'ACTIVE'",
    )
    suspend fun recoverInterruptedSessions(endedAt: Long): Int

    /** Closes connections left open by a previous process with a stable recovery code. */
    @Query(
        "UPDATE traffic_connections SET sequenceVersion = sequenceVersion + 1, " +
            "closedAtEpochMillis = :closedAt, state = 'CLOSED', terminalErrorCode = 'process-interrupted' " +
            "WHERE state = 'OPEN'",
    )
    suspend fun recoverInterruptedConnections(closedAt: Long): Int

    /** Fails exchanges left non-terminal by a previous process without replacing terminal results. */
    @Query(
        "UPDATE traffic_exchanges SET version = version + 1, state = 'FAILED', " +
            "completedAtEpochMillis = :completedAt, terminalErrorCode = 'process-interrupted' " +
            "WHERE state NOT IN ('COMPLETED', 'FAILED', 'DROPPED', 'CANCELLED')",
    )
    suspend fun recoverInterruptedExchanges(completedAt: Long): Int

    /** Counts terminal sessions for global retention policy evaluation. */
    @Query("SELECT COUNT(*) FROM capture_sessions WHERE state != 'ACTIVE'")
    suspend fun countClosedSessions(): Int

    /** Sums terminal-session body bytes without loading body rows. */
    @Query(
        "SELECT COALESCE(SUM(body_objects.storedBytes), 0) FROM body_objects " +
            "INNER JOIN capture_sessions ON capture_sessions.id = body_objects.sessionId " +
            "WHERE capture_sessions.state != 'ACTIVE'",
    )
    suspend fun sumClosedSessionStoredBytes(): Long

    /** Loads a bounded oldest-first terminal-session projection for retention. */
    @Query(
        "SELECT capture_sessions.id AS sessionId, capture_sessions.startedAtEpochMillis AS startedAtEpochMillis, " +
            "COALESCE(SUM(body_objects.storedBytes), 0) AS storedBytes FROM capture_sessions " +
            "LEFT JOIN body_objects ON body_objects.sessionId = capture_sessions.id " +
            "WHERE capture_sessions.state != 'ACTIVE' GROUP BY capture_sessions.id, capture_sessions.startedAtEpochMillis " +
            "ORDER BY capture_sessions.startedAtEpochMillis ASC, capture_sessions.id ASC LIMIT :limit",
    )
    suspend fun getOldestClosedSessionSummaries(limit: Int): List<CanonicalSessionStorageSummary>

    /** Creates one connection without replacing newer state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConnection(connection: TrafficConnectionEntity): Long

    /** Returns one connection for recovery and tests. */
    @Query("SELECT * FROM traffic_connections WHERE id = :connectionId")
    suspend fun getConnection(connectionId: String): TrafficConnectionEntity?

    /** Closes a connection only when its per-connection sequence advances. */
    @Query(
        "UPDATE traffic_connections SET sequenceVersion = :sequence, closedAtEpochMillis = :closedAt, " +
            "receivedBytes = :receivedBytes, sentBytes = :sentBytes, state = 'CLOSED', " +
            "terminalErrorCode = :errorCode WHERE id = :connectionId AND sequenceVersion < :sequence",
    )
    suspend fun closeConnection(
        connectionId: String,
        sequence: Long,
        closedAt: Long,
        receivedBytes: Long,
        sentBytes: Long,
        errorCode: String?,
    ): Int

    /** Creates one exchange without replacing a newer lifecycle row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExchange(exchange: CanonicalExchangeEntity): Long

    /**
     * Creates a bounded fixture/import batch without replacing existing exchange lifecycles.
     *
     * Production capture still uses [insertExchange] through one ordered writer. This batch entry
     * point exists for bounded import and scale-fixture construction, never for unbounded buffering.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExchangeBatch(exchanges: List<CanonicalExchangeEntity>): List<Long>

    /** Returns one canonical exchange for direct lookup and tests. */
    @Query("SELECT * FROM traffic_exchanges WHERE id = :exchangeId")
    suspend fun getExchange(exchangeId: String): CanonicalExchangeEntity?

    /** Emits a compact invalidation scalar for one canonical session. */
    @Query("SELECT COUNT(*) + COALESCE(MAX(version), 0) FROM traffic_exchanges WHERE sessionId = :sessionId")
    fun observeExchangeChangeScalar(sessionId: String): Flow<Long>

    /** Loads one database-filtered newest-first keyset page across one or every retained session. */
    @Query(
        "SELECT * FROM traffic_exchanges WHERE (:sessionId IS NULL OR sessionId = :sessionId) " +
            "AND (:cursorTimestamp IS NULL OR startedAtEpochMillis < :cursorTimestamp " +
            "OR (startedAtEpochMillis = :cursorTimestamp AND id < :cursorId)) " +
            "AND (:searchPattern IS NULL OR host LIKE :searchPattern ESCAPE '\\' " +
            "OR pathAndQuery LIKE :searchPattern ESCAPE '\\' " +
            "OR method LIKE :searchPattern ESCAPE '\\' " +
            "OR CAST(responseStatusCode AS TEXT) LIKE :searchPattern ESCAPE '\\') " +
            "AND (:filterMethods = 0 OR method IN (:methods)) " +
            "AND (:filterStatuses = 0 OR responseStatusCode IN (:statuses)) " +
            "AND (:filterSchemes = 0 OR scheme IN (:schemes)) " +
            "AND (:filterProtocols = 0 OR COALESCE(responseProtocol, protocol) IN (:protocols)) " +
            "ORDER BY startedAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    suspend fun getNewestExchangePage(
        sessionId: String?,
        cursorTimestamp: Long?,
        cursorId: String?,
        searchPattern: String?,
        filterMethods: Int,
        methods: List<String>,
        filterStatuses: Int,
        statuses: List<Int>,
        filterSchemes: Int,
        schemes: List<String>,
        filterProtocols: Int,
        protocols: List<String>,
        limit: Int,
    ): List<CanonicalExchangeEntity>

    /** Loads one database-filtered oldest-first keyset page across one or every retained session. */
    @Query(
        "SELECT * FROM traffic_exchanges WHERE (:sessionId IS NULL OR sessionId = :sessionId) " +
            "AND (:cursorTimestamp IS NULL OR startedAtEpochMillis > :cursorTimestamp " +
            "OR (startedAtEpochMillis = :cursorTimestamp AND id > :cursorId)) " +
            "AND (:searchPattern IS NULL OR host LIKE :searchPattern ESCAPE '\\' " +
            "OR pathAndQuery LIKE :searchPattern ESCAPE '\\' " +
            "OR method LIKE :searchPattern ESCAPE '\\' " +
            "OR CAST(responseStatusCode AS TEXT) LIKE :searchPattern ESCAPE '\\') " +
            "AND (:filterMethods = 0 OR method IN (:methods)) " +
            "AND (:filterStatuses = 0 OR responseStatusCode IN (:statuses)) " +
            "AND (:filterSchemes = 0 OR scheme IN (:schemes)) " +
            "AND (:filterProtocols = 0 OR COALESCE(responseProtocol, protocol) IN (:protocols)) " +
            "ORDER BY startedAtEpochMillis ASC, id ASC LIMIT :limit",
    )
    suspend fun getOldestExchangePage(
        sessionId: String?,
        cursorTimestamp: Long?,
        cursorId: String?,
        searchPattern: String?,
        filterMethods: Int,
        methods: List<String>,
        filterStatuses: Int,
        statuses: List<Int>,
        filterSchemes: Int,
        schemes: List<String>,
        filterProtocols: Int,
        protocols: List<String>,
        limit: Int,
    ): List<CanonicalExchangeEntity>

    /** Attaches response metadata only when the exchange version advances and remains non-terminal. */
    @Query(
        "UPDATE traffic_exchanges SET version = :version, state = 'RESPONSE_HEADERS', " +
            "responseProtocol = :protocol, responseStatusCode = :statusCode, " +
            "responseReasonPhrase = :reasonPhrase, responseHeadersEncoded = :headersEncoded " +
            "WHERE id = :exchangeId AND version < :version " +
            "AND state NOT IN ('COMPLETED', 'FAILED', 'DROPPED', 'CANCELLED')",
    )
    suspend fun updateResponse(
        exchangeId: String,
        version: Long,
        protocol: String,
        statusCode: Int,
        reasonPhrase: String?,
        headersEncoded: String,
    ): Int

    /** Attaches a request body only when the exchange lifecycle advances. */
    @Query(
        "UPDATE traffic_exchanges SET version = :version, requestBodyId = :bodyId " +
            "WHERE id = :exchangeId AND version < :version " +
            "AND state NOT IN ('COMPLETED', 'FAILED', 'DROPPED', 'CANCELLED')",
    )
    suspend fun updateRequestBody(exchangeId: String, version: Long, bodyId: String): Int

    /** Attaches a response body only when the exchange lifecycle advances. */
    @Query(
        "UPDATE traffic_exchanges SET version = :version, responseBodyId = :bodyId " +
            "WHERE id = :exchangeId AND version < :version " +
            "AND state NOT IN ('COMPLETED', 'FAILED', 'DROPPED', 'CANCELLED')",
    )
    suspend fun updateResponseBody(exchangeId: String, version: Long, bodyId: String): Int

    /** Inserts finalized body metadata; body IDs are immutable and globally unique. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBody(body: BodyObjectEntity)

    /** Returns body metadata for bounded access and tests. */
    @Query("SELECT * FROM body_objects WHERE id = :bodyId")
    suspend fun getBody(bodyId: String): BodyObjectEntity?

    /** Loads body metadata for a bounded page without per-row queries. */
    @Query("SELECT * FROM body_objects WHERE id IN (:bodyIds)")
    suspend fun getBodies(bodyIds: List<String>): List<BodyObjectEntity>

    /** Returns the subset of an inventory page that still has a metadata owner. */
    @Query("SELECT storageKey FROM body_objects WHERE storageKey IN (:storageKeys)")
    suspend fun getExistingStorageKeys(storageKeys: List<String>): List<String>

    /** Loads a bounded keyset batch of finalized body metadata for startup verification. */
    @Query(
        "SELECT * FROM body_objects WHERE state = 'FINALIZED' AND (:afterBodyId IS NULL OR id > :afterBodyId) " +
            "ORDER BY id ASC LIMIT :limit",
    )
    suspend fun getFinalizedBodyRecoveryBatch(afterBodyId: String?, limit: Int): List<BodyObjectEntity>

    /** Marks metadata whose finalized body object is no longer readable. */
    @Query(
        "UPDATE body_objects SET state = 'MISSING', outcome = 'FAILED:body-file-missing' " +
            "WHERE id = :bodyId AND state = 'FINALIZED'",
    )
    suspend fun markBodyMissing(bodyId: String): Int

    /** Marks finalized metadata whose durable object failed an integrity check. */
    @Query(
        "UPDATE body_objects SET state = 'CORRUPT', outcome = :outcome " +
            "WHERE id = :bodyId AND state = 'FINALIZED'",
    )
    suspend fun markBodyCorrupt(bodyId: String, outcome: String): Int

    /** Atomically attaches a finalized request body or leaves no orphan metadata. */
    @Transaction
    suspend fun attachRequestBody(
        exchangeId: String,
        version: Long,
        body: BodyObjectEntity,
    ): Boolean {
        if (updateRequestBody(exchangeId, version, body.id) == 0) return false
        insertBody(body)
        return true
    }

    /** Atomically attaches a finalized response body or leaves no orphan metadata. */
    @Transaction
    suspend fun attachResponseBody(
        exchangeId: String,
        version: Long,
        body: BodyObjectEntity,
    ): Boolean {
        if (updateResponseBody(exchangeId, version, body.id) == 0) return false
        insertBody(body)
        return true
    }

    /** Moves one exchange to a terminal state only when its version advances. */
    @Query(
        "UPDATE traffic_exchanges SET version = :version, state = :state, completedAtEpochMillis = :completedAt, " +
            "timingDnsMillis = :dnsMillis, timingConnectMillis = :connectMillis, " +
            "timingTlsMillis = :tlsMillis, timingFirstByteMillis = :firstByteMillis, " +
            "timingDownloadMillis = :downloadMillis, timingTotalMillis = :totalMillis, " +
            "terminalErrorCode = :errorCode WHERE id = :exchangeId AND version < :version",
    )
    suspend fun terminateExchange(
        exchangeId: String,
        version: Long,
        state: String,
        completedAt: Long,
        dnsMillis: Long?,
        connectMillis: Long?,
        tlsMillis: Long?,
        firstByteMillis: Long?,
        downloadMillis: Long?,
        totalMillis: Long?,
        errorCode: String?,
    ): Int

    /** Persists one explicit gap rather than silently losing bounded capture work. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGap(gap: CaptureGapEntity): Long

    /** Counts explicit gaps for health and regression tests. */
    @Query("SELECT COUNT(*) FROM capture_gaps WHERE sessionId = :sessionId")
    suspend fun countGaps(sessionId: String): Long

    /** Upserts one deterministic exchange/inspector/schema annotation for safe reruns. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInspectionAnnotation(annotation: InspectionAnnotationEntity)

    /** Returns all current inspector versions for one exchange. */
    @Query(
        "SELECT * FROM inspection_annotations WHERE exchangeId = :exchangeId " +
            "ORDER BY inspectorId ASC, version DESC",
    )
    suspend fun getInspectionAnnotations(exchangeId: String): List<InspectionAnnotationEntity>

    /** Observes bounded semantic annotation changes for one selected exchange. */
    @Query(
        "SELECT * FROM inspection_annotations WHERE exchangeId = :exchangeId " +
            "ORDER BY inspectorId ASC, version DESC",
    )
    fun observeInspectionAnnotations(exchangeId: String): Flow<List<InspectionAnnotationEntity>>

    /** Observes semantic annotations for one bounded set of retained Traffic exchanges. */
    @Query(
        "SELECT * FROM inspection_annotations WHERE exchangeId IN (:exchangeIds) " +
            "ORDER BY exchangeId ASC, inspectorId ASC, version DESC",
    )
    fun observeInspectionAnnotations(exchangeIds: List<String>): Flow<List<InspectionAnnotationEntity>>

    /** Enqueues one idempotent body deletion operation. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueDeletion(operation: DeletionOutboxEntity): Long

    /** Enqueues an idempotent batch of body deletion operations. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueDeletions(operations: List<DeletionOutboxEntity>): List<Long>

    /** Returns bounded oldest deletion work. */
    @Query("SELECT * FROM deletion_outbox ORDER BY id ASC LIMIT :limit")
    suspend fun getDeletionWork(limit: Int): List<DeletionOutboxEntity>

    /** Removes a converged deletion operation. */
    @Query("DELETE FROM deletion_outbox WHERE id = :id")
    suspend fun completeDeletion(id: Long): Int

    /** Records a bounded retry failure without exposing exception text. */
    @Query(
        "UPDATE deletion_outbox SET attemptCount = attemptCount + 1, lastErrorCode = :errorCode WHERE id = :id",
    )
    suspend fun markDeletionFailed(id: Long, errorCode: String): Int

    /** Returns body IDs owned by one session before transactional metadata deletion. */
    @Query("SELECT id FROM body_objects WHERE sessionId = :sessionId")
    suspend fun getSessionBodyIds(sessionId: String): List<String>

    /** Deletes semantic annotations owned by one session. */
    @Query("DELETE FROM inspection_annotations WHERE sessionId = :sessionId")
    suspend fun deleteSessionAnnotations(sessionId: String): Int

    /** Deletes duplex message metadata owned by one session. */
    @Query("DELETE FROM duplex_messages WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String): Int

    /** Deletes capture-gap metadata owned by one session. */
    @Query("DELETE FROM capture_gaps WHERE sessionId = :sessionId")
    suspend fun deleteSessionGaps(sessionId: String): Int

    /** Deletes canonical exchange metadata owned by one session. */
    @Query("DELETE FROM traffic_exchanges WHERE sessionId = :sessionId")
    suspend fun deleteSessionExchanges(sessionId: String): Int

    /** Deletes canonical connection metadata owned by one session. */
    @Query("DELETE FROM traffic_connections WHERE sessionId = :sessionId")
    suspend fun deleteSessionConnections(sessionId: String): Int

    /** Deletes finalized body metadata after its durable outbox work is queued. */
    @Query("DELETE FROM body_objects WHERE sessionId = :sessionId")
    suspend fun deleteSessionBodies(sessionId: String): Int

    /** Deletes one terminal session record. */
    @Query("DELETE FROM capture_sessions WHERE id = :sessionId AND state != 'ACTIVE'")
    suspend fun deleteClosedSession(sessionId: String): Int

    /**
     * Atomically queues body-file cleanup and removes all metadata for one closed session.
     *
     * @return Number of body objects queued for convergent file deletion.
     */
    @Transaction
    suspend fun queueAndDeleteClosedSession(sessionId: String, requestedAtEpochMillis: Long): Int {
        val bodyIds = getSessionBodyIds(sessionId)
        if (bodyIds.isNotEmpty()) {
            enqueueDeletions(
                bodyIds.map { bodyId ->
                    DeletionOutboxEntity(
                        sessionId = sessionId,
                        bodyId = bodyId,
                        operation = "DELETE_BODY",
                        createdAtEpochMillis = requestedAtEpochMillis,
                        attemptCount = 0,
                        lastErrorCode = null,
                    )
                }
            )
        }
        deleteSessionAnnotations(sessionId)
        deleteSessionMessages(sessionId)
        deleteSessionGaps(sessionId)
        deleteSessionExchanges(sessionId)
        deleteSessionConnections(sessionId)
        deleteSessionBodies(sessionId)
        check(deleteClosedSession(sessionId) == 1) { "Canonical session must be closed before deletion." }
        return bodyIds.size
    }
}
