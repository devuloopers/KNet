package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.HeaderField
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Negotiated RFC 7692 settings that affect decoding independently in each direction.
 *
 * @property clientNoContextTakeover Whether client-to-server messages reset their inflater.
 * @property serverNoContextTakeover Whether server-to-client messages reset their inflater.
 */
data class WebSocketPerMessageDeflateNegotiation(
    val clientNoContextTakeover: Boolean,
    val serverNoContextTakeover: Boolean,
) {
    /** Returns the negotiated context-takeover behavior for [direction]. */
    fun noContextTakeover(direction: TrafficDirection): Boolean = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> clientNoContextTakeover
        TrafficDirection.SERVER_TO_CLIENT -> serverNoContextTakeover
    }

    companion object {
        /** Parses the selected per-message deflate parameters from response [headers]. */
        fun fromResponseHeaders(headers: List<HeaderField>): WebSocketPerMessageDeflateNegotiation? {
            val extension = headers
                .asSequence()
                .filter { header -> header.name.value.equals(EXTENSIONS, ignoreCase = true) }
                .flatMap { header -> header.value.split(',').asSequence() }
                .map { value -> value.split(';').map(String::trim) }
                .firstOrNull { tokens ->
                    tokens.firstOrNull()?.equals(PER_MESSAGE_DEFLATE, ignoreCase = true) == true
                }
                ?: return null
            val parameters = extension.drop(1).map { parameter ->
                parameter.substringBefore('=').trim().lowercase()
            }
            return WebSocketPerMessageDeflateNegotiation(
                clientNoContextTakeover = CLIENT_NO_CONTEXT_TAKEOVER in parameters,
                serverNoContextTakeover = SERVER_NO_CONTEXT_TAKEOVER in parameters,
            )
        }

        private const val CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover"
        private const val EXTENSIONS = "sec-websocket-extensions"
        private const val PER_MESSAGE_DEFLATE = "permessage-deflate"
        private const val SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover"
    }
}

/** Bounded result of inflating one complete compressed WebSocket message. */
sealed interface WebSocketInflateResult {
    /** Successfully decoded logical message payload. */
    data class Success(val payload: ByteArray) : WebSocketInflateResult

    /** Stable validation or output-bound failure. */
    data class Failure(val errorCode: String) : WebSocketInflateResult
}

/**
 * Stateful RFC 7692 raw-DEFLATE decoder for one WebSocket direction.
 *
 * The inflater is deliberately direction-scoped because context takeover dictionaries must never
 * cross the client/server boundary. Output is bounded before it is retained by capture or rules.
 */
class WebSocketPerMessageDeflateDecoder(
    private val noContextTakeover: Boolean,
    private val maximumOutputBytes: Int,
) : AutoCloseable {
    private val inflater = Inflater(true)
    private var closed = false

    init {
        require(maximumOutputBytes > 0) { "Maximum inflated WebSocket bytes must be positive." }
    }

    /** Decodes one complete RFC 7692 message [payload] using the negotiated direction state. */
    fun decode(payload: ByteArray): WebSocketInflateResult {
        if (closed) return WebSocketInflateResult.Failure(INFLATER_CLOSED)
        val output = ByteArrayOutputStream(minOf(payload.size.coerceAtLeast(32), maximumOutputBytes))
        val buffer = ByteArray(minOf(OUTPUT_CHUNK_BYTES, maximumOutputBytes))
        return try {
            inflater.setInput(payload)
            when (val bodyResult = drain(output, buffer)) {
                null -> {
                    inflater.setInput(MESSAGE_TAIL)
                    when (val tailResult = drain(output, buffer)) {
                        null -> WebSocketInflateResult.Success(output.toByteArray())
                        else -> tailResult
                    }
                }
                else -> bodyResult
            }
        } catch (_: DataFormatException) {
            WebSocketInflateResult.Failure(INVALID_COMPRESSED_PAYLOAD)
        } catch (_: IllegalStateException) {
            WebSocketInflateResult.Failure(INVALID_COMPRESSED_PAYLOAD)
        } finally {
            if (noContextTakeover && !closed) inflater.reset()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        inflater.end()
    }

    private fun drain(
        output: ByteArrayOutputStream,
        buffer: ByteArray,
    ): WebSocketInflateResult.Failure? {
        while (true) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                if (output.size() > maximumOutputBytes - count) {
                    return WebSocketInflateResult.Failure(INFLATED_MESSAGE_LIMIT)
                }
                output.write(buffer, 0, count)
                continue
            }
            if (inflater.needsDictionary()) {
                return WebSocketInflateResult.Failure(INVALID_COMPRESSED_PAYLOAD)
            }
            if (inflater.needsInput() || inflater.finished()) return null
            return WebSocketInflateResult.Failure(INVALID_COMPRESSED_PAYLOAD)
        }
    }

    companion object {
        const val INFLATED_MESSAGE_LIMIT = "websocket_inflated_message_limit"
        const val INVALID_COMPRESSED_PAYLOAD = "websocket_invalid_compressed_payload"
        private const val INFLATER_CLOSED = "websocket_inflater_closed"
        private const val OUTPUT_CHUNK_BYTES = 8 * 1_024
        private val MESSAGE_TAIL = byteArrayOf(0x00, 0x00, 0xff.toByte(), 0xff.toByte())
    }
}
