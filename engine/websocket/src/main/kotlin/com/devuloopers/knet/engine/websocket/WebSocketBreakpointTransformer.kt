package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformerFactory
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates a bounded WebSocket message gate only when an enabled rule can match the handshake. */
class WebSocketBreakpointTransformerFactory(
    private val gate: ProtocolMessageBreakpointGate,
    private val scope: CoroutineScope,
    semanticLayers: List<WebSocketSemanticBreakpointLayer> = emptyList(),
    private val maximumEditableMessageBytes: Int = DEFAULT_MAXIMUM_EDITABLE_MESSAGE_BYTES,
) : ProxyDuplexTransformerFactory {
    private val semanticLayers = semanticLayers.sortedWith(
        compareByDescending<WebSocketSemanticBreakpointLayer>(WebSocketSemanticBreakpointLayer::priority)
            .thenBy { layer -> layer.protocolId.value },
    ).also { layers ->
        require(layers.distinctBy(WebSocketSemanticBreakpointLayer::protocolId).size == layers.size) {
            "WebSocket semantic breakpoint layer IDs must be unique."
        }
    }

    init {
        require(maximumEditableMessageBytes > 0) { "Maximum editable WebSocket bytes must be positive." }
    }

    override fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyDuplexTransformer? {
        if (capture == null || !WebSocketProtocol.isHandshake(request)) return null
        val potentialRoute = semanticLayers
            .filter { layer -> layer.mayApply(request) }
            .map(WebSocketSemanticBreakpointLayer::protocolId)
            .plus(WebSocketBreakpointProtocol.id)
        val interceptClient = gate.mayInterceptMessage(
            request,
            potentialRoute,
            TrafficDirection.CLIENT_TO_SERVER,
        )
        val interceptServer = gate.mayInterceptMessage(
            request,
            potentialRoute,
            TrafficDirection.SERVER_TO_CLIENT,
        )
        if (!interceptClient && !interceptServer) return null
        return WebSocketBreakpointTransformer(
            request = request,
            exchangeCapture = capture,
            gate = gate,
            scope = scope,
            semanticLayers = semanticLayers,
            interceptClient = interceptClient,
            interceptServer = interceptServer,
            maximumEditableMessageBytes = maximumEditableMessageBytes,
        )
    }

    private companion object {
        const val DEFAULT_MAXIMUM_EDITABLE_MESSAGE_BYTES: Int = 10 * 1_024 * 1_024
    }
}

private class WebSocketBreakpointTransformer(
    request: HttpRequestSnapshot,
    exchangeCapture: ProxyExchangeCapture,
    private val gate: ProtocolMessageBreakpointGate,
    private val scope: CoroutineScope,
    private val semanticLayers: List<WebSocketSemanticBreakpointLayer>,
    interceptClient: Boolean,
    interceptServer: Boolean,
    maximumEditableMessageBytes: Int,
) : ProxyDuplexTransformer {
    private val cancelled = AtomicBoolean(false)
    private var compressionAccepted = false
    private var negotiatedSubprotocol: String? = null
    private val clientGate = WebSocketDirectionGate(
        request,
        exchangeCapture,
        gate,
        TrafficDirection.CLIENT_TO_SERVER,
        interceptClient,
        maximumEditableMessageBytes,
        semanticLayers,
    )
    private val serverGate = WebSocketDirectionGate(
        request,
        exchangeCapture,
        gate,
        TrafficDirection.SERVER_TO_CLIENT,
        interceptServer,
        maximumEditableMessageBytes,
        semanticLayers,
    )

    override fun onEstablished(response: ResponseHead, occurredAtEpochMillis: Long) {
        negotiatedSubprotocol = WebSocketProtocol.header(response.headers, SUBPROTOCOL)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        compressionAccepted = WebSocketProtocol.header(response.headers, EXTENSIONS)
            ?.contains(PER_MESSAGE_DEFLATE, ignoreCase = true) == true
        clientGate.establish(compressionAccepted, negotiatedSubprotocol)
        serverGate.establish(compressionAccepted, negotiatedSubprotocol)
    }

    override fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyDuplexTransformResult> {
        if (cancelled.get()) {
            return CompletableFuture.completedFuture(ProxyDuplexTransformResult.DropConnection(CANCELLED))
        }
        val future = CompletableFuture<ProxyDuplexTransformResult>()
        scope.launch {
            val result = runCatching {
                directionGate(direction).transform(payload, occurredAtEpochMillis)
            }.getOrElse {
                ProxyDuplexTransformResult.DropConnection(TRANSFORM_FAILED)
            }
            future.complete(result)
        }
        return future
    }

    override fun cancel(errorCode: String?) {
        if (!cancelled.compareAndSet(false, true)) return
        clientGate.cancel()
        serverGate.cancel()
        gate.cancelProtocolMessages(clientGate.exchangeId)
    }

    private fun directionGate(direction: TrafficDirection): WebSocketDirectionGate = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> clientGate
        TrafficDirection.SERVER_TO_CLIENT -> serverGate
    }

    private companion object {
        const val CANCELLED: String = "websocket_breakpoint_cancelled"
        const val EXTENSIONS: String = "sec-websocket-extensions"
        const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
        const val SUBPROTOCOL: String = "sec-websocket-protocol"
        const val TRANSFORM_FAILED: String = "websocket_breakpoint_transform_failed"
    }
}

