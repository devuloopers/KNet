package com.devuloopers.knet.engine.websocket

import java.io.ByteArrayOutputStream

/**
 * Wire opcode defined by RFC 6455 section 5.2.
 *
 * @property wireValue Four-bit value encoded in a WebSocket frame header.
 */
enum class WebSocketOpcode(val wireValue: Int) {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA),
    ;

    /** Returns whether this opcode is a connection-control frame. */
    val isControl: Boolean get() = wireValue >= 0x8

    companion object {
        /** Resolves one supported wire value, returning `null` for reserved opcodes. */
        fun fromWireValue(value: Int): WebSocketOpcode? = entries.firstOrNull { opcode ->
            opcode.wireValue == value
        }
    }
}

/**
 * One validated, fully decoded WebSocket frame plus its exact original wire representation.
 *
 * @property final Whether this frame ends its logical message.
 * @property compressed Whether the negotiated RSV1 compression marker was set.
 * @property opcode Semantic frame opcode.
 * @property masked Whether the payload arrived with a masking key.
 * @property maskingKey Four-byte key retained for exact directional inspection, when present.
 * @property payload Unmasked payload bytes owned by this frame.
 * @property originalWireBytes Exact immutable-by-contract bytes observed on the wire.
 * @throws IllegalArgumentException when masking metadata is inconsistent.
 */
