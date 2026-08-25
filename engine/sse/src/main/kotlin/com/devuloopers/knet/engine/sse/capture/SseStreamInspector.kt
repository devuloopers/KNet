package com.devuloopers.knet.engine.sse.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspectorFactory
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecPlanResult
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecRegistry
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecResult
import com.devuloopers.knet.engine.sse.encoding.SseContentDecoder
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates a lightweight response observer that activates only after an SSE response head. */
class SseStreamInspectorFactory(
    private val limits: SseLimits = SseLimits(),
) : ProxyStreamInspectorFactory {
    override fun create(
        request: RequestHead,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamInspector? = capture?.let { admittedCapture ->
        SseStreamInspector(streamId, admittedCapture, limits)
    }
}

/** Exchange-confined passive record framer; forwarding remains entirely proxy-owned. */
private class SseStreamInspector(
    streamId: StreamId?,
    capture: ProxyExchangeCapture,
    private val limits: SseLimits,
) : ProxyStreamInspector {
    private val framer = SseRecordCaptureFramer(streamId, capture, limits)
    private val codecs = SseContentCodecRegistry(limits)
    private var active = false
    private var decoder: SseContentDecoder? = null
    private var encoded = false
    private var unavailableReason: String? = null

    override fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        active = SseProtocol.isEventStream(response.headers)
        if (!active) return
        when (val result = codecs.resolve(SseProtocol.header(response.headers, SseProtocol.CONTENT_ENCODING))) {
            is SseContentCodecPlanResult.Supported -> {
                encoded = !result.plan.isIdentity
                decoder = result.plan.openDecoder().takeIf { encoded }
            }
            is SseContentCodecPlanResult.Unavailable -> unavailableReason = result.reason.code
        }
    }

    override fun onPayload(
        direction: TrafficDirection,
        payload: ProxyPayloadSlice,
        occurredAtEpochMillis: Long,
    ) {
        if (!active || direction != TrafficDirection.SERVER_TO_CLIENT) return
        unavailableReason?.let { reason ->
            framer.failAndDetach(occurredAtEpochMillis, reason, payload.size.toLong())
            unavailableReason = null
            return
        }
        if (!encoded) {
            framer.accept(payload, occurredAtEpochMillis)
            return
        }
        if (payload.size > limits.maximumDecoderInputBytesPerChunk) {
            framer.failAndDetach(occurredAtEpochMillis, INPUT_LIMIT, payload.size.toLong())
            return
        }
        val ownedInput = ByteArray(payload.size)
        payload.copyTo(ownedInput)
        when (val result = requireNotNull(decoder).accept(ownedInput, endOfInput = false)) {
            is SseContentCodecResult.Failure -> {
                decoder?.close()
                framer.failAndDetach(
                    occurredAtEpochMillis,
                    result.reason.code,
                    payload.size.toLong(),
                )
            }
            is SseContentCodecResult.Output -> {
                val decoded = result.copyBytes()
                if (decoded.isNotEmpty()) framer.accept(OwnedPayloadSlice(decoded), occurredAtEpochMillis)
            }
        }
    }

    override fun onDirectionEnd(direction: TrafficDirection, occurredAtEpochMillis: Long) {
        if (!active || direction != TrafficDirection.SERVER_TO_CLIENT) return
        unavailableReason?.let { reason ->
            framer.failAndDetach(occurredAtEpochMillis, reason, 0L)
            unavailableReason = null
            return
        }
        if (encoded) {
            when (val result = requireNotNull(decoder).accept(ByteArray(0), endOfInput = true)) {
                is SseContentCodecResult.Failure -> {
                    decoder?.close()
                    framer.failAndDetach(occurredAtEpochMillis, result.reason.code, 0L)
                    return
                }
                is SseContentCodecResult.Output -> {
                    val decoded = result.copyBytes()
                    if (decoded.isNotEmpty()) framer.accept(OwnedPayloadSlice(decoded), occurredAtEpochMillis)
                }
            }
            decoder?.close()
        }
        framer.finish(occurredAtEpochMillis)
    }

    override fun onExchangeTerminated(
        state: ExchangeState,
        occurredAtEpochMillis: Long,
        errorCode: String?,
    ) {
        decoder?.close()
        framer.cancel(occurredAtEpochMillis, errorCode ?: PARENT_TERMINATED)
    }

    private companion object {
        const val INPUT_LIMIT: String = "sse_decoder_input_chunk_limit"
        const val PARENT_TERMINATED: String = "sse_parent_exchange_terminated"
    }
}