/** Direction-confined frame collector used only while at least one message rule is active. */
@OptIn(ExperimentalUuidApi::class)
private class WebSocketDirectionGate(
    private val request: HttpRequestSnapshot,
    exchangeCapture: ProxyExchangeCapture,
    private val gate: ProtocolMessageBreakpointGate,
    private val direction: TrafficDirection,
    private val enabled: Boolean,
    private val maximumEditableMessageBytes: Int,
    private val semanticLayers: List<WebSocketSemanticBreakpointLayer>,
) {
    val exchangeId = exchangeCapture.exchangeId
    private var decoder: WebSocketFrameDecoder? = null
    private var initialFrame: WebSocketFrame? = null
    private val heldFrames = mutableListOf<HeldFrame>()
    private val messagePayload = ByteArrayOutputStream()
    private var sequence: Long = 0L
    private var cancelled = false
    private var negotiatedSubprotocol: String? = null

    fun establish(compressionAccepted: Boolean, negotiatedSubprotocol: String?) {
        this.negotiatedSubprotocol = negotiatedSubprotocol
        decoder = WebSocketFrameDecoder(
            expectsMaskedFrames = direction == TrafficDirection.CLIENT_TO_SERVER,
            permitsCompression = compressionAccepted,
            maximumFrameBytes = maximumEditableMessageBytes,
        )
    }

    suspend fun transform(input: ByteArray, occurredAtEpochMillis: Long): ProxyDuplexTransformResult {
        if (cancelled) return ProxyDuplexTransformResult.DropConnection(CANCELLED)
        if (!enabled) return ProxyDuplexTransformResult.Forward(input)
        val selectedDecoder = decoder ?: return ProxyDuplexTransformResult.DropConnection(NOT_ESTABLISHED)
        return when (val result = selectedDecoder.accept(input)) {
            is WebSocketDecodeResult.Failure -> ProxyDuplexTransformResult.DropConnection(result.errorCode)
            is WebSocketDecodeResult.Frames -> {
                val output = ByteArrayOutputStream(input.size)
                for (frame in result.values) {
                    when (val frameResult = acceptFrame(frame, occurredAtEpochMillis)) {
                        is ProxyDuplexTransformResult.Forward -> output.write(frameResult.copyPayload())
                        is ProxyDuplexTransformResult.DropConnection -> return frameResult
                    }
                }
                ProxyDuplexTransformResult.Forward(output.toByteArray())
            }
        }
    }

    fun cancel() {
        cancelled = true
        decoder?.clear()
        resetMessage()
    }

    private suspend fun acceptFrame(
        frame: WebSocketFrame,
        occurredAtEpochMillis: Long,
    ): ProxyDuplexTransformResult {
        if (frame.opcode.isControl) {
            val resolved = resolveSingleFrame(frame, occurredAtEpochMillis)
            if (initialFrame == null || resolved is ProxyDuplexTransformResult.DropConnection) return resolved
            heldFrames += HeldFrame(frame = frame, forwardedWireBytes = resolved.copyForwardedPayload())
            return ProxyDuplexTransformResult.Forward(ByteArray(0))
        }
        when (frame.opcode) {
            WebSocketOpcode.TEXT, WebSocketOpcode.BINARY -> {
                if (initialFrame != null) return ProxyDuplexTransformResult.DropConnection(UNEXPECTED_DATA)
                initialFrame = frame
            }
            WebSocketOpcode.CONTINUATION -> if (initialFrame == null) {
                return ProxyDuplexTransformResult.DropConnection(UNEXPECTED_CONTINUATION)
            }
            else -> Unit
        }
        heldFrames += HeldFrame(frame = frame, forwardedWireBytes = null)
        messagePayload.write(frame.payload)
        if (messagePayload.size() > maximumEditableMessageBytes) {
            return ProxyDuplexTransformResult.DropConnection(MESSAGE_LIMIT)
        }
        if (!frame.final) return ProxyDuplexTransformResult.Forward(ByteArray(0))

        val first = checkNotNull(initialFrame)
        val original = messagePayload.toByteArray()
        val originalWire = heldFrames.concatenateWireBytes()
        val decision = intercept(first, original, occurredAtEpochMillis)
        val result = when (decision) {
            ProtocolMessageBreakpointDecision.ContinueUnchanged -> ProxyDuplexTransformResult.Forward(originalWire)
            is ProtocolMessageBreakpointDecision.Replace -> {
                if (first.compressed) {
                    ProxyDuplexTransformResult.DropConnection(COMPRESSED_EDIT_UNSUPPORTED)
                } else {
                    ProxyDuplexTransformResult.Forward(
                        heldFrames.rebuildWithReplacement(decision.body.copyBytes()),
                    )
                }
            }
            ProtocolMessageBreakpointDecision.DropStream ->
                ProxyDuplexTransformResult.DropConnection(MESSAGE_DROPPED)
        }
        resetMessage()
        return result
    }

    private suspend fun resolveSingleFrame(
        frame: WebSocketFrame,
        occurredAtEpochMillis: Long,
    ): ProxyDuplexTransformResult = when (val decision = intercept(frame, frame.payload, occurredAtEpochMillis)) {
        ProtocolMessageBreakpointDecision.ContinueUnchanged ->
            ProxyDuplexTransformResult.Forward(frame.originalWireBytes)
        is ProtocolMessageBreakpointDecision.Replace -> ProxyDuplexTransformResult.Forward(
            WebSocketFrameDecoder.encode(
                opcode = frame.opcode,
                payload = decision.body.copyBytes(),
                maskingKey = frame.maskingKey,
            ),
        )
        ProtocolMessageBreakpointDecision.DropStream ->
            ProxyDuplexTransformResult.DropConnection(MESSAGE_DROPPED)
    }

    private suspend fun intercept(
        frame: WebSocketFrame,
        body: ByteArray,
        occurredAtEpochMillis: Long,
    ): ProtocolMessageBreakpointDecision {
        val kind = frame.opcode.messageKind()
        val protocolRoute = semanticLayers
            .filter { layer ->
                layer.applies(
                    request = request,
                    negotiatedSubprotocol = negotiatedSubprotocol,
                    kind = kind,
                    direction = direction,
                    payload = body,
                )
            }
            .map(WebSocketSemanticBreakpointLayer::protocolId)
            .plus(WebSocketBreakpointProtocol.id)
        return gate.interceptMessage(
            ProtocolMessageBreakpointCandidate(
            exchangeId = exchangeId,
            messageId = ProtocolMessageId(Uuid.random().toString()),
            protocolRoute = protocolRoute,
            kind = kind,
            request = request,
            negotiatedSubprotocol = negotiatedSubprotocol,
            direction = direction,
            sequence = ++sequence,
            declaredBytes = body.size.toLong(),
            compressed = frame.compressed,
            compressionEncoding = PER_MESSAGE_DEFLATE.takeIf { frame.compressed },
            body = BreakpointBody(body),
            retainedTransportBytes = if (frame.opcode.isControl) {
                frame.originalWireBytes.size.toLong()
            } else {
                heldFrames.asSequence()
                    .filter { held -> !held.frame.opcode.isControl }
                    .sumOf { held -> held.frame.originalWireBytes.size.toLong() }
            },
            startedAtEpochMillis = occurredAtEpochMillis,
        ),
        )
    }

    private fun resetMessage() {
        initialFrame = null
        heldFrames.clear()
        messagePayload.reset()
    }

    private companion object {
        const val CANCELLED: String = "websocket_breakpoint_cancelled"
        const val COMPRESSED_EDIT_UNSUPPORTED: String = "websocket_compressed_edit_unsupported"
        const val MESSAGE_DROPPED: String = "websocket_message_dropped"
        const val MESSAGE_LIMIT: String = "websocket_editable_message_limit"
        const val NOT_ESTABLISHED: String = "websocket_breakpoint_not_established"
        const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
        const val UNEXPECTED_CONTINUATION: String = "websocket_unexpected_continuation"
        const val UNEXPECTED_DATA: String = "websocket_unexpected_data_frame"
    }
}