data class WebSocketFrame(
    val final: Boolean,
    val compressed: Boolean,
    val opcode: WebSocketOpcode,
    val masked: Boolean,
    val maskingKey: ByteArray?,
    val payload: ByteArray,
    val originalWireBytes: ByteArray,
) {
    init {
        require(masked == (maskingKey != null)) { "Masked frames require a masking key." }
        require(maskingKey == null || maskingKey.size == MASK_BYTES) { "WebSocket masking keys use four bytes." }
    }

    companion object {
        private const val MASK_BYTES: Int = 4
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WebSocketFrame

        if (final != other.final) return false
        if (compressed != other.compressed) return false
        if (masked != other.masked) return false
        if (opcode != other.opcode) return false
        if (!maskingKey.contentEquals(other.maskingKey)) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!originalWireBytes.contentEquals(other.originalWireBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = final.hashCode()
        result = 31 * result + compressed.hashCode()
        result = 31 * result + masked.hashCode()
        result = 31 * result + opcode.hashCode()
        result = 31 * result + (maskingKey?.contentHashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + originalWireBytes.contentHashCode()
        return result
    }
}

/** Typed result from one incremental decoder input. */
sealed interface WebSocketDecodeResult {
    /**
     * Zero or more complete frames; incomplete trailing bytes remain decoder-owned.
     *
     * @property values Complete frames produced by the current input slice.
     */
    data class Frames(val values: List<WebSocketFrame>) : WebSocketDecodeResult

    /**
     * Stable validation failure requiring the upgraded connection to close.
     *
     * @property errorCode Machine-readable protocol failure code.
     */
    data class Failure(val errorCode: String) : WebSocketDecodeResult
}

/**
 * Bounded incremental RFC 6455 frame decoder.
 *
 * The decoder owns incomplete bytes, validates masking by traffic direction, rejects reserved bits
 * other than negotiated RSV1 compression, and never allocates a declared payload above its limit.
 */
class WebSocketFrameDecoder(
    private val expectsMaskedFrames: Boolean,
    private val permitsCompression: Boolean,
    private val maximumFrameBytes: Int = DEFAULT_MAXIMUM_FRAME_BYTES,
) {
    private var pending = ByteArray(0)
    private var failed = false

    init {
        require(maximumFrameBytes > 0) { "Maximum WebSocket frame bytes must be positive." }
    }

    /** Accepts the next ordered transport slice and returns every complete validated frame. */
    fun accept(input: ByteArray): WebSocketDecodeResult {
        if (failed) return WebSocketDecodeResult.Failure(DECODER_FAILED)
        if (input.isNotEmpty()) pending += input
        val frames = mutableListOf<WebSocketFrame>()
        var offset = 0
        while (true) {
            val remaining = pending.size - offset
            if (remaining < BASE_HEADER_BYTES) break
            val first = pending[offset].toInt() and 0xff
            val second = pending[offset + 1].toInt() and 0xff
            val final = first and FIN_BIT != 0
            val compressed = first and RSV1_BIT != 0
            val reserved = first and (RSV2_BIT or RSV3_BIT) != 0
            val opcode = WebSocketOpcode.fromWireValue(first and OPCODE_MASK)
                ?: return fail(RESERVED_OPCODE)
            if (reserved || (compressed && !permitsCompression)) return fail(RESERVED_BITS)
            if (opcode == WebSocketOpcode.CONTINUATION && compressed) return fail(COMPRESSED_CONTINUATION)

            val masked = second and MASK_BIT != 0
            if (masked != expectsMaskedFrames) return fail(INVALID_MASKING)
            val shortLength = second and LENGTH_MASK
            var headerBytes = BASE_HEADER_BYTES
            val declaredLength = when (shortLength) {
                LENGTH_16_MARKER -> {
                    if (remaining < headerBytes + LENGTH_16_BYTES) break
                    val value = unsigned16(pending, offset + headerBytes)
                    headerBytes += LENGTH_16_BYTES
                    if (value < LENGTH_16_MARKER) return fail(NON_MINIMAL_LENGTH)
                    value.toLong()
                }
                LENGTH_64_MARKER -> {
                    if (remaining < headerBytes + LENGTH_64_BYTES) break
                    val value = unsigned63(pending, offset + headerBytes) ?: return fail(INVALID_LENGTH)
                    headerBytes += LENGTH_64_BYTES
                    if (value <= U16_MAXIMUM) return fail(NON_MINIMAL_LENGTH)
                    value
                }
                else -> shortLength.toLong()
            }
            if (opcode.isControl && (compressed || !final || declaredLength > MAXIMUM_CONTROL_BYTES)) {
                return fail(INVALID_CONTROL_FRAME)
            }
            if (opcode == WebSocketOpcode.CLOSE && declaredLength == INVALID_CLOSE_PAYLOAD_BYTES.toLong()) {
                return fail(INVALID_CLOSE_FRAME)
            }
            if (declaredLength > maximumFrameBytes.toLong()) return fail(FRAME_LIMIT)
            if (masked) headerBytes += MASK_BYTES
            val totalBytes = headerBytes.toLong() + declaredLength
            if (totalBytes > Int.MAX_VALUE || remaining.toLong() < totalBytes) break

            val maskingKey = if (masked) {
                pending.copyOfRange(offset + headerBytes - MASK_BYTES, offset + headerBytes)
            } else {
                null
            }
            val payloadStart = offset + headerBytes
            val payload = pending.copyOfRange(payloadStart, payloadStart + declaredLength.toInt())
            if (maskingKey != null) applyMask(payload, maskingKey)
            val frameEnd = offset + totalBytes.toInt()
            frames += WebSocketFrame(
                final = final,
                compressed = compressed,
                opcode = opcode,
                masked = masked,
                maskingKey = maskingKey,
                payload = payload,
                originalWireBytes = pending.copyOfRange(offset, frameEnd),
            )
            offset = frameEnd
        }
        if (offset > 0) pending = pending.copyOfRange(offset, pending.size)
        return WebSocketDecodeResult.Frames(frames)
    }

    /** Returns whether an incomplete frame remains buffered. */
    fun hasPendingBytes(): Boolean = pending.isNotEmpty()

    /** Releases incomplete transport bytes. */
    fun clear() {
        pending = ByteArray(0)
    }

    private fun fail(errorCode: String): WebSocketDecodeResult.Failure {
        failed = true
        pending = ByteArray(0)
        return WebSocketDecodeResult.Failure(errorCode)
    }

    companion object {
        /** Default maximum admitted by one frame before the connection is rejected. */
        const val DEFAULT_MAXIMUM_FRAME_BYTES: Int = 64 * 1_024 * 1_024

        /** Encodes one complete frame, applying the supplied client mask when present. */
        fun encode(
            opcode: WebSocketOpcode,
            payload: ByteArray,
            final: Boolean = true,
            compressed: Boolean = false,
            maskingKey: ByteArray? = null,
        ): ByteArray {
            require(opcode != WebSocketOpcode.CONTINUATION || !compressed) {
                "Continuation frames cannot set RSV1."
            }
            require(!opcode.isControl || (!compressed && final && payload.size <= MAXIMUM_CONTROL_BYTES)) {
                "Control frames cannot be compressed, must be final, and may contain at most 125 bytes."
            }
            require(opcode != WebSocketOpcode.CLOSE || payload.size != INVALID_CLOSE_PAYLOAD_BYTES) {
                "Close frames cannot contain a one-byte payload."
            }
            require(maskingKey == null || maskingKey.size == MASK_BYTES) {
                "WebSocket masking keys use four bytes."
            }
            val output = ByteArrayOutputStream(payload.size + MAXIMUM_HEADER_BYTES)
            var first = opcode.wireValue
            if (final) first = first or FIN_BIT
            if (compressed) first = first or RSV1_BIT
            output.write(first)
            val maskBit = if (maskingKey == null) 0 else MASK_BIT
            when {
                payload.size < LENGTH_16_MARKER -> output.write(maskBit or payload.size)
                payload.size <= U16_MAXIMUM -> {
                    output.write(maskBit or LENGTH_16_MARKER)
                    output.write(payload.size ushr 8)
                    output.write(payload.size)
                }
                else -> {
                    output.write(maskBit or LENGTH_64_MARKER)
                    repeat(LENGTH_64_BYTES) { index ->
                        output.write((payload.size.toLong() ushr ((LENGTH_64_BYTES - 1 - index) * 8)).toInt())
                    }
                }
            }
            if (maskingKey != null) output.write(maskingKey)
            val wirePayload = payload.copyOf()
            if (maskingKey != null) applyMask(wirePayload, maskingKey)
            output.write(wirePayload)
            return output.toByteArray()
        }

        private fun unsigned16(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

        private fun unsigned63(bytes: ByteArray, offset: Int): Long? {
            if (bytes[offset].toInt() and 0x80 != 0) return null
            var value = 0L
            repeat(LENGTH_64_BYTES) { index -> value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL) }
            return value
        }

        private fun applyMask(payload: ByteArray, maskingKey: ByteArray) {
            payload.indices.forEach { index ->
                payload[index] = (payload[index].toInt() xor maskingKey[index % MASK_BYTES].toInt()).toByte()
            }
        }

        private const val BASE_HEADER_BYTES: Int = 2
        private const val COMPRESSED_CONTINUATION: String = "websocket_compressed_continuation"
        private const val DECODER_FAILED: String = "websocket_decoder_failed"
        private const val FIN_BIT: Int = 0x80
        private const val FRAME_LIMIT: String = "websocket_frame_limit"
        private const val INVALID_CONTROL_FRAME: String = "websocket_invalid_control_frame"
        private const val INVALID_CLOSE_FRAME: String = "websocket_invalid_close_frame"
        private const val INVALID_CLOSE_PAYLOAD_BYTES: Int = 1
        private const val INVALID_LENGTH: String = "websocket_invalid_length"
        private const val INVALID_MASKING: String = "websocket_invalid_masking"
        private const val LENGTH_16_BYTES: Int = 2
        private const val LENGTH_16_MARKER: Int = 126
        private const val LENGTH_64_BYTES: Int = 8
        private const val LENGTH_64_MARKER: Int = 127
        private const val LENGTH_MASK: Int = 0x7f
        private const val MASK_BIT: Int = 0x80
        private const val MASK_BYTES: Int = 4
        private const val MAXIMUM_CONTROL_BYTES: Int = 125
        private const val MAXIMUM_HEADER_BYTES: Int = 14
        private const val NON_MINIMAL_LENGTH: String = "websocket_non_minimal_length"
        private const val OPCODE_MASK: Int = 0x0f
        private const val RESERVED_BITS: String = "websocket_reserved_bits"
        private const val RESERVED_OPCODE: String = "websocket_reserved_opcode"
        private const val RSV1_BIT: Int = 0x40
        private const val RSV2_BIT: Int = 0x20
        private const val RSV3_BIT: Int = 0x10
        private const val U16_MAXIMUM: Long = 65_535L
    }
}
