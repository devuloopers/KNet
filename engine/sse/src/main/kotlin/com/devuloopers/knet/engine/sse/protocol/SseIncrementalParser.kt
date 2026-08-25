package com.devuloopers.knet.engine.sse.protocol

/** Semantic class of one terminated SSE record. */
enum class SseRecordKind {
    EVENT,
    COMMENT,
    STATE_UPDATE,
}

/** One bounded, blank-line-terminated SSE record and its derived WHATWG semantics. */
class SseParsedRecord(
    val kind: SseRecordKind,
    val eventType: String?,
    val data: String?,
    val lastEventId: String,
    val retryMillis: Long?,
    val comments: List<String>,
    rawRecord: ByteArray,
) {
    private val rawBytes: ByteArray = rawRecord.copyOf()

    /** Returns an owned copy of the exact decoded-stream bytes including the terminating blank line. */
    fun copyRawRecord(): ByteArray = rawBytes.copyOf()
}

/** Explicit bounded parser output; malformed or oversized records never escape as exceptions. */
sealed interface SseParseResult {
    /** Successfully framed and semantically interpreted record. */
    data class Record(val value: SseParsedRecord) : SseParseResult

    /** A record that could not be retained within the configured parser limits. */
    data class Gap(val reason: String, val observedBytes: Long) : SseParseResult {
        init {
            require(reason.isNotBlank()) { "SSE parser gap reason must not be blank." }
            require(observedBytes >= 0L) { "SSE parser gap bytes must not be negative." }
        }
    }
}

/**
 * Incremental WHATWG-compatible event-stream parser.
 *
 * The parser accepts arbitrary chunk boundaries, retains at most one bounded record, recognizes
 * LF/CRLF/CR delimiters, strips one initial UTF-8 BOM, and discards an unterminated record at EOF.
 * Instances are stream-confined and are not thread-safe.
 */
