package com.devuloopers.knet.storage.capture.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable metadata for one bounded canonical capture session.
 *
 * @property id Stable session identifier.
 * @property startedAtEpochMillis Session start timestamp.
 * @property endedAtEpochMillis Terminal timestamp when closed.
 * @property state Stable lifecycle token.
 * @property version Monotonic session version.
 */
@Entity(
    tableName = "capture_sessions",
    indices = [Index(value = ["state", "startedAtEpochMillis"], name = "index_capture_session_state_started")],
)
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val state: String,
    val version: Long,
)

/**
 * Durable lifecycle and ingress metadata for one downstream connection.
 *
 * @property id Stable connection identifier.
 * @property sessionId Owning session.
 * @property sequenceVersion Highest applied connection sequence.
 * @property openedAtEpochMillis Open timestamp.
 * @property closedAtEpochMillis Close timestamp when terminal.
 * @property ingressKind Stable ingress token.
 * @property clientIdentity Optional authorized client identity.
 * @property downstreamHost Optional client host.
 * @property downstreamPort Optional client port.
 * @property listenerHost Admitting listener host.
 * @property listenerPort Admitting listener port.
 * @property transportProtocol Stable transport token.
 * @property receivedBytes Downstream bytes received.
 * @property sentBytes Downstream bytes sent.
 * @property state Stable lifecycle token.
 * @property terminalErrorCode Optional safe terminal code.
 */
@Entity(
    tableName = "traffic_connections",
    indices = [
        Index(value = ["sessionId", "openedAtEpochMillis", "id"], name = "index_connection_session_opened_id"),
        Index(value = ["sessionId", "state"], name = "index_connection_session_state"),
    ],
)
data class TrafficConnectionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sequenceVersion: Long,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long?,
    val ingressKind: String,
    val clientIdentity: String?,
    val downstreamHost: String?,
    val downstreamPort: Int?,
    val listenerHost: String,
    val listenerPort: Int?,
    val transportProtocol: String,
    val receivedBytes: Long,
    val sentBytes: Long,
    val state: String,
    val terminalErrorCode: String?,
)

/**
 * Durable canonical request/response metadata for one logical exchange.
 *
 * Request/response heads use indexed scalar columns plus versioned ordered-header encodings;
 * body columns contain opaque IDs, never paths. [captureSequence] is generated once by SQLite and
 * remains stable while lifecycle writes advance [version].
 */
@Entity(
    tableName = "traffic_exchanges",
    indices = [
        Index(value = ["id"], unique = true, name = "index_exchange_id"),
        Index(value = ["sessionId", "captureSequence"], name = "index_exchange_session_sequence"),
        Index(value = ["sessionId", "host", "captureSequence"], name = "index_exchange_session_host_sequence"),
        Index(value = ["sessionId", "method", "captureSequence"], name = "index_exchange_session_method_sequence"),
        Index(value = ["sessionId", "responseStatusCode", "captureSequence"], name = "index_exchange_session_status_sequence"),
        Index(value = ["sessionId", "protocol", "captureSequence"], name = "index_exchange_session_protocol_sequence"),
        Index(value = ["connectionId", "connectionSequence"], name = "index_exchange_connection_sequence"),
    ],
)
data class CanonicalExchangeEntity(
    @PrimaryKey(autoGenerate = true) val captureSequence: Long = 0L,
    val id: String,
    val sessionId: String,
    val connectionId: String,
    val streamId: Long?,
    val connectionSequence: Long,
    val version: Long,
    val state: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val method: String,
    val scheme: String?,
    val host: String?,
    val port: Int?,
    val pathAndQuery: String,
    val protocol: String,
    val requestHeadersEncoded: String,
    val requestBodyId: String?,
    val responseProtocol: String?,
    val responseStatusCode: Int?,
    val responseReasonPhrase: String?,
    val responseHeadersEncoded: String?,
    val responseBodyId: String?,
    val timingDnsMillis: Long?,
    val timingConnectMillis: Long?,
    val timingTlsMillis: Long?,
    val timingFirstByteMillis: Long?,
    val timingDownloadMillis: Long?,
    val timingTotalMillis: Long?,
    val terminalErrorCode: String?,
)

