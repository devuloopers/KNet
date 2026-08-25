package com.devuloopers.knet.engine.sse.encoding

import com.devuloopers.knet.engine.sse.protocol.SseLimits
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Supported HTTP representation codings for a live SSE stream. */
internal enum class SseContentCoding(val token: String) {
    GZIP("gzip"),
    DEFLATE("deflate"),
}

/** Stable failure classification shared by SSE capture, API Studio, and breakpoints. */
internal enum class SseContentCodecFailure(val code: String) {
    UNSUPPORTED_ENCODING("sse_content_encoding_unsupported"),
    INVALID_ENCODING_CHAIN("sse_content_encoding_chain_invalid"),
    ENCODING_LAYER_LIMIT("sse_content_encoding_layer_limit"),
    INPUT_CHUNK_LIMIT("sse_decoder_input_chunk_limit"),
    RETAINED_INPUT_LIMIT("sse_decoder_retained_input_limit"),
    DECODED_CHUNK_LIMIT("sse_decoder_output_chunk_limit"),
    EXPANSION_LIMIT("sse_decoder_expansion_limit"),
    MALFORMED_STREAM("sse_content_encoding_malformed"),
    TRUNCATED_STREAM("sse_content_encoding_truncated"),
    TRAILING_BYTES("sse_content_encoding_trailing_bytes"),
    ENCODER_FAILURE("sse_content_encoder_failure"),
}

/** Result of one bounded content-codec operation. */
internal sealed interface SseContentCodecResult {
    /** Owned bytes emitted by the codec for this call. */
    class Output(bytes: ByteArray) : SseContentCodecResult {
        private val ownedBytes = bytes.copyOf()

        /** Returns an owned copy of the emitted bytes. */
        fun copyBytes(): ByteArray = ownedBytes.copyOf()
    }

    /** Terminal codec failure after which the stream session must not be reused. */
    data class Failure(val reason: SseContentCodecFailure) : SseContentCodecResult
}

/** Parsed immutable content-coding plan used to create stream-confined codec sessions. */
internal class SseContentCodecPlan internal constructor(
    codings: List<SseContentCoding>,
    private val limits: SseLimits,
) {
    private val orderedCodings = codings.toList()

    /** Whether representation bytes can be consumed without a content decoder. */
    val isIdentity: Boolean = orderedCodings.isEmpty()

    /** Opens a fresh decoder in reverse HTTP Content-Encoding application order. */
    fun openDecoder(): SseContentDecoder = SseContentDecoderChain(
        decoders = orderedCodings.asReversed().map { coding ->
            when (coding) {
                SseContentCoding.GZIP -> IncrementalGzipDecoder(limits)
                SseContentCoding.DEFLATE -> IncrementalDeflateDecoder(limits)
            }
        },
        limits = limits,
    )

    /** Opens a fresh encoder in HTTP Content-Encoding application order. */
    fun openEncoder(): SseContentEncoder = SseContentEncoderChain(
        encoders = orderedCodings.map { coding ->
            when (coding) {
                SseContentCoding.GZIP -> IncrementalGzipEncoder(limits)
                SseContentCoding.DEFLATE -> IncrementalDeflateEncoder(limits)
            }
        },
        limits = limits,
    )
}

/** Result of parsing one response Content-Encoding declaration. */
internal sealed interface SseContentCodecPlanResult {
    /** Supported content-coding plan. */
    data class Supported(val plan: SseContentCodecPlan) : SseContentCodecPlanResult

    /** Unsupported or malformed content-coding declaration. */
    data class Unavailable(
        val reason: SseContentCodecFailure,
        val token: String? = null,
    ) : SseContentCodecPlanResult
}

