package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspectorFactory
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates a WebSocket frame observer only for a valid RFC 6455 HTTP/1.1 handshake. */
class WebSocketDuplexInspectorFactory(
    private val maximumFrameBytes: Int = WebSocketFrameDecoder.DEFAULT_MAXIMUM_FRAME_BYTES,
) : ProxyDuplexInspectorFactory {
    init {
        require(maximumFrameBytes > 0) { "Maximum WebSocket frame bytes must be positive." }
    }

    override fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyDuplexInspector? {
        if (!WebSocketProtocol.isHandshake(request)) return null
        return WebSocketDuplexInspector(streamId, capture, maximumFrameBytes)
    }
}

/** Exchange-scoped WebSocket observer with one direction-confined incremental parser per leg. */
private class WebSocketDuplexInspector(
    private val streamId: StreamId?,
    private val capture: ProxyExchangeCapture?,
    private val maximumFrameBytes: Int,
) : ProxyDuplexInspector {
    private var clientMessages: WebSocketCaptureDirection? = null
    private var serverMessages: WebSocketCaptureDirection? = null

    override fun onEstablished(response: ResponseHead, occurredAtEpochMillis: Long) {
        val compression = WebSocketProtocol.header(response.headers, EXTENSIONS)
            ?.split(',')
            ?.any { extension ->
                extension.substringBefore(';').trim().equals(PER_MESSAGE_DEFLATE, ignoreCase = true)
            } == true
        clientMessages = WebSocketCaptureDirection(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            streamId = streamId,
            capture = capture,
            permitsCompression = compression,
            maximumFrameBytes = maximumFrameBytes,
        )
        serverMessages = WebSocketCaptureDirection(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            streamId = streamId,
            capture = capture,
            permitsCompression = compression,
            maximumFrameBytes = maximumFrameBytes,
        )
    }

    override fun onPayload(
        direction: TrafficDirection,
        payload: ProxyPayloadSlice,
        occurredAtEpochMillis: Long,
    ) {
        val owned = ByteArray(payload.size)
        payload.copyTo(owned)
        directionState(direction)?.accept(owned, occurredAtEpochMillis)
    }

    override fun onTerminated(state: ExchangeState, occurredAtEpochMillis: Long, errorCode: String?) {
        val terminalCode = errorCode ?: PARENT_TERMINATED
        clientMessages?.terminate(state, occurredAtEpochMillis, terminalCode)
        serverMessages?.terminate(state, occurredAtEpochMillis, terminalCode)
    }

    private fun directionState(direction: TrafficDirection): WebSocketCaptureDirection? = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> clientMessages
        TrafficDirection.SERVER_TO_CLIENT -> serverMessages
    }

    private companion object {
        const val EXTENSIONS: String = "sec-websocket-extensions"
        const val PARENT_TERMINATED: String = "websocket_parent_exchange_terminated"
        const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
    }
}