class SseIncrementalParser(
    private val limits: SseLimits = SseLimits(),
    initialLastEventId: String = "",
) {
    private val rawRecord = BoundedByteAccumulator(limits.maximumRecordBytes)
    private val line = BoundedByteAccumulator(limits.maximumLineBytes)
    private val dataLines = mutableListOf<String>()
    private val comments = mutableListOf<String>()
    private var eventType: String? = null
    private var lastEventId: String = initialLastEventId
    private var retryMillis: Long? = null
    private var recordRetryMillis: Long? = null
    private var pendingCarriageReturn = false
    private var firstLine = true
    private var recordHasContent = false
    private var recordOverflow = false
    private var recordFailureReason: String? = null
    private var observedRecordBytes = 0L

    init {
        require(initialLastEventId.length <= limits.maximumEventIdCharacters) {
            "Initial SSE event ID exceeds the configured limit."
        }
        require(NULL !in initialLastEventId) { "Initial SSE event ID must not contain U+0000." }
    }

    /** Parses one owned byte chunk and returns every record completed by this chunk. */
    fun accept(bytes: ByteArray): List<SseParseResult> {
        if (bytes.isEmpty()) return emptyList()
        val output = mutableListOf<SseParseResult>()
        bytes.forEach { byte -> acceptByte(byte, output) }
        return output
    }

    /**
     * Ends the stream. A CR delimiter is resolved, while a non-blank pending record is discarded as
     * required by the event-stream algorithm.
     */
    fun finish(): List<SseParseResult> {
        val output = mutableListOf<SseParseResult>()
        if (pendingCarriageReturn) {
            pendingCarriageReturn = false
            completeLine(output)
        }
        resetRecord()
        return output
    }

    /** Current sticky event ID buffer, useful for explicit reconnect presentation. */
    fun currentLastEventId(): String = lastEventId

    /** Current valid reconnection time in milliseconds, or null before a valid `retry` field. */
    fun currentRetryMillis(): Long? = retryMillis

    private fun acceptByte(byte: Byte, output: MutableList<SseParseResult>) {
        if (pendingCarriageReturn) {
            pendingCarriageReturn = false
            if (byte == LF) {
                appendRaw(byte)
                completeLine(output)
                return
            }
            completeLine(output)
        }

        when (byte) {
            CR -> {
                appendRaw(byte)
                pendingCarriageReturn = true
            }
            LF -> {
                appendRaw(byte)
                completeLine(output)
            }
            else -> {
                appendRaw(byte)
                if (!line.append(byte)) recordOverflow = true
            }
        }
    }

    private fun appendRaw(byte: Byte) {
        observedRecordBytes++
        if (!rawRecord.append(byte)) recordOverflow = true
    }

    private fun completeLine(output: MutableList<SseParseResult>) {
        if (line.size == 0) {
            firstLine = false
            completeRecord(output)
            return
        }
        recordHasContent = true
        val stripInitialBom = firstLine
        firstLine = false
        if (!recordOverflow) parseLine(line.copyBytes(), stripInitialBom)
        line.clear()
    }

    private fun parseLine(bytes: ByteArray, stripInitialBom: Boolean) {
        var text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
            .getOrElse {
                recordFailureReason = MALFORMED_UTF8
                return
            }
        if (stripInitialBom) text = text.removePrefix(UTF8_BOM)
        if (text.startsWith(':')) {
            comments += text.drop(1).removePrefix(" ")
            return
        }
        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        val rawValue = if (colon < 0) "" else text.substring(colon + 1)
        val value = rawValue.removePrefix(" ")
        when (field) {
            DATA -> {
                val totalCharacters = dataLines.sumOf(String::length) + dataLines.size + value.length
                if (totalCharacters > limits.maximumDataCharacters) recordOverflow = true else dataLines += value
            }
            EVENT -> if (value.length <= limits.maximumEventTypeCharacters) {
                eventType = value
            } else {
                recordOverflow = true
            }
            ID -> if (NULL !in value) {
                if (value.length <= limits.maximumEventIdCharacters) lastEventId = value else recordOverflow = true
            }
            RETRY -> if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
                value.toLongOrNull()?.let { parsed ->
                    retryMillis = parsed
                    recordRetryMillis = parsed
                }
            }
        }
    }

    private fun completeRecord(output: MutableList<SseParseResult>) {
        val failureReason = recordFailureReason
        if (recordOverflow || failureReason != null) {
            output += SseParseResult.Gap(failureReason ?: RECORD_LIMIT_EXCEEDED, observedRecordBytes)
        } else if (recordHasContent) {
            val kind = when {
                dataLines.isNotEmpty() -> SseRecordKind.EVENT
                comments.isNotEmpty() -> SseRecordKind.COMMENT
                else -> SseRecordKind.STATE_UPDATE
            }
            output += SseParseResult.Record(
                SseParsedRecord(
                    kind = kind,
                    eventType = if (kind == SseRecordKind.EVENT) eventType.orEmpty().ifEmpty { DEFAULT_EVENT_TYPE } else null,
                    data = if (kind == SseRecordKind.EVENT) dataLines.joinToString("\n") else null,
                    lastEventId = lastEventId,
                    retryMillis = recordRetryMillis,
                    comments = comments.toList(),
                    rawRecord = rawRecord.copyBytes(),
                ),
            )
        }
        resetRecord()
    }

    private fun resetRecord() {
        rawRecord.clear()
        line.clear()
        dataLines.clear()
        comments.clear()
        eventType = null
        recordRetryMillis = null
        recordHasContent = false
        recordOverflow = false
        recordFailureReason = null
        observedRecordBytes = 0L
    }

    private companion object {
        const val DATA: String = "data"
        const val EVENT: String = "event"
        const val ID: String = "id"
        const val RETRY: String = "retry"
        const val DEFAULT_EVENT_TYPE: String = "message"
        const val RECORD_LIMIT_EXCEEDED: String = "sse_record_limit_exceeded"
        const val MALFORMED_UTF8: String = "sse_malformed_utf8"
        const val UTF8_BOM: String = "\uFEFF"
        const val NULL: Char = '\u0000'
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}

/** Fixed-capacity byte storage that never reallocates in response to untrusted stream input. */
private class BoundedByteAccumulator(maximumBytes: Int) {
    private val storage = ByteArray(maximumBytes)
    var size: Int = 0
        private set

    fun append(value: Byte): Boolean {
        if (size == storage.size) return false
        storage[size++] = value
        return true
    }

    fun copyBytes(): ByteArray = storage.copyOf(size)

    fun clear() {
        size = 0
    }
}