private data class HeldFrame(
    val frame: WebSocketFrame,
    val forwardedWireBytes: ByteArray?,
)

private fun ProxyDuplexTransformResult.copyForwardedPayload(): ByteArray = when (this) {
    is ProxyDuplexTransformResult.Forward -> copyPayload()
    is ProxyDuplexTransformResult.DropConnection -> error("A dropped frame has no forwarded payload.")
}

private fun List<HeldFrame>.concatenateWireBytes(): ByteArray {
    val output = ByteArrayOutputStream(sumOf { held ->
        held.forwardedWireBytes?.size ?: held.frame.originalWireBytes.size
    })
    forEach { held -> output.write(held.forwardedWireBytes ?: held.frame.originalWireBytes) }
    return output.toByteArray()
}

/** Re-fragments edited data while retaining control-frame order and each client masking key. */
private fun List<HeldFrame>.rebuildWithReplacement(replacement: ByteArray): ByteArray {
    val dataFrames = filterNot { held -> held.frame.opcode.isControl }
    require(dataFrames.isNotEmpty()) { "A WebSocket message requires at least one data frame." }
    var offset = 0
    var dataFrameIndex = 0
    val rebuilt = associateWith { held ->
        if (held.frame.opcode.isControl) {
            held.forwardedWireBytes ?: held.frame.originalWireBytes
        } else {
            val lastDataFrame = dataFrameIndex == dataFrames.lastIndex
            val remaining = replacement.size - offset
            val length = if (lastDataFrame) remaining else minOf(held.frame.payload.size, remaining)
            val payload = replacement.copyOfRange(offset, offset + length)
            offset += length
            val encoded = WebSocketFrameDecoder.encode(
                opcode = if (dataFrameIndex == 0) held.frame.opcode else WebSocketOpcode.CONTINUATION,
                payload = payload,
                final = lastDataFrame,
                compressed = false,
                maskingKey = held.frame.maskingKey,
            )
            dataFrameIndex++
            encoded
        }
    }
    val output = ByteArrayOutputStream(rebuilt.values.sumOf(ByteArray::size))
    forEach { held -> output.write(checkNotNull(rebuilt[held])) }
    return output.toByteArray()
}