/** Incremental logical-message capture for one WebSocket traffic direction. */
@OptIn(ExperimentalUuidApi::class)
private class WebSocketCaptureDirection(
    private val direction: TrafficDirection,
    private val streamId: StreamId?,
    private val capture: ProxyExchangeCapture?,
    permitsCompression: Boolean,
    maximumFrameBytes: Int,
) {
    private val decoder = WebSocketFrameDecoder(
        expectsMaskedFrames = direction == TrafficDirection.CLIENT_TO_SERVER,
        permitsCompression = permitsCompression,
        maximumFrameBytes = maximumFrameBytes,
    )
    private var activeCapture: ProxyMessageCapture? = null
    private var activeKind: ProtocolMessageKind? = null
    private var activeObservedBytes: Long = 0L
    private var sequence: Long = 0L
    private var failed = false

    fun accept(input: ByteArray, occurredAtEpochMillis: Long) {
        if (failed) return
        when (val result = decoder.accept(input)) {
            is WebSocketDecodeResult.Failure -> failActive(occurredAtEpochMillis, result.errorCode)
            is WebSocketDecodeResult.Frames -> result.values.forEach { frame ->
                if (!failed) acceptFrame(frame, occurredAtEpochMillis)
            }
        }
    }

    fun terminate(state: ExchangeState, occurredAtEpochMillis: Long, errorCode: String) {
        if (failed) return
        val messageState = when (state) {
            ExchangeState.COMPLETED -> ProtocolMessageState.TRUNCATED
            ExchangeState.CANCELLED -> ProtocolMessageState.CANCELLED
            ExchangeState.FAILED -> ProtocolMessageState.FAILED
            else -> ProtocolMessageState.CANCELLED
        }
        activeCapture?.terminate(activeObservedBytes, messageState, occurredAtEpochMillis, errorCode)
        activeCapture = null
        decoder.clear()
        failed = true
    }

    private fun acceptFrame(frame: WebSocketFrame, occurredAtEpochMillis: Long) {
        if (frame.opcode.isControl) {
            captureControl(frame, occurredAtEpochMillis)
            return
        }
        when (frame.opcode) {
            WebSocketOpcode.TEXT, WebSocketOpcode.BINARY -> {
                if (activeKind != null) {
                    failActive(occurredAtEpochMillis, UNEXPECTED_DATA_FRAME)
                    return
                }
                activeKind = frame.opcode.messageKind()
                activeObservedBytes = 0L
                activeCapture = startCapture(
                    kind = checkNotNull(activeKind),
                    declaredBytes = frame.payload.size.toLong().takeIf { frame.final },
                    compressed = frame.compressed,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                )
                append(frame.payload, occurredAtEpochMillis)
                if (frame.final) completeActive(occurredAtEpochMillis)
            }
            WebSocketOpcode.CONTINUATION -> {
                if (activeKind == null) {
                    failActive(occurredAtEpochMillis, UNEXPECTED_CONTINUATION)
                    return
                }
                append(frame.payload, occurredAtEpochMillis)
                if (frame.final) completeActive(occurredAtEpochMillis)
            }
            else -> Unit
        }
    }

    private fun captureControl(frame: WebSocketFrame, occurredAtEpochMillis: Long) {
        val messageCapture = startCapture(
            kind = frame.opcode.messageKind(),
            declaredBytes = frame.payload.size.toLong(),
            compressed = false,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        copyPayload(messageCapture, frame.payload, occurredAtEpochMillis)
        messageCapture?.complete(frame.payload.size.toLong(), occurredAtEpochMillis)
    }

    private fun startCapture(
        kind: ProtocolMessageKind,
        declaredBytes: Long?,
        compressed: Boolean,
        occurredAtEpochMillis: Long,
    ): ProxyMessageCapture? = capture?.startMessage(
        ProxyMessageCaptureMetadata(
            messageId = ProtocolMessageId(Uuid.random().toString()),
            streamId = streamId,
            protocol = MessageProtocolId.WEBSOCKET,
            kind = kind,
            direction = direction,
            messageSequence = ++sequence,
            declaredBytes = declaredBytes,
            compressed = compressed,
            compressionEncoding = PER_MESSAGE_DEFLATE.takeIf { compressed },
            occurredAtEpochMillis = occurredAtEpochMillis,
        ),
    )

    private fun append(payload: ByteArray, occurredAtEpochMillis: Long) {
        copyPayload(activeCapture, payload, occurredAtEpochMillis)
        activeObservedBytes += payload.size
    }

    private fun completeActive(occurredAtEpochMillis: Long) {
        activeCapture?.complete(activeObservedBytes, occurredAtEpochMillis)
        activeCapture = null
        activeKind = null
        activeObservedBytes = 0L
    }

    private fun failActive(occurredAtEpochMillis: Long, errorCode: String) {
        activeCapture?.terminate(
            activeObservedBytes,
            ProtocolMessageState.FAILED,
            occurredAtEpochMillis,
            errorCode,
        )
        activeCapture = null
        activeKind = null
        decoder.clear()
        failed = true
    }

    private fun copyPayload(
        messageCapture: ProxyMessageCapture?,
        payload: ByteArray,
        occurredAtEpochMillis: Long,
    ) {
        var offset = 0
        while (messageCapture != null && offset < payload.size) {
            val reservation = messageCapture.tryReservePayload(payload.size - offset) ?: return
            val admitted = reservation.writableBytes.size
            payload.copyInto(reservation.writableBytes, 0, offset, offset + admitted)
            if (!reservation.publish(occurredAtEpochMillis)) return
            offset += admitted
        }
    }

    private companion object {
        const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
        const val UNEXPECTED_CONTINUATION: String = "websocket_unexpected_continuation"
        const val UNEXPECTED_DATA_FRAME: String = "websocket_unexpected_data_frame"
    }
}

/** Maps one WebSocket opcode to the stable common child-message kind. */
internal fun WebSocketOpcode.messageKind(): ProtocolMessageKind = when (this) {
    WebSocketOpcode.TEXT -> ProtocolMessageKind.TEXT
    WebSocketOpcode.BINARY -> ProtocolMessageKind.BINARY
    WebSocketOpcode.CLOSE -> ProtocolMessageKind.CLOSE
    WebSocketOpcode.PING -> ProtocolMessageKind.PING
    WebSocketOpcode.PONG -> ProtocolMessageKind.PONG
    WebSocketOpcode.CONTINUATION -> error("Continuation frames inherit their initial data kind.")
}