/** Owned byte-array adapter used only after bounded representation decoding. */
private class OwnedPayloadSlice(private val bytes: ByteArray) : ProxyPayloadSlice {
    override val size: Int = bytes.size

    override fun indexOf(value: Byte, startIndex: Int): Int =
        (startIndex until bytes.size).firstOrNull { index -> bytes[index] == value } ?: -1

    override fun copyTo(
        destination: ByteArray,
        destinationOffset: Int,
        sourceOffset: Int,
        length: Int,
    ) {
        bytes.copyInto(destination, destinationOffset, sourceOffset, sourceOffset + length)
    }
}

/**
 * Finds record delimiters against borrowed payload and copies only ranges admitted by canonical
 * message reservations. The state machine supports LF, CRLF, and split CRLF boundaries.
 */
@OptIn(ExperimentalUuidApi::class)
private class SseRecordCaptureFramer(
    private val streamId: StreamId?,
    private val exchangeCapture: ProxyExchangeCapture,
    private val limits: SseLimits,
) {
    private var sequence = 0L
    private var currentCapture: ProxyMessageCapture? = null
    private var recordActive = false
    private var recordTruncated = false
    private var observedRecordBytes = 0L
    private var lineBytes = 0L
    private var pendingCarriageReturn = false
    private var pendingCarriageReturnWasBlank = false
    private var completedRecords = 0
    private var capturedExchangeBytes = 0L
    private var detached = false

    fun accept(payload: ProxyPayloadSlice, occurredAtEpochMillis: Long) {
        if (detached || payload.size == 0) return
        var offset = 0
        if (pendingCarriageReturn) {
            val beginsWithLineFeed = payload.indexOf(LF, 0) == 0
            if (beginsWithLineFeed) {
                appendDelimiter(payload, 0, 1, occurredAtEpochMillis)
                offset = 1
            }
            val blank = pendingCarriageReturnWasBlank
            pendingCarriageReturn = false
            pendingCarriageReturnWasBlank = false
            if (blank) completeRecord(occurredAtEpochMillis)
        }

        while (offset < payload.size && !detached) {
            val carriageReturn = payload.indexOf(CR, offset)
            val lineFeed = payload.indexOf(LF, offset)
            val delimiter = when {
                carriageReturn < 0 -> lineFeed
                lineFeed < 0 -> carriageReturn
                else -> minOf(carriageReturn, lineFeed)
            }
            if (delimiter < 0) {
                appendContent(payload, offset, payload.size - offset, occurredAtEpochMillis)
                return
            }
            if (delimiter > offset) {
                appendContent(payload, offset, delimiter - offset, occurredAtEpochMillis)
            }

            val blankLine = lineBytes == 0L
            if (delimiter == carriageReturn) {
                val hasLineFeed = delimiter + 1 < payload.size && payload.indexOf(LF, delimiter + 1) == delimiter + 1
                val delimiterBytes = if (hasLineFeed) 2 else 1
                appendDelimiter(payload, delimiter, delimiterBytes, occurredAtEpochMillis)
                offset = delimiter + delimiterBytes
                lineBytes = 0L
                if (hasLineFeed) {
                    if (blankLine) completeRecord(occurredAtEpochMillis)
                } else if (offset == payload.size) {
                    pendingCarriageReturn = true
                    pendingCarriageReturnWasBlank = blankLine
                    return
                } else if (blankLine) {
                    completeRecord(occurredAtEpochMillis)
                }
            } else {
                appendDelimiter(payload, delimiter, 1, occurredAtEpochMillis)
                offset = delimiter + 1
                lineBytes = 0L
                if (blankLine) completeRecord(occurredAtEpochMillis)
            }
        }
    }

    fun finish(occurredAtEpochMillis: Long) {
        if (detached) return
        if (pendingCarriageReturn) {
            val blank = pendingCarriageReturnWasBlank
            pendingCarriageReturn = false
            pendingCarriageReturnWasBlank = false
            if (blank) completeRecord(occurredAtEpochMillis)
        }
        if (recordActive) {
            currentCapture?.terminate(
                observedBytes = observedRecordBytes,
                state = ProtocolMessageState.FAILED,
                occurredAtEpochMillis = occurredAtEpochMillis,
                errorCode = INCOMPLETE_RECORD,
            )
            resetRecord()
        }
    }

    fun cancel(occurredAtEpochMillis: Long, errorCode: String) {
        if (recordActive) {
            currentCapture?.terminate(
                observedBytes = observedRecordBytes,
                state = ProtocolMessageState.CANCELLED,
                occurredAtEpochMillis = occurredAtEpochMillis,
                errorCode = errorCode,
            )
        }
        resetRecord()
        detached = true
    }

    /** Records one bounded semantic failure and permanently detaches this passive inspector. */
    fun failAndDetach(occurredAtEpochMillis: Long, errorCode: String, observedBytes: Long) {
        if (detached) return
        ensureRecord(occurredAtEpochMillis)
        currentCapture?.terminate(
            observedBytes = observedRecordBytes + observedBytes,
            state = ProtocolMessageState.FAILED,
            occurredAtEpochMillis = occurredAtEpochMillis,
            errorCode = errorCode,
        )
        resetRecord()
        detached = true
    }

    private fun appendContent(
        payload: ProxyPayloadSlice,
        sourceOffset: Int,
        length: Int,
        occurredAtEpochMillis: Long,
    ) {
        if (length == 0) return
        ensureRecord(occurredAtEpochMillis)
        lineBytes += length
        append(payload, sourceOffset, length, occurredAtEpochMillis)
    }

    private fun appendDelimiter(
        payload: ProxyPayloadSlice,
        sourceOffset: Int,
        length: Int,
        occurredAtEpochMillis: Long,
    ) {
        if (recordActive) append(payload, sourceOffset, length, occurredAtEpochMillis)
    }

    private fun ensureRecord(occurredAtEpochMillis: Long) {
        if (recordActive) return
        recordActive = true
        currentCapture = exchangeCapture.startMessage(
            ProxyMessageCaptureMetadata(
                messageId = ProtocolMessageId(Uuid.random().toString()),
                streamId = streamId,
                protocol = MessageProtocolId.SSE,
                kind = ProtocolMessageKind.RECORD,
                direction = TrafficDirection.SERVER_TO_CLIENT,
                messageSequence = ++sequence,
                declaredBytes = null,
                compressed = false,
                compressionEncoding = null,
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
    }

    private fun append(
        payload: ProxyPayloadSlice,
        sourceOffset: Int,
        length: Int,
        occurredAtEpochMillis: Long,
    ) {
        observedRecordBytes += length
        if (observedRecordBytes > limits.maximumRecordBytes) recordTruncated = true
        if (recordTruncated) return

        var copied = 0
        while (copied < length) {
            val exchangeRemaining = limits.maximumCapturedBytesPerExchange - capturedExchangeBytes
            if (exchangeRemaining <= 0L) {
                recordTruncated = true
                return
            }
            val requested = minOf(length - copied, exchangeRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val reservation = currentCapture?.tryReservePayload(requested)
            if (reservation == null || reservation.writableBytes.isEmpty()) {
                reservation?.cancel()
                recordTruncated = true
                return
            }
            val admitted = minOf(reservation.writableBytes.size, requested)
            payload.copyTo(
                destination = reservation.writableBytes,
                sourceOffset = sourceOffset + copied,
                length = admitted,
            )
            reservation.publish(occurredAtEpochMillis)
            copied += admitted
            capturedExchangeBytes += admitted
        }
    }

    private fun completeRecord(occurredAtEpochMillis: Long) {
        if (!recordActive) return
        if (recordTruncated) {
            currentCapture?.terminate(
                observedBytes = observedRecordBytes,
                state = ProtocolMessageState.TRUNCATED,
                occurredAtEpochMillis = occurredAtEpochMillis,
                errorCode = RECORD_LIMIT,
            )
        } else {
            currentCapture?.complete(observedRecordBytes, occurredAtEpochMillis)
        }
        completedRecords++
        resetRecord()
        if (completedRecords >= limits.maximumCapturedRecordsPerExchange ||
            capturedExchangeBytes >= limits.maximumCapturedBytesPerExchange
        ) {
            detached = true
        }
    }

    private fun resetRecord() {
        currentCapture = null
        recordActive = false
        recordTruncated = false
        observedRecordBytes = 0L
        lineBytes = 0L
        pendingCarriageReturn = false
        pendingCarriageReturnWasBlank = false
    }

    private companion object {
        const val CR: Byte = 13
        const val LF: Byte = 10
        const val INCOMPLETE_RECORD: String = "sse_record_ended_without_blank_line"
        const val RECORD_LIMIT: String = "sse_record_capture_limit"
    }
}
