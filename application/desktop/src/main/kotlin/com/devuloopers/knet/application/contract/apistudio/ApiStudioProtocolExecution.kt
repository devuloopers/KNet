package com.devuloopers.knet.application.contract.apistudio

import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import kotlinx.coroutines.flow.Flow

/** Opaque, versioned authored document owned by one API Studio protocol extension. */
public class ApiStudioProtocolDocument(
    public val id: String,
    public val name: String,
    public val kind: RequestKindId,
    public val schemaVersion: Int,
    payload: ByteArray,
) {
    private val encodedPayload: ByteArray = payload.copyOf()

    init {
        require(id.isNotBlank()) { "API Studio protocol document ID must not be blank." }
        require(name.isNotBlank()) { "API Studio protocol document name must not be blank." }
        require(schemaVersion > 0) { "API Studio protocol document schema version must be positive." }
        require(encodedPayload.size <= MAXIMUM_DOCUMENT_BYTES) { "API Studio protocol document is too large." }
    }

    public fun copyPayload(): ByteArray = encodedPayload.copyOf()

    public companion object {
        public const val MAXIMUM_DOCUMENT_BYTES: Int = 16 * 1_024 * 1_024
    }
}

/** Route selected by API Studio for this execution. */
public sealed interface ApiStudioProtocolRoute {
    public data object Direct : ApiStudioProtocolRoute

    public data class LocalProxy(
        public val host: String = "127.0.0.1",
        public val port: Int,
    ) : ApiStudioProtocolRoute {
        init {
            require(host.isNotBlank()) { "Local proxy host must not be blank." }
            require(port in 1..65_535) { "Local proxy port is invalid." }
        }
    }
}

/** Immutable execution command dispatched without teaching API Studio about protocol fields. */
public data class ApiStudioProtocolExecutionCommand(
    public val document: ApiStudioProtocolDocument,
    public val route: ApiStudioProtocolRoute = ApiStudioProtocolRoute.Direct,
)

/** Direction of one authored or received protocol message. */
public enum class ApiStudioProtocolMessageDirection {
    OUTBOUND,
    INBOUND,
}

/** Bounded protocol message shown in the generic API Studio execution timeline. */
public class ApiStudioProtocolMessage(
    public val sequence: Long,
    public val direction: ApiStudioProtocolMessageDirection,
    public val contentType: String,
    public val displayText: String,
    payload: ByteArray,
) {
    private val bytes: ByteArray = payload.copyOf()

    init {
        require(sequence > 0L) { "API Studio protocol message sequence must be positive." }
        require(contentType.isNotBlank()) { "API Studio protocol message content type must not be blank." }
        require(bytes.size <= MAXIMUM_MESSAGE_BYTES) { "API Studio protocol message is too large." }
    }

    public fun copyPayload(): ByteArray = bytes.copyOf()

    public companion object {
        public const val MAXIMUM_MESSAGE_BYTES: Int = 16 * 1_024 * 1_024
    }
}

/** Streaming execution events shared by unary and long-lived protocols. */
public sealed interface ApiStudioProtocolExecutionEvent {
    public data class Started(
        public val summary: String,
        /** Server-selected application protocol, when the transport negotiated one. */
        public val negotiatedApplicationProtocol: String? = null,
    ) : ApiStudioProtocolExecutionEvent
    public data class Message(public val message: ApiStudioProtocolMessage) : ApiStudioProtocolExecutionEvent
    public data class Completed(
        public val statusCode: String,
        public val statusMessage: String?,
        public val actualProtocol: String,
        public val trailers: List<Pair<String, String>> = emptyList(),
    ) : ApiStudioProtocolExecutionEvent

    public data class Failed(
        public val code: String,
        public val message: String,
        public val retryable: Boolean,
        public val actualProtocol: String? = null,
        public val trailers: List<Pair<String, String>> = emptyList(),
    ) : ApiStudioProtocolExecutionEvent
}

/** Additive execution boundary implemented by one native protocol engine. */
public interface ApiStudioProtocolExecutor {
    public val kind: RequestKindId
    public fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent>
}

/** One protocol-neutral outbound message sent after an interactive session is open. */
public data class ApiStudioProtocolOutboundMessage(
    public val displayText: String,
    public val contentType: String = "application/json",
) {
    init {
        require(contentType.isNotBlank()) { "Outbound protocol message content type must not be blank." }
        require(displayText.encodeToByteArray().size <= ApiStudioProtocolMessage.MAXIMUM_MESSAGE_BYTES) {
            "Outbound protocol message is too large."
        }
    }
}

/** Live client-streaming or bidirectional execution owned by a protocol engine. */
public interface ApiStudioProtocolExecutionSession {
    public val events: Flow<ApiStudioProtocolExecutionEvent>

    public suspend fun send(message: ApiStudioProtocolOutboundMessage): Result<Unit>

    public suspend fun halfClose(): Result<Unit>

    public fun cancel()
}

/** Additive interactive-session boundary for protocols that support live outbound messages. */
public interface ApiStudioProtocolSessionExecutor {
    public val kind: RequestKindId

    public fun open(command: ApiStudioProtocolExecutionCommand): Result<ApiStudioProtocolExecutionSession>
}

/** Immutable interactive-session registry assembled by the desktop product. */
public class ApiStudioProtocolSessionExecutorRegistry(
    executors: List<ApiStudioProtocolSessionExecutor> = emptyList(),
) {
    private val byKind = executors.associateBy(ApiStudioProtocolSessionExecutor::kind).also { indexed ->
        require(indexed.size == executors.size) { "API Studio protocol session executor kinds must be unique." }
    }

    public fun open(command: ApiStudioProtocolExecutionCommand): Result<ApiStudioProtocolExecutionSession> =
        executor(command.document.kind).open(command)

    private fun executor(kind: RequestKindId): ApiStudioProtocolSessionExecutor =
        requireNotNull(byKind[kind]) {
            "No API Studio interactive executor is registered for '${kind.value}'."
        }
}

/** Immutable execution registry assembled by the desktop product. */
public class ApiStudioProtocolExecutorRegistry(
    executors: List<ApiStudioProtocolExecutor> = emptyList(),
) {
    private val byKind = executors.associateBy(ApiStudioProtocolExecutor::kind).also { indexed ->
        require(indexed.size == executors.size) { "API Studio protocol executor kinds must be unique." }
    }

    public fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> {
        val executor = requireNotNull(byKind[command.document.kind]) {
            "No API Studio executor is registered for '${command.document.kind.value}'."
        }
        return executor.execute(command)
    }
}
