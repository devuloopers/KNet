package com.devuloopers.knet.engine.sse.integration.apistudio

import com.devuloopers.knet.application.contract.apistudio.HttpLiveResponseRecord
import com.devuloopers.knet.application.contract.apistudio.HttpLiveResponseUpdate
import com.devuloopers.knet.application.contract.apistudio.HttpResponseStreamInterpreter
import com.devuloopers.knet.application.contract.apistudio.HttpResponseStreamInterpreterSession
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecPlanResult
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecRegistry
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecResult
import com.devuloopers.knet.engine.sse.encoding.SseContentDecoder
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.engine.sse.protocol.SseRecordKind

/** Interprets bounded identity, gzip, and deflate SSE responses in the existing HTTP API Studio workspace. */
class SseHttpResponseStreamInterpreter(
    private val limits: SseLimits = SseLimits(),
) : HttpResponseStreamInterpreter {
    override val id: String = "sse"
    private val codecs = SseContentCodecRegistry(limits)

    override fun supports(head: HttpExecutionResponseHead): Boolean =
        SseProtocol.isEventStream(head.headers.header(SseProtocol.CONTENT_TYPE))

    override fun open(head: HttpExecutionResponseHead): HttpResponseStreamInterpreterSession {
        require(supports(head)) { "The response is not an SSE stream." }
        return Session(limits, codecs.resolve(head.headers.header(SseProtocol.CONTENT_ENCODING)))
    }

    private class Session(
        private val limits: SseLimits,
        planResult: SseContentCodecPlanResult,
    ) : HttpResponseStreamInterpreterSession {
        override val protocolLabel: String = "SSE"
        override val maximumRetainedRecords: Int = limits.maximumRetainedApiStudioEvents
        private val parser = SseIncrementalParser(limits)
        private val unavailableReason = (planResult as? SseContentCodecPlanResult.Unavailable)?.reason
        private val decoder: SseContentDecoder? =
            (planResult as? SseContentCodecPlanResult.Supported)?.plan?.openDecoder()
        private var sequence = 0L
        private var observedBytes = 0L
        private var detached = false

        override fun accept(bytes: ByteArray): List<HttpLiveResponseUpdate> {
            observedBytes = saturatedAdd(observedBytes, bytes.size.toLong())
            if (detached) return emptyList()
            unavailableReason?.let { reason ->
                detached = true
                return listOf(HttpLiveResponseUpdate.Gap(reason.code, observedBytes))
            }
            return when (val result = requireNotNull(decoder).accept(bytes, endOfInput = false)) {
                is SseContentCodecResult.Failure -> {
                    decoder.close()
                    detached = true
                    listOf(HttpLiveResponseUpdate.Gap(result.reason.code, observedBytes))
                }
                is SseContentCodecResult.Output -> parser.accept(result.copyBytes()).map(::map)
            }
        }

        override fun finish(): List<HttpLiveResponseUpdate> {
            if (detached) return emptyList()
            unavailableReason?.let { reason ->
                detached = true
                return listOf(HttpLiveResponseUpdate.Gap(reason.code, observedBytes))
            }
            return when (val result = requireNotNull(decoder).accept(ByteArray(0), endOfInput = true)) {
                is SseContentCodecResult.Failure -> {
                    decoder.close()
                    detached = true
                    listOf(HttpLiveResponseUpdate.Gap(result.reason.code, observedBytes))
                }
                is SseContentCodecResult.Output -> {
                    decoder.close()
                    buildList {
                        addAll(parser.accept(result.copyBytes()).map(::map))
                        addAll(parser.finish().map(::map))
                    }
                }
            }
        }

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

private fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