/** Parses content-coding declarations and creates bounded per-stream codec plans. */
internal class SseContentCodecRegistry(
    private val limits: SseLimits,
) {
    /** Resolves [contentEncoding] without allocating a decoder for a non-SSE response. */
    fun resolve(contentEncoding: String?): SseContentCodecPlanResult {
        if (contentEncoding.isNullOrBlank()) {
            return SseContentCodecPlanResult.Supported(SseContentCodecPlan(emptyList(), limits))
        }
        val tokens = contentEncoding.split(',').map(String::trim)
        if (tokens.any(String::isEmpty)) {
            return SseContentCodecPlanResult.Unavailable(SseContentCodecFailure.INVALID_ENCODING_CHAIN)
        }
        if (tokens.size > limits.maximumContentEncodingLayers) {
            return SseContentCodecPlanResult.Unavailable(SseContentCodecFailure.ENCODING_LAYER_LIMIT)
        }
        if (tokens.size == 1 && tokens.single().equals(IDENTITY, ignoreCase = true)) {
            return SseContentCodecPlanResult.Supported(SseContentCodecPlan(emptyList(), limits))
        }
        if (tokens.any { token -> token.equals(IDENTITY, ignoreCase = true) }) {
            return SseContentCodecPlanResult.Unavailable(SseContentCodecFailure.INVALID_ENCODING_CHAIN)
        }
        val codings = tokens.map { token ->
            SseContentCoding.entries.firstOrNull { coding -> coding.token.equals(token, ignoreCase = true) }
                ?: return SseContentCodecPlanResult.Unavailable(
                    SseContentCodecFailure.UNSUPPORTED_ENCODING,
                    token,
                )
        }
        return SseContentCodecPlanResult.Supported(SseContentCodecPlan(codings, limits))
    }

    private companion object {
        const val IDENTITY: String = "identity"
    }
}

/** Stream-confined incremental decoder with explicit native-resource cleanup. */
internal interface SseContentDecoder {
    /** Decodes one ordered input chunk and validates completion when [endOfInput] is true. */
    fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult

    /** Releases retained input and native compression state. Safe to call more than once. */
    fun close()
}

/** Stream-confined incremental encoder with explicit native-resource cleanup. */
internal interface SseContentEncoder {
    /** Encodes one ordered decoded chunk and completes the representation when [endOfInput] is true. */
    fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult

    /** Releases native compression state. Safe to call more than once. */
    fun close()
}

/** Applies a fixed decoder chain while bounding every intermediate representation. */
private class SseContentDecoderChain(
    private val decoders: List<SseContentDecoder>,
    private val limits: SseLimits,
) : SseContentDecoder {
    private var failed = false

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (failed) return SseContentCodecResult.Failure(SseContentCodecFailure.MALFORMED_STREAM)
        if (input.size > limits.maximumDecoderInputBytesPerChunk) return fail(SseContentCodecFailure.INPUT_CHUNK_LIMIT)
        var output = input
        decoders.forEach { decoder ->
            when (val result = decoder.accept(output, endOfInput)) {
                is SseContentCodecResult.Failure -> return fail(result.reason)
                is SseContentCodecResult.Output -> output = result.copyBytes()
            }
            if (output.size > limits.maximumDecodedBytesPerChunk) return fail(SseContentCodecFailure.DECODED_CHUNK_LIMIT)
        }
        return SseContentCodecResult.Output(output)
    }

    override fun close() {
        decoders.forEach(SseContentDecoder::close)
    }

    private fun fail(reason: SseContentCodecFailure): SseContentCodecResult.Failure {
        failed = true
        close()
        return SseContentCodecResult.Failure(reason)
    }
}

/** Applies a fixed encoder chain while bounding every intermediate representation. */
private class SseContentEncoderChain(
    private val encoders: List<SseContentEncoder>,
    private val limits: SseLimits,
) : SseContentEncoder {
    private var failed = false

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (failed) return SseContentCodecResult.Failure(SseContentCodecFailure.ENCODER_FAILURE)
        var output = input
        encoders.forEach { encoder ->
            when (val result = encoder.accept(output, endOfInput)) {
                is SseContentCodecResult.Failure -> {
                    failed = true
                    close()
                    return result
                }
                is SseContentCodecResult.Output -> output = result.copyBytes()
            }
            if (output.size > limits.maximumDecodedBytesPerChunk) {
                failed = true
                close()
                return SseContentCodecResult.Failure(SseContentCodecFailure.DECODED_CHUNK_LIMIT)
            }
        }
        return SseContentCodecResult.Output(output)
    }

    override fun close() {
        encoders.forEach(SseContentEncoder::close)
    }
}

