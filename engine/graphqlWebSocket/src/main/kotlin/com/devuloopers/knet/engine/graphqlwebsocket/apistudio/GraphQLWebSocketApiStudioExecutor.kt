package com.devuloopers.knet.engine.graphqlwebsocket.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutor
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOutboundMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSessionExecutor
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketMessageType
import com.devuloopers.knet.engine.graphqlwebsocket.session.GraphQLWebSocketSessionStateMachine
import com.devuloopers.knet.engine.websocket.WebSocketApiStudioExecutor
import com.devuloopers.knet.engine.websocket.WebSocketRequestDraft
import com.devuloopers.knet.engine.websocket.WebSocketRequestDraftCodec
import com.devuloopers.knet.traffic.model.TrafficDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Modern GraphQL subscription executor layered over the existing interactive WebSocket transport. */
class GraphQLWebSocketApiStudioExecutor(
    private val draftCodec: GraphQLWebSocketRequestDraftCodec,
    private val webSocketDraftCodec: WebSocketRequestDraftCodec,
    private val webSocketExecutor: WebSocketApiStudioExecutor,
    private val envelopeParser: GraphQLWebSocketEnvelopeParser,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : ApiStudioProtocolExecutor, ApiStudioProtocolSessionExecutor {
    override val kind: RequestKindId = RequestKindId.GRAPHQL_WEBSOCKET

    override fun open(command: ApiStudioProtocolExecutionCommand): Result<ApiStudioProtocolExecutionSession> =
        draftCodec.decode(command.document).mapCatching { draft ->
            val transportDocument = webSocketDraftCodec.encode(
                id = command.document.id,
                name = command.document.name,
                draft = WebSocketRequestDraft(
                    url = draft.url,
                    subprotocols = listOf(GRAPHQL_TRANSPORT_WS_SUBPROTOCOL),
                    headers = draft.headers,
                    connectTimeoutMillis = draft.connectTimeoutMillis,
                ),
            )
            val rawSession = webSocketExecutor.open(command.copy(document = transportDocument)).getOrThrow()
            GraphQLExecutionSession(draft, draftCodec, envelopeParser, rawSession, scope)
        }

    override fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> =
        callbackFlow {
            val session = open(command).getOrElse { error ->
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "invalid_graphql_websocket_document",
                    message = error.message ?: "The GraphQL WebSocket document is invalid.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val relay = launch {
                session.events.collect { event ->
                    trySend(event)
                    if (event is ApiStudioProtocolExecutionEvent.Completed ||
                        event is ApiStudioProtocolExecutionEvent.Failed
                    ) close()
                }
            }
            awaitClose {
                relay.cancel()
                session.cancel()
            }
        }
}

/** One operation-focused GraphQL session backed by a long-lived raw WebSocket execution session. */
private class GraphQLExecutionSession(
    private val draft: GraphQLWebSocketRequestDraft,
    private val draftCodec: GraphQLWebSocketRequestDraftCodec,
    private val parser: GraphQLWebSocketEnvelopeParser,
    private val rawSession: ApiStudioProtocolExecutionSession,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : ApiStudioProtocolExecutionSession {
    private val eventFlow = MutableSharedFlow<ApiStudioProtocolExecutionEvent>(
        replay = 1,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val terminal = MutableStateFlow(false)
    private val stateMachine = GraphQLWebSocketSessionStateMachine(parser)
    private var acknowledgementDeadline: Job? = null
    private var subscribeSent = false
    private var operationTerminal = false
    private val relayJob = scope.launch {
        rawSession.events.collect(::acceptRawEvent)
    }

    override val events: Flow<ApiStudioProtocolExecutionEvent> = eventFlow

    override suspend fun send(message: ApiStudioProtocolOutboundMessage): Result<Unit> =
        Result.failure(UnsupportedOperationException("Use the authored GraphQL operation controls."))

    override suspend fun halfClose(): Result<Unit> = runCatching {
        if (!operationTerminal && stateMachine.isAcknowledged) {
            rawSession.send(textMessage(completeEnvelope())).getOrThrow()
            operationTerminal = true
        }
        rawSession.halfClose().getOrThrow()
    }

    override fun cancel() {
        if (!terminal.compareAndSet(expect = false, update = true)) return
        acknowledgementDeadline?.cancel()
        stateMachine.close()
        relayJob.cancel()
        rawSession.cancel()
        eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Failed(
            code = "graphql_websocket_cancelled",
            message = "GraphQL WebSocket execution was cancelled.",
            retryable = false,
            actualProtocol = GRAPHQL_TRANSPORT_WS_SUBPROTOCOL,
        ))
    }

    private suspend fun acceptRawEvent(event: ApiStudioProtocolExecutionEvent) {
        if (terminal.value) return
        when (event) {
            is ApiStudioProtocolExecutionEvent.Started -> acceptStarted(event)
            is ApiStudioProtocolExecutionEvent.Message -> acceptMessage(event)
            is ApiStudioProtocolExecutionEvent.Completed -> finish(event)
            is ApiStudioProtocolExecutionEvent.Failed -> fail(event.code, event.message, event.retryable)
        }
    }

    private suspend fun acceptStarted(event: ApiStudioProtocolExecutionEvent.Started) {
        if (event.negotiatedApplicationProtocol != GRAPHQL_TRANSPORT_WS_SUBPROTOCOL) {
            fail(
                code = "graphql_websocket_subprotocol_not_negotiated",
                message = "The server did not negotiate graphql-transport-ws.",
                retryable = false,
            )
            return
        }
        eventFlow.emit(ApiStudioProtocolExecutionEvent.Started(
            summary = "GraphQL WebSocket ${draft.url}",
            negotiatedApplicationProtocol = GRAPHQL_TRANSPORT_WS_SUBPROTOCOL,
        ))
        rawSession.send(textMessage(draftCodec.run { draft.connectionInitEnvelope().toString() })).getOrElse { error ->
            fail("graphql_websocket_init_failed", error.message ?: "Unable to initialize the connection.", true)
            return
        }
        acknowledgementDeadline = scope.launch {
            delay(draft.acknowledgementTimeoutMillis)
            if (!stateMachine.isAcknowledged && !terminal.value) {
                fail(
                    code = "graphql_websocket_ack_timeout",
                    message = "The server did not acknowledge the GraphQL WebSocket connection in time.",
                    retryable = true,
                )
            }
        }
    }

    private suspend fun acceptMessage(event: ApiStudioProtocolExecutionEvent.Message) {
        val envelope = parser.parse(event.message.copyPayload())
        if (envelope == null) {
            fail("graphql_websocket_invalid_message", "Received an invalid GraphQL WebSocket message.", false)
            return
        }
        val direction = when (event.message.direction) {
            ApiStudioProtocolMessageDirection.OUTBOUND -> TrafficDirection.CLIENT_TO_SERVER
            ApiStudioProtocolMessageDirection.INBOUND -> TrafficDirection.SERVER_TO_CLIENT
        }
        runCatching { stateMachine.accept(direction, envelope) }.getOrElse { error ->
            fail("graphql_websocket_protocol_error", error.message ?: "GraphQL WebSocket protocol error.", false)
            return
        }
        eventFlow.emit(event)
        if (envelope.type == GraphQLWebSocketMessageType.CONNECTION_ACK && !subscribeSent) {
            acknowledgementDeadline?.cancel()
            subscribeSent = true
            rawSession.send(textMessage(draftCodec.run { draft.subscribeEnvelope().toString() })).getOrElse { error ->
                fail("graphql_websocket_subscribe_failed", error.message ?: "Unable to start the operation.", true)
                return
            }
        }
        if (direction == TrafficDirection.SERVER_TO_CLIENT &&
            envelope.type in setOf(GraphQLWebSocketMessageType.ERROR, GraphQLWebSocketMessageType.COMPLETE)
        ) {
            operationTerminal = true
            rawSession.halfClose()
        }
    }

    private fun finish(event: ApiStudioProtocolExecutionEvent.Completed) {
        if (!terminal.compareAndSet(expect = false, update = true)) return
        acknowledgementDeadline?.cancel()
        stateMachine.close()
        eventFlow.tryEmit(event.copy(actualProtocol = GRAPHQL_TRANSPORT_WS_SUBPROTOCOL))
    }

    private fun fail(code: String, message: String, retryable: Boolean) {
        if (!terminal.compareAndSet(expect = false, update = true)) return
        acknowledgementDeadline?.cancel()
        stateMachine.close()
        rawSession.cancel()
        eventFlow.tryEmit(ApiStudioProtocolExecutionEvent.Failed(
            code = code,
            message = message,
            retryable = retryable,
            actualProtocol = GRAPHQL_TRANSPORT_WS_SUBPROTOCOL,
        ))
    }

    private fun completeEnvelope(): String = buildJsonObject {
        put("id", draft.operationId)
        put("type", "complete")
    }.toString()

    private fun textMessage(text: String): ApiStudioProtocolOutboundMessage =
        ApiStudioProtocolOutboundMessage(text, "text/plain; charset=UTF-8")
}
