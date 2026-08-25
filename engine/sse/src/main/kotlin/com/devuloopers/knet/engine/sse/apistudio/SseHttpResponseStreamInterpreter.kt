package com.devuloopers.knet.engine.sse.apistudio

import com.devuloopers.knet.application.port.apistudio.HttpLiveResponseRecord
import com.devuloopers.knet.application.port.apistudio.HttpLiveResponseUpdate
import com.devuloopers.knet.application.port.apistudio.HttpResponseStreamInterpreter
import com.devuloopers.knet.application.port.apistudio.HttpResponseStreamInterpreterSession
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.engine.sse.protocol.SseRecordKind

/** Interprets identity-encoded `text/event-stream` responses for the existing HTTP API Studio workspace. */
class SseHttpResponseStreamInterpreter(
    private val limits: SseLimits = SseLimits(),
) : HttpResponseStreamInterpreter {
    override val id: String = "sse"

    override fun supports(head: HttpExecutionResponseHead): Boolean {
        val contentType = head.headers.header(SseProtocol.CONTENT_TYPE)
        val contentEncoding = head.headers.header(SseProtocol.CONTENT_ENCODING)
        return SseProtocol.isEventStream(contentType) &&
            (contentEncoding.isNullOrBlank() || contentEncoding.equals("identity", ignoreCase = true))
    }

    override fun open(head: HttpExecutionResponseHead): HttpResponseStreamInterpreterSession {
        require(supports(head)) { "The response is not an identity-encoded SSE stream." }
        return Session(limits)
    }

    private class Session(
        private val limits: SseLimits,
    ) : HttpResponseStreamInterpreterSession {
        override val protocolLabel: String = "SSE"
        override val maximumRetainedRecords: Int = limits.maximumRetainedApiStudioEvents
        private val parser = SseIncrementalParser(limits)
        private var sequence = 0L

        override fun accept(bytes: ByteArray): List<HttpLiveResponseUpdate> = parser.accept(bytes).map(::map)

        override fun finish(): List<HttpLiveResponseUpdate> = parser.finish().map(::map)

        private fun map(result: SseParseResult): HttpLiveResponseUpdate = when (result) {
            is SseParseResult.Gap -> HttpLiveResponseUpdate.Gap(result.reason, result.observedBytes)
            is SseParseResult.Record -> {
                val record = result.value
                val attributes = buildList {
                    record.eventType?.let { add("Event" to it) }
                    record.lastEventId.takeIf(String::isNotBlank)?.let { add("ID" to it) }
                    record.retryMillis?.let { add("Retry" to "$it ms") }
                    if (record.comments.isNotEmpty()) add("Comments" to record.comments.size.toString())
                }
                val title = when (record.kind) {
                    SseRecordKind.EVENT -> record.eventType ?: "message"
                    SseRecordKind.COMMENT -> "Keep-alive comment"
                    SseRecordKind.STATE_UPDATE -> "Stream state"
                }
                HttpLiveResponseUpdate.Record(
                    HttpLiveResponseRecord(
                        sequence = ++sequence,
                        title = title,
                        attributes = attributes,
                        data = record.data ?: record.comments.joinToString("\n"),
                        raw = record.copyRawRecord().decodeToString(),
                    ),
                )
            }
        }
    }
}

private fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { (candidate, _) -> candidate.equals(name, ignoreCase = true) }?.value