/** Stateful zlib/raw-DEFLATE decoder selected from the first two representation bytes. */
private class IncrementalDeflateDecoder(
    private val limits: SseLimits,
) : SseContentDecoder {
    private var prefix = ByteArray(0)
    private var inflater: Inflater? = null
    private var finished = false
    private val expansionGuard = ExpansionGuard(limits)

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (finished) {
            return if (input.isEmpty()) SseContentCodecResult.Output(ByteArray(0))
            else SseContentCodecResult.Failure(SseContentCodecFailure.TRAILING_BYTES)
        }
        var selectedInput = input
        if (inflater == null) {
            val combined = prefix + input
            if (combined.size < 2 && !endOfInput) {
                prefix = combined
                return SseContentCodecResult.Output(ByteArray(0))
            }
            if (combined.size < 2) return SseContentCodecResult.Failure(SseContentCodecFailure.TRUNCATED_STREAM)
            val zlibWrapped = isZlibHeader(combined[0], combined[1])
            inflater = Inflater(!zlibWrapped)
            prefix = ByteArray(0)
            selectedInput = combined
        }
        val selectedInflater = requireNotNull(inflater)
        expansionGuard.observeInput(selectedInput.size)
        val output = BoundedChunkCollector(limits.maximumDecodedBytesPerChunk)
        val failure = inflateInto(selectedInflater, selectedInput) { bytes, offset, length ->
            expansionGuard.observeOutput(length)
            if (output.append(bytes, offset, length)) null else SseContentCodecFailure.DECODED_CHUNK_LIMIT
        }
        if (failure != null) return SseContentCodecResult.Failure(failure)
        if (!expansionGuard.isAllowed()) return SseContentCodecResult.Failure(SseContentCodecFailure.EXPANSION_LIMIT)
        if (selectedInflater.finished()) {
            if (selectedInflater.remaining != 0) return SseContentCodecResult.Failure(SseContentCodecFailure.TRAILING_BYTES)
            finished = true
            selectedInflater.end()
        } else if (endOfInput) {
            selectedInflater.end()
            return SseContentCodecResult.Failure(SseContentCodecFailure.TRUNCATED_STREAM)
        }
        return SseContentCodecResult.Output(output.toByteArray())
    }

    override fun close() {
        inflater?.end()
        inflater = null
        prefix = ByteArray(0)
        finished = true
    }
}

