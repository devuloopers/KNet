package com.devuloopers.knet.engine.sse.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointInterceptionUnit
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.port.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.port.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolObservation
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable identities used by SSE response-record breakpoint rules. */
object SseBreakpointProtocol {
    val id: BreakpointProtocolId = BreakpointProtocolId("sse")
    val eventTypeFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("event-type")
    val eventIdFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("event-id")
    val dataFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("data-contains")
}

/** Message-level breakpoint contribution for complete SSE response records. */
class SseBreakpointExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val limits: SseLimits = SseLimits(),
) : BreakpointProtocolExtension {
    private val lastEventIds = ConcurrentHashMap<ExchangeId, String>()

    override val suggestionPriority: Int = 180
    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = SseBreakpointProtocol.id,
        displayName = "Server-Sent Events",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = SseBreakpointProtocol.eventTypeFieldId,
                label = "Event Type",
                description = "Exact value or a wildcard using * and ?. Empty matches every event type.",
                placeholder = "e.g. price-update",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = SseBreakpointProtocol.eventIdFieldId,
                label = "Event ID",
                description = "Exact value or a wildcard. Empty matches every current Last-Event-ID value.",
                placeholder = "e.g. order-*",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = SseBreakpointProtocol.dataFieldId,
                label = "Data Contains",
                description = "Optional bounded literal text that must occur in the joined event data.",
                placeholder = "e.g. declined",
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != SseBreakpointProtocol.id) return null
        return decode(criteria.encodedPayload)?.let(::SseCompiledCriteria)
    }

    /** HTTP exchange inspection is disabled because SSE rules pause complete response records. */
    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation? {
        if (input.direction != TrafficDirection.SERVER_TO_CLIENT || input.kind != ProtocolMessageKind.RECORD) return null
        val initialId = lastEventIds[input.exchangeId].orEmpty()
        val parser = SseIncrementalParser(limits = limits, initialLastEventId = initialId)
        val record = (parser.accept(input.body.copyBytes()).singleOrNull() as? SseParseResult.Record)?.value ?: return null
        lastEventIds[input.exchangeId] = record.lastEventId
        return SseObservation(record.eventType, record.lastEventId, record.data)
    }

    override fun releaseMessages(exchangeId: ExchangeId) {
        lastEventIds.remove(exchangeId)
    }

    override fun validateMessageReplacement(
        input: ProtocolMessageInspectionInput,
        replacement: com.devuloopers.knet.application.port.breakpoint.BreakpointBody,
    ): Boolean {
        val bytes = replacement.copyBytes()
        val results = SseIncrementalParser(limits).accept(bytes)
        val record = (results.singleOrNull() as? SseParseResult.Record)?.value ?: return false
        return record.copyRawRecord().size == bytes.size
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { it.protocolId == SseBreakpointProtocol.id }
            ?.encodedPayload
            ?.let(::decode)
            ?: SseCriteria()
        return listOf(
            ProtocolCriteriaValue(SseBreakpointProtocol.eventTypeFieldId, decoded.eventType.orEmpty()),
            ProtocolCriteriaValue(SseBreakpointProtocol.eventIdFieldId, decoded.eventId.orEmpty()),
            ProtocolCriteriaValue(SseBreakpointProtocol.dataFieldId, decoded.dataContains.orEmpty()),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        if (values.any { it.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { it.fieldId to it.value.trim() }
        val eventType = byId[SseBreakpointProtocol.eventTypeFieldId].orEmpty().takeIf(String::isNotEmpty)
        val eventId = byId[SseBreakpointProtocol.eventIdFieldId].orEmpty().takeIf(String::isNotEmpty)
        val data = byId[SseBreakpointProtocol.dataFieldId].orEmpty().takeIf(String::isNotEmpty)
        if (listOfNotNull(eventType, eventId, data).any { it.length > MAXIMUM_FIELD_CHARACTERS }) return null
        return ProtocolMatchCriteria(
            protocolId = SseBreakpointProtocol.id,
            encodedPayload = buildJsonObject {
                put(VERSION, CRITERIA_VERSION)
                put(EVENT_TYPE, eventType.orEmpty())
                put(EVENT_ID, eventId.orEmpty())
                put(DATA_CONTAINS, data.orEmpty())
            }.toString(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        if (!SseProtocol.isEventStream(input.response?.head?.headers.orEmpty())) return null
        return createCriteria(emptyList())
    }

    private fun decode(payload: String): SseCriteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { it !in JSON_FIELDS }) return null
        if ((root[VERSION] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        fun optional(key: String): String? = (root[key] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { it.length <= MAXIMUM_FIELD_CHARACTERS }
        val eventType = optional(EVENT_TYPE)
        val eventId = optional(EVENT_ID)
        val data = optional(DATA_CONTAINS)
        if ((root[EVENT_TYPE] as? JsonPrimitive)?.contentOrNull == null ||
            (root[EVENT_ID] as? JsonPrimitive)?.contentOrNull == null ||
            (root[DATA_CONTAINS] as? JsonPrimitive)?.contentOrNull == null
        ) return null
        return SseCriteria(eventType, eventId, data)
    }

    private data class SseCriteria(
        val eventType: String? = null,
        val eventId: String? = null,
        val dataContains: String? = null,
    )

    private data class SseObservation(
        val eventType: String?,
        val eventId: String,
        val data: String?,
    ) : ProtocolObservation {
        override val protocolId: BreakpointProtocolId = SseBreakpointProtocol.id
    }

    private class SseCompiledCriteria(
        private val criteria: SseCriteria,
    ) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = SseBreakpointProtocol.id

        override fun matches(observation: ProtocolObservation?): Boolean {
            val sse = observation as? SseObservation ?: return false
            return wildcardMatches(criteria.eventType, sse.eventType.orEmpty()) &&
                wildcardMatches(criteria.eventId, sse.eventId) &&
                (criteria.dataContains == null || sse.data?.contains(criteria.dataContains) == true)
        }

        private fun wildcardMatches(pattern: String?, value: String): Boolean {
            if (pattern == null) return true
            var patternIndex = 0
            var valueIndex = 0
            var starIndex = -1
            var retryValueIndex = -1
            while (valueIndex < value.length) {
                when {
                    patternIndex < pattern.length &&
                        (pattern[patternIndex] == '?' || pattern[patternIndex] == value[valueIndex]) -> {
                        patternIndex++
                        valueIndex++
                    }
                    patternIndex < pattern.length && pattern[patternIndex] == '*' -> {
                        starIndex = patternIndex++
                        retryValueIndex = valueIndex
                    }
                    starIndex >= 0 -> {
                        patternIndex = starIndex + 1
                        valueIndex = ++retryValueIndex
                    }
                    else -> return false
                }
            }
            while (patternIndex < pattern.length && pattern[patternIndex] == '*') patternIndex++
            return patternIndex == pattern.length
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val VERSION: String = "version"
        const val EVENT_TYPE: String = "eventType"
        const val EVENT_ID: String = "eventId"
        const val DATA_CONTAINS: String = "dataContains"
        const val MAXIMUM_FIELD_CHARACTERS: Int = 4_096
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 16_384
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            SseBreakpointProtocol.eventTypeFieldId,
            SseBreakpointProtocol.eventIdFieldId,
            SseBreakpointProtocol.dataFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION, EVENT_TYPE, EVENT_ID, DATA_CONTAINS)
    }
}