/**
 * Durable metadata for one atomically finalized body object.
 *
 * The [id] is an opaque body-store key. Sizes, digest, encoding, and outcome allow query/export
 * without reading the object, while [state] distinguishes finalized/recovery states. [storageKey]
 * is an opaque maintenance-only key used to find finalized objects that have no metadata owner.
 */
@Entity(
    tableName = "body_objects",
    indices = [
        Index(value = ["sessionId", "createdAtEpochMillis"], name = "index_body_session_created"),
        Index(value = ["exchangeId", "direction"], name = "index_body_exchange_direction"),
        Index(value = ["state"], name = "index_body_state"),
        Index(value = ["storageKey"], name = "index_body_storage_key", unique = true),
    ],
)
data class BodyObjectEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exchangeId: String,
    val direction: String,
    val observedBytes: Long,
    val storedBytes: Long,
    val digestAlgorithm: String?,
    val digestValue: String?,
    val contentEncoding: String?,
    val outcome: String,
    val state: String,
    val createdAtEpochMillis: Long,
    val finalizedAtEpochMillis: Long,
    val storageKey: String,
)

/**
 * Durable frame/message metadata for WebSocket, SSE, gRPC, and future duplex transports.
 *
 * Payload content is referenced through [bodyId] rather than embedded in this row.
 */
@Entity(
    tableName = "duplex_messages",
    indices = [
        Index(value = ["connectionId", "sequence"], name = "index_duplex_connection_sequence"),
        Index(value = ["exchangeId", "sequence"], name = "index_duplex_exchange_sequence"),
    ],
)
data class DuplexMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val connectionId: String,
    val exchangeId: String?,
    val streamId: Long?,
    val sequence: Long,
    val direction: String,
    val messageKind: String,
    val occurredAtEpochMillis: Long,
    val bodyId: String?,
    val terminal: Boolean,
)

/**
 * Versioned asynchronous semantic annotation attached to an exchange or message.
 *
 * [payloadEncoded] is inspector-owned versioned data; failures use safe [errorCode] values.
 */
@Entity(
    tableName = "inspection_annotations",
    indices = [
        Index(value = ["exchangeId", "inspectorId", "version"], unique = true, name = "index_annotation_exchange_inspector_version"),
        Index(value = ["sessionId", "createdAtEpochMillis"], name = "index_annotation_session_created"),
    ],
)
data class InspectionAnnotationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exchangeId: String,
    val inspectorId: String,
    val version: Long,
    val state: String,
    val payloadEncoded: String?,
    val errorCode: String?,
    val createdAtEpochMillis: Long,
)

/**
 * Compact durable record of traffic metadata or body loss under bounded capture.
 *
 * @property id Generated gap row identifier.
 * @property sessionId Owning session.
 * @property connectionId Associated connection.
 * @property sequence Nearest connection sequence.
 * @property occurredAtEpochMillis Gap timestamp.
 * @property droppedEvents Coalesced metadata loss.
 * @property droppedBodyBytes Coalesced body-byte loss.
 * @property reasonCode Stable degradation reason.
 */
@Entity(
    tableName = "capture_gaps",
    indices = [Index(value = ["sessionId", "occurredAtEpochMillis"], name = "index_gap_session_occurred")],
)
data class CaptureGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val connectionId: String,
    val sequence: Long,
    val occurredAtEpochMillis: Long,
    val droppedEvents: Long,
    val droppedBodyBytes: Long,
    val reasonCode: String,
)

/**
 * Durable retry work that converges database and body-file deletion after crashes.
 *
 * @property id Generated work identifier.
 * @property sessionId Session that owned the body.
 * @property bodyId Opaque body identifier.
 * @property operation Stable maintenance operation token.
 * @property createdAtEpochMillis Work creation timestamp.
 * @property attemptCount Failed attempt count.
 * @property lastErrorCode Optional safe failure code.
 */
@Entity(
    tableName = "deletion_outbox",
    indices = [
        Index(value = ["sessionId", "createdAtEpochMillis"], name = "index_deletion_session_created"),
        Index(value = ["bodyId"], unique = true, name = "index_deletion_body"),
    ],
)
data class DeletionOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val bodyId: String,
    val operation: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorCode: String?,
)