/** Incremental RFC 1952 decoder with bounded headers and concatenated-member support. */
private class IncrementalGzipDecoder(
    private val limits: SseLimits,
) : SseContentDecoder {
    private enum class State { HEADER, DEFLATE, TRAILER, COMPLETE }

    private var state = State.HEADER
    private val header = ByteArray(limits.maximumDecoderRetainedBytes)
    private var headerSize = 0
    private val trailer = ByteArray(GZIP_TRAILER_BYTES)
    private var trailerSize = 0
    private var inflater: Inflater? = null
    private val memberCrc = CRC32()
    private var memberBytes = 0L
    private var completedMembers = 0
    private val expansionGuard = ExpansionGuard(limits)

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (state == State.COMPLETE) {
            return if (input.isEmpty()) SseContentCodecResult.Output(ByteArray(0))
            else SseContentCodecResult.Failure(SseContentCodecFailure.TRAILING_BYTES)
        }
        expansionGuard.observeInput(input.size)
        val output = BoundedChunkCollector(limits.maximumDecodedBytesPerChunk)
        var offset = 0
        try {
            while (offset < input.size) {
                when (state) {
                    State.HEADER -> {
                        if (headerSize == header.size) return failure(SseContentCodecFailure.RETAINED_INPUT_LIMIT)
                        header[headerSize++] = input[offset++]
                        if (!validGzipPrefix(header, headerSize)) {
                            return failure(SseContentCodecFailure.MALFORMED_STREAM)
                        }
                        val length = gzipHeaderLength(header, headerSize) ?: continue
                        if (length != headerSize) return failure(SseContentCodecFailure.MALFORMED_STREAM)
                        inflater = Inflater(true)
                        memberCrc.reset()
                        memberBytes = 0L
                        headerSize = 0
                        state = State.DEFLATE
                    }
                    State.DEFLATE -> {
                        val selectedInflater = requireNotNull(inflater)
                        val available = input.size - offset
                        selectedInflater.setInput(input, offset, available)
                        val decodeFailure = drainInflater(selectedInflater, output)
                        if (decodeFailure != null) return failure(decodeFailure)
                        val consumed = available - selectedInflater.remaining
                        offset += consumed
                        if (selectedInflater.finished()) {
                            selectedInflater.end()
                            inflater = null
                            trailerSize = 0
                            state = State.TRAILER
                        } else if (selectedInflater.needsInput()) {
                            offset = input.size
                        } else {
                            return failure(SseContentCodecFailure.MALFORMED_STREAM)
                        }
                    }
                    State.TRAILER -> {
                        val copied = minOf(GZIP_TRAILER_BYTES - trailerSize, input.size - offset)
                        input.copyInto(trailer, trailerSize, offset, offset + copied)
                        trailerSize += copied
                        offset += copied
                        if (trailerSize == GZIP_TRAILER_BYTES) {
                            if (!validTrailer()) return failure(SseContentCodecFailure.MALFORMED_STREAM)
                            completedMembers++
                            trailerSize = 0
                            state = State.HEADER
                        }
                    }
                    State.COMPLETE -> return failure(SseContentCodecFailure.TRAILING_BYTES)
                }
            }
        } catch (_: DataFormatException) {
            return failure(SseContentCodecFailure.MALFORMED_STREAM)
        }
        if (!expansionGuard.isAllowed()) return failure(SseContentCodecFailure.EXPANSION_LIMIT)
        if (endOfInput) {
            if (state != State.HEADER || headerSize != 0 || completedMembers == 0) {
                return failure(SseContentCodecFailure.TRUNCATED_STREAM)
            }
            state = State.COMPLETE
        }
        return SseContentCodecResult.Output(output.toByteArray())
    }

    override fun close() {
        inflater?.end()
        inflater = null
        headerSize = 0
        trailerSize = 0
        state = State.COMPLETE
    }

    private fun drainInflater(inflater: Inflater, output: BoundedChunkCollector): SseContentCodecFailure? {
        val buffer = ByteArray(DECODE_BUFFER_BYTES)
        while (true) {
            val decoded = inflater.inflate(buffer)
            if (decoded > 0) {
                memberCrc.update(buffer, 0, decoded)
                memberBytes = (memberBytes + decoded) and UNSIGNED_INT_MASK
                expansionGuard.observeOutput(decoded)
                if (!output.append(buffer, 0, decoded)) return SseContentCodecFailure.DECODED_CHUNK_LIMIT
                if (!expansionGuard.isAllowed()) return SseContentCodecFailure.EXPANSION_LIMIT
                continue
            }
            return when {
                inflater.finished() || inflater.needsInput() -> null
                inflater.needsDictionary() -> SseContentCodecFailure.MALFORMED_STREAM
                else -> SseContentCodecFailure.MALFORMED_STREAM
            }
        }
    }

    private fun validTrailer(): Boolean =
        readLittleEndianUnsignedInt(trailer, 0) == memberCrc.value &&
            readLittleEndianUnsignedInt(trailer, 4) == memberBytes

    private fun failure(reason: SseContentCodecFailure): SseContentCodecResult.Failure {
        inflater?.end()
        inflater = null
        state = State.COMPLETE
        return SseContentCodecResult.Failure(reason)
    }
}

/** Standards-compliant zlib-wrapped DEFLATE encoder. */
private class IncrementalDeflateEncoder(limits: SseLimits) : SseContentEncoder {
    private val delegate = IncrementalDeflaterEncoder(nowrap = false, limits)
    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult =
        delegate.accept(input, endOfInput)

    override fun close() = delegate.close()
}

/** RFC 1952 encoder that writes one streaming GZIP member. */
private class IncrementalGzipEncoder(
    private val limits: SseLimits,
) : SseContentEncoder {
    private val deflater = IncrementalDeflaterEncoder(nowrap = true, limits)
    private val crc = CRC32()
    private var inputBytes = 0L
    private var headerWritten = false
    private var finished = false

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (finished) return SseContentCodecResult.Failure(SseContentCodecFailure.ENCODER_FAILURE)
        crc.update(input)
        inputBytes = (inputBytes + input.size) and UNSIGNED_INT_MASK
        val encoded = when (val result = deflater.accept(input, endOfInput)) {
            is SseContentCodecResult.Failure -> return result
            is SseContentCodecResult.Output -> result.copyBytes()
        }
        val prefix = if (headerWritten) ByteArray(0) else GZIP_HEADER.also { headerWritten = true }
        val suffix = if (endOfInput) gzipTrailer(crc.value, inputBytes).also { finished = true } else ByteArray(0)
        val outputBytes = prefix.size + encoded.size + suffix.size
        if (outputBytes > limits.maximumDecodedBytesPerChunk) {
            return SseContentCodecResult.Failure(SseContentCodecFailure.DECODED_CHUNK_LIMIT)
        }
        return SseContentCodecResult.Output(prefix + encoded + suffix)
    }

    override fun close() {
        deflater.close()
        finished = true
    }
}

