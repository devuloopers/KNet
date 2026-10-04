package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionMessageInspectionInput
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformerFactory
import com.devuloopers.knet.engine.simulator.NetworkConditionDirection
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import com.devuloopers.knet.engine.simulator.NetworkConditionProtocolScheduleResult
import com.devuloopers.knet.engine.simulator.NetworkConditionProtocolScheduler
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationCode
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates the shared logical-message Network Conditions shaper for every supported WebSocket handshake.
 *
 * The transformer resolves both generic WebSocket rules and negotiated GraphQL WebSocket rules without applying
 * conditions twice. The GraphQL WebSocket module retains a source-compatible alias for its earlier factory name.
 */
class WebSocketNetworkConditionTransformerFactory(
    private val engine: NetworkConditionEngine,
    private val semanticResolver: NetworkConditionSemanticResolver,
    private val scope: CoroutineScope,
    private val maximumMessageBytes: Int = DEFAULT_MAXIMUM_MESSAGE_BYTES,
) : ProxyDuplexTransformerFactory {
    init {
        require(maximumMessageBytes > 0) { "Maximum WebSocket condition message bytes must be positive." }
    }

    override fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyDuplexTransformer? {
        if (!WebSocketProtocol.isHandshake(request)) return null
        val destination = request.destination() ?: return null
        return WebSocketNetworkConditionTransformer(
            request = request,
            exchangeId = capture?.exchangeId ?: newExchangeId(),
            host = destination.first,
            port = destination.second,
            engine = engine,
            semanticResolver = semanticResolver,
            scope = scope,
            maximumMessageBytes = maximumMessageBytes,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newExchangeId(): ExchangeId = ExchangeId(Uuid.random().toString())

    private companion object {
        const val DEFAULT_MAXIMUM_MESSAGE_BYTES: Int = 10 * 1_024 * 1_024
    }
}

private class WebSocketNetworkConditionTransformer(
    private val request: HttpRequestSnapshot,
    private val exchangeId: ExchangeId,
    private val host: String,
    private val port: Int,
    private val engine: NetworkConditionEngine,
    private val semanticResolver: NetworkConditionSemanticResolver,
    private val scope: CoroutineScope,
    maximumMessageBytes: Int,
) : ProxyDuplexTransformer {
    private val scheduler = NetworkConditionProtocolScheduler(engine)
    private val flow = scheduler.openFlow()
    private val cancelled = AtomicBoolean(false)
    private val pendingTimeouts = ConcurrentHashMap.newKeySet<CompletableFuture<ProxyDuplexTransformResult>>()
    private val activeJobs = ConcurrentHashMap<CompletableFuture<ProxyDuplexTransformResult>, Job>()
    private var negotiatedSubprotocol: String? = null
    private val client = DirectionShaper(TrafficDirection.CLIENT_TO_SERVER, maximumMessageBytes)
    private val server = DirectionShaper(TrafficDirection.SERVER_TO_CLIENT, maximumMessageBytes)

    override val handlesNetworkConditions: Boolean = true

    override fun onEstablished(response: ResponseHead, occurredAtEpochMillis: Long) {
        negotiatedSubprotocol = WebSocketProtocol.header(response.headers, SUBPROTOCOL)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val compression = WebSocketPerMessageDeflateNegotiation.fromResponseHeaders(response.headers)
        client.establish(compression, negotiatedSubprotocol)
        server.establish(compression, negotiatedSubprotocol)
    }

    override fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyDuplexTransformResult> {
        if (cancelled.get()) return CompletableFuture.completedFuture(drop(CANCELLED))
        val future = CompletableFuture<ProxyDuplexTransformResult>()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = try {
                directionShaper(direction).transform(payload)
            } catch (_: CancellationException) {
                future.complete(drop(CANCELLED))
                return@launch
            } catch (_: Throwable) {
                drop(TRANSFORM_FAILED)
            } finally {
                activeJobs.remove(future)
            }
            if (result == null) {
                pendingTimeouts += future
                if (cancelled.get() && pendingTimeouts.remove(future)) future.complete(drop(CANCELLED))
            } else {
                future.complete(result)
            }
        }
        activeJobs[future] = job
        if (cancelled.get() && activeJobs.remove(future, job)) {
            job.cancel()
            future.complete(drop(CANCELLED))
        } else {
            job.start()
        }
        return future
    }

    override fun cancel(reason: TrafficTerminationReason?) {
        if (!cancelled.compareAndSet(false, true)) return
        client.cancel()
        server.cancel()
        semanticResolver.releaseMessages(exchangeId)
        activeJobs.forEach { (future, job) ->
            job.cancel()
            future.complete(drop(CANCELLED))
        }
        activeJobs.clear()
        pendingTimeouts.forEach { future -> future.complete(drop(CANCELLED)) }
        pendingTimeouts.clear()
        flow.close()
    }

    private fun directionShaper(direction: TrafficDirection): DirectionShaper = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> client
        TrafficDirection.SERVER_TO_CLIENT -> server
    }

    private inner class DirectionShaper(
        private val direction: TrafficDirection,
        private val maximumMessageBytes: Int,
    ) {
        private var decoder: WebSocketFrameDecoder? = null
        private var firstFrame: WebSocketFrame? = null
        private val heldFrames = mutableListOf<WebSocketFrame>()
        private val messagePayload = ByteArrayOutputStream()
        private var sequence: Long = 0L
        private var inflater: WebSocketPerMessageDeflateDecoder? = null
        private var selectedSubprotocol: String? = null

        fun establish(
            compression: WebSocketPerMessageDeflateNegotiation?,
            subprotocol: String?,
        ) {
            selectedSubprotocol = subprotocol
            decoder = WebSocketFrameDecoder(
                expectsMaskedFrames = direction == TrafficDirection.CLIENT_TO_SERVER,
                permitsCompression = compression != null,
                maximumFrameBytes = maximumMessageBytes,
            )
            inflater = compression?.let { negotiation ->
                WebSocketPerMessageDeflateDecoder(
                    noContextTakeover = negotiation.noContextTakeover(direction),
                    maximumOutputBytes = maximumMessageBytes,
                )
            }
        }

        suspend fun transform(input: ByteArray): ProxyDuplexTransformResult? {
            val selectedDecoder = decoder ?: return drop(NOT_ESTABLISHED)
            return when (val decoded = selectedDecoder.accept(input)) {
                is WebSocketDecodeResult.Failure -> drop(decoded.errorCode)
                is WebSocketDecodeResult.Frames -> {
                    val output = ByteArrayOutputStream(input.size)
                    for (frame in decoded.values) {
                        val result = accept(frame) ?: return null
                        when (result) {
                            is ProxyDuplexTransformResult.DropConnection -> return result
                            is ProxyDuplexTransformResult.Forward -> output.write(result.copyPayload())
                        }
                    }
                    ProxyDuplexTransformResult.Forward(output.toByteArray())
                }
            }
        }

        fun cancel() {
            decoder?.clear()
            inflater?.close()
            resetMessage()
        }

        private suspend fun accept(frame: WebSocketFrame): ProxyDuplexTransformResult? {
            if (frame.opcode.isControl) {
                return shape(frame.opcode.messageKind(), frame.payload, frame.originalWireBytes, frame.compressed)
            }
            when (frame.opcode) {
                WebSocketOpcode.TEXT, WebSocketOpcode.BINARY -> {
                    if (firstFrame != null) return drop(UNEXPECTED_DATA)
                    firstFrame = frame
                }
                WebSocketOpcode.CONTINUATION -> if (firstFrame == null) return drop(UNEXPECTED_CONTINUATION)
                else -> Unit
            }
            heldFrames += frame
            messagePayload.write(frame.payload)
            if (messagePayload.size() > maximumMessageBytes) return drop(MESSAGE_LIMIT)
            if (!frame.final) return ProxyDuplexTransformResult.Forward(ByteArray(0))

            val first = checkNotNull(firstFrame)
            val wirePayload = messagePayload.toByteArray()
            val logicalPayload = if (first.compressed) {
                when (val inflated = inflater?.decode(wirePayload)) {
                    is WebSocketInflateResult.Success -> inflated.payload
                    is WebSocketInflateResult.Failure -> return drop(inflated.errorCode)
                    null -> return drop(INVALID_COMPRESSED_PAYLOAD)
                }
            } else {
                wirePayload
            }
            val originalWire = heldFrames.concatenateWireBytes()
            val result = shape(first.opcode.messageKind(), logicalPayload, originalWire, first.compressed)
            resetMessage()
            return result
        }

        @OptIn(ExperimentalUuidApi::class)
        private suspend fun shape(
            kind: ProtocolMessageKind,
            logicalPayload: ByteArray,
            wirePayload: ByteArray,
            compressed: Boolean,
        ): ProxyDuplexTransformResult? {
            val messageSequence = ++sequence
            val effective = semanticResolver.resolveMessage(
                configuration = engine.configurationSnapshot(),
                host = host,
                port = port,
                input = NetworkConditionMessageInspectionInput(
                    exchangeId = exchangeId,
                    request = request,
                    messageId = ProtocolMessageId(Uuid.random().toString()),
                    kind = kind,
                    negotiatedSubprotocol = selectedSubprotocol,
                    direction = direction,
                    sequence = messageSequence,
                    declaredBytes = logicalPayload.size.toLong(),
                    compressed = compressed,
                    compressionEncoding = PER_MESSAGE_DEFLATE.takeIf { compressed },
                    body = NetworkConditionBody(logicalPayload),
                ),
                alwaysObserveProtocolId = NetworkConditionProtocolId.GRAPHQL_WEBSOCKET.takeIf {
                    selectedSubprotocol == GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
                },
            )
            return when (
                scheduler.schedule(
                    effective = effective,
                    direction = direction.conditionDirection(),
                    bytes = wirePayload.size,
                    sequence = messageSequence,
                )
            ) {
                NetworkConditionProtocolScheduleResult.Forward -> ProxyDuplexTransformResult.Forward(wirePayload)
                is NetworkConditionProtocolScheduleResult.DropConnection -> drop(CONDITION_FAULT)
                NetworkConditionProtocolScheduleResult.Timeout -> null
            }
        }

        private fun resetMessage() {
            firstFrame = null
            heldFrames.clear()
            messagePayload.reset()
        }
    }

    private companion object {
        const val CANCELLED: String = "websocket_condition_cancelled"
        const val CONDITION_FAULT: String = "websocket_condition_fault"
        const val INVALID_COMPRESSED_PAYLOAD: String = "websocket_condition_invalid_compression"
        const val MESSAGE_LIMIT: String = "websocket_condition_message_limit"
        const val NOT_ESTABLISHED: String = "websocket_condition_not_established"
        const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
        const val GRAPHQL_TRANSPORT_WS_SUBPROTOCOL: String = "graphql-transport-ws"
        const val SUBPROTOCOL: String = "sec-websocket-protocol"
        const val TRANSFORM_FAILED: String = "websocket_condition_transform_failed"
        const val UNEXPECTED_CONTINUATION: String = "websocket_condition_unexpected_continuation"
        const val UNEXPECTED_DATA: String = "websocket_condition_unexpected_data"
    }
}

private fun HttpRequestSnapshot.destination(): Pair<String, Int>? = when (val target = head.target) {
    is RequestTarget.Absolute -> target.authority.host to (
        target.authority.port ?: if (target.scheme.token.equals("https", ignoreCase = true)) 443 else 80
    )
    is RequestTarget.AuthorityForm -> target.authority.port?.let { port -> target.authority.host to port }
    else -> null
}

private fun TrafficDirection.conditionDirection(): NetworkConditionDirection = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> NetworkConditionDirection.UPLOAD
    TrafficDirection.SERVER_TO_CLIENT -> NetworkConditionDirection.DOWNLOAD
}

private fun List<WebSocketFrame>.concatenateWireBytes(): ByteArray {
    val output = ByteArrayOutputStream(sumOf { frame -> frame.originalWireBytes.size })
    forEach { frame -> output.write(frame.originalWireBytes) }
    return output.toByteArray()
}

private fun drop(code: String): ProxyDuplexTransformResult.DropConnection =
    ProxyDuplexTransformResult.DropConnection(
        TrafficTerminationReason.Protocol(
            protocol = MessageProtocolId("websocket"),
            code = TrafficTerminationCode(code),
        ),
    )