/** Shared streaming Deflater adapter with bounded per-call output. */
private class IncrementalDeflaterEncoder(
    nowrap: Boolean,
    private val limits: SseLimits,
) : SseContentEncoder {
    private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)
    private var finished = false

    override fun accept(input: ByteArray, endOfInput: Boolean): SseContentCodecResult {
        if (finished) return SseContentCodecResult.Failure(SseContentCodecFailure.ENCODER_FAILURE)
        deflater.setInput(input)
        if (endOfInput) deflater.finish()
        val output = BoundedChunkCollector(limits.maximumDecodedBytesPerChunk)
        val buffer = ByteArray(DECODE_BUFFER_BYTES)
        while (true) {
            val encoded = if (endOfInput) {
                deflater.deflate(buffer)
            } else {
                deflater.deflate(buffer, 0, buffer.size, Deflater.SYNC_FLUSH)
            }
            if (encoded > 0 && !output.append(buffer, 0, encoded)) {
                deflater.end()
                finished = true
                return SseContentCodecResult.Failure(SseContentCodecFailure.DECODED_CHUNK_LIMIT)
            }
            if (endOfInput && deflater.finished()) break
            if (!endOfInput && deflater.needsInput() && encoded == 0) break
            if (encoded == 0 && !deflater.needsInput()) {
                deflater.end()
                finished = true
                return SseContentCodecResult.Failure(SseContentCodecFailure.ENCODER_FAILURE)
            }
        }
        if (endOfInput) {
            deflater.end()
            finished = true
        }
        return SseContentCodecResult.Output(output.toByteArray())
    }

    override fun close() {
        if (!finished) deflater.end()
        finished = true
    }
}

/** Cumulative expansion guard that remains valid for an endless stream. */
private class ExpansionGuard(private val limits: SseLimits) {
    private var encodedBytes = 0L
    private var decodedBytes = 0L

    fun observeInput(bytes: Int) {
        encodedBytes = saturatedAdd(encodedBytes, bytes.toLong())
    }

    fun observeOutput(bytes: Int) {
        decodedBytes = saturatedAdd(decodedBytes, bytes.toLong())
    }

    fun isAllowed(): Boolean {
        val ratioAllowance = saturatedMultiply(encodedBytes, limits.maximumDecoderExpansionRatio.toLong())
        val allowed = saturatedAdd(limits.maximumDecoderExpansionGraceBytes.toLong(), ratioAllowance)
        return decodedBytes <= allowed
    }
}

/** Fixed-limit chunk collector used only for one codec call. */
private class BoundedChunkCollector(private val maximumBytes: Int) {
    private val chunks = mutableListOf<ByteArray>()
    private var size = 0

    fun append(source: ByteArray, offset: Int, length: Int): Boolean {
        if (length > maximumBytes - size) return false
        chunks += source.copyOfRange(offset, offset + length)
        size += length
        return true
    }

    fun toByteArray(): ByteArray {
        if (size == 0) return ByteArray(0)
        val output = ByteArray(size)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }
}

private fun inflateInto(
    inflater: Inflater,
    input: ByteArray,
    onDecoded: (ByteArray, Int, Int) -> SseContentCodecFailure?,
): SseContentCodecFailure? {
    inflater.setInput(input)
    val buffer = ByteArray(DECODE_BUFFER_BYTES)
    return try {
        while (true) {
            val decoded = inflater.inflate(buffer)
            if (decoded > 0) {
                onDecoded(buffer, 0, decoded)?.let { return it }
                continue
            }
            when {
                inflater.finished() || inflater.needsInput() -> return null
                inflater.needsDictionary() -> return SseContentCodecFailure.MALFORMED_STREAM
                else -> return SseContentCodecFailure.MALFORMED_STREAM
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    } catch (_: DataFormatException) {
        SseContentCodecFailure.MALFORMED_STREAM
    }
}

private fun isZlibHeader(first: Byte, second: Byte): Boolean {
    val cmf = first.toInt() and 0xff
    val flg = second.toInt() and 0xff
    return cmf and 0x0f == 8 && (cmf shl 8 or flg) % 31 == 0
}

private fun gzipHeaderLength(bytes: ByteArray, size: Int): Int? {
    if (size < GZIP_FIXED_HEADER_BYTES) return null
    if ((bytes[0].toInt() and 0xff) != GZIP_ID_ONE || (bytes[1].toInt() and 0xff) != GZIP_ID_TWO) {
        throw DataFormatException("Invalid GZIP magic.")
    }
    if ((bytes[2].toInt() and 0xff) != GZIP_DEFLATE_METHOD) throw DataFormatException("Invalid GZIP method.")
    val flags = bytes[3].toInt() and 0xff
    if (flags and GZIP_RESERVED_FLAGS != 0) throw DataFormatException("Invalid GZIP flags.")
    var offset = GZIP_FIXED_HEADER_BYTES
    if (flags and GZIP_FLAG_EXTRA != 0) {
        if (size < offset + 2) return null
        val extraLength = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        offset += 2
        if (size < offset + extraLength) return null
        offset += extraLength
    }
    if (flags and GZIP_FLAG_NAME != 0) offset = terminatedFieldEnd(bytes, size, offset) ?: return null
    if (flags and GZIP_FLAG_COMMENT != 0) offset = terminatedFieldEnd(bytes, size, offset) ?: return null
    if (flags and GZIP_FLAG_HEADER_CRC != 0) {
        if (size < offset + 2) return null
        val expected = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        val actual = CRC32().apply { update(bytes, 0, offset) }.value.toInt() and 0xffff
        if (expected != actual) throw DataFormatException("Invalid GZIP header CRC.")
        offset += 2
    }
    return offset
}

private fun validGzipPrefix(bytes: ByteArray, size: Int): Boolean = when (size) {
    0 -> true
    1 -> (bytes[0].toInt() and 0xff) == GZIP_ID_ONE
    2 -> (bytes[1].toInt() and 0xff) == GZIP_ID_TWO
    3 -> (bytes[2].toInt() and 0xff) == GZIP_DEFLATE_METHOD
    else -> (bytes[3].toInt() and GZIP_RESERVED_FLAGS) == 0
}

private fun terminatedFieldEnd(bytes: ByteArray, size: Int, start: Int): Int? {
    for (index in start until size) if (bytes[index] == 0.toByte()) return index + 1
    return null
}

private fun readLittleEndianUnsignedInt(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xffL) or
        ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
        ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
        ((bytes[offset + 3].toLong() and 0xffL) shl 24)

private fun gzipTrailer(crc: Long, size: Long): ByteArray = ByteArray(GZIP_TRAILER_BYTES).also { bytes ->
    writeLittleEndianUnsignedInt(bytes, 0, crc)
    writeLittleEndianUnsignedInt(bytes, 4, size)
}

private fun writeLittleEndianUnsignedInt(bytes: ByteArray, offset: Int, value: Long) {
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun saturatedMultiply(left: Long, right: Long): Long =
    if (left != 0L && Long.MAX_VALUE / left < right) Long.MAX_VALUE else left * right

private const val DECODE_BUFFER_BYTES: Int = 8 * 1_024
private const val GZIP_FIXED_HEADER_BYTES: Int = 10
private const val GZIP_TRAILER_BYTES: Int = 8
private const val GZIP_ID_ONE: Int = 0x1f
private const val GZIP_ID_TWO: Int = 0x8b
private const val GZIP_DEFLATE_METHOD: Int = 8
private const val GZIP_RESERVED_FLAGS: Int = 0xe0
private const val GZIP_FLAG_HEADER_CRC: Int = 0x02
private const val GZIP_FLAG_EXTRA: Int = 0x04
private const val GZIP_FLAG_NAME: Int = 0x08
private const val GZIP_FLAG_COMMENT: Int = 0x10
private const val UNSIGNED_INT_MASK: Long = 0xffff_ffffL
private val GZIP_HEADER: ByteArray = byteArrayOf(
    0x1f,
    0x8b.toByte(),
    0x08,
    0x00,
    0x00,
    0x00,
    0x00,
    0x00,
    0x00,
    0xff.toByte(),
)
