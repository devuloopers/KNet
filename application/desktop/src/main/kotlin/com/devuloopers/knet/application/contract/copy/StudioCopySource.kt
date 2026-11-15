package com.devuloopers.knet.application.contract.copy

import com.devuloopers.knet.application.contract.traffic.BodyAccess
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePage
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.Authority

/** Native editor evidence preserves protocol semantics without inventing an HTTP response. */
public data class StudioProtocolCopyRecord(
    /** Execution-local identity. */ public val id: String,
    /** Actual native protocol contribution. */ public val kind: RequestKindId,
    /** Target submitted at execution time. */ public val target: String,
    /** Submitted authority, separate from a URI path or service/method. */ public val destination: Authority,
    /** Recorded operation/service/method, when supplied by the native editor. */ public val operation: String?,
    /** Submitted metadata in original order. */ public val requestHeaders: List<HeaderField>,
    /** Received handshake/response metadata. */ public val responseHeaders: List<HeaderField>,
    /** Received terminal metadata. */ public val trailers: List<HeaderField>,
    /** Explicit execution lifecycle. */ public val state: StudioCopyState,
    /** Recorded native status; it is never labelled an HTTP status. */ public val status: String?,
    /** Recorded safe failure code. */ public val failure: String?,
)

/** Closed execution snapshot lifecycle, including retained partial data after cancellation. */
public enum class StudioCopyState { ACTIVE, COMPLETED, FAILED, PARTIAL }

/**
 * One immutable bounded execution snapshot, independent of Traffic and the mutable editor draft.
 * Only one of [http] or [protocol] is present; payload handles expose no filesystem paths.
 */
public class StudioCopySnapshot(
    /** Owner that supplied this snapshot. */ public val id: String,
    /** Actual HTTP execution evidence, when this is an HTTP editor. */ public val http: HttpExchangeSnapshot?,
    /** Actual native execution evidence otherwise. */ public val protocol: StudioProtocolCopyRecord?,
    /** Bounded first page of retained framed/native records. */ public val messages: ProtocolMessagePage,
    chunks: Map<BodyId, BodyChunk>,
    /** Explicit source representation/partial-retention explanation. */ public val note: String? = null,
    /** Bounded engine-decoded native messages, kept separate from the original captured bytes. */
    messagePresentations: Map<ProtocolMessageId, ProtocolMessagePresentation> = emptyMap(),
    /** True only when the transport exposed the sent HTTP request representation. */
    public val requestTransportCaptured: Boolean = false,
) : BodyAccess {
    private val payloads = chunks.toMap()
    /** Engine interpretations for this bounded message page; separate from original payload handles. */
    public val messagePresentations: Map<ProtocolMessageId, ProtocolMessagePresentation> = messagePresentations.toMap()
    init {
        require((http == null) != (protocol == null)) { "A Studio copy snapshot must have exactly one parent." }
        require(chunks.values.sumOf { it.size.toLong() } <= 655_360L) { "Studio clipboard snapshot exceeds its payload budget." }
        require(messagePresentations.size <= 32 && messagePresentations.values.sumOf { it.text.length.toLong() } <= 524_288) { "Studio interpreted message snapshot exceeds its budget." }
        require(messages.items.size <= 32) { "Studio clipboard message snapshot exceeds its page budget." }
    }
    /** Returns only the already owned bounded prefix; a subsequent clear cannot change this snapshot. */
    override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
        val chunk = checkNotNull(payloads[bodyId]) { "This execution body is unavailable." }
        val bytes = chunk.copyBytes(); require(range.offset <= bytes.size) { "Body range is outside the copied prefix." }
        val start = range.offset.toInt(); val end = minOf(bytes.size.toLong(), range.offset + range.length).toInt()
        return BodyChunk(bytes.copyOfRange(start, end), range.offset, end == bytes.size && chunk.endOfBody)
    }
}

/** Reader of execution-owned evidence. Adapters copy bounded prefixes atomically while their owner exists. */
public fun interface StudioCopySource {
    /** Fails for missing owners or absent server response; never substitutes the current draft or Traffic. */
    public suspend fun snapshot(id: String, preset: TrafficCopyPreset): StudioCopySnapshot
}

/** Bounded reads required by one preset; metadata-only actions never open payload files. */
public data class StudioCopyPayloadNeeds(
    /** Whether the submitted HTTP body is needed. */ public val request: Boolean,
    /** Whether received HTTP bytes are needed. */ public val response: Boolean,
    /** Whether a bounded native/framed message page is needed. */ public val messages: Boolean,
) {
    public companion object {
        /** Maps the closed preset set to its minimum required payload categories. */
        public fun forPreset(preset: TrafficCopyPreset): StudioCopyPayloadNeeds {
            val both = preset in setOf(TrafficCopyPreset.FULL, TrafficCopyPreset.STRUCTURED_JSON, TrafficCopyPreset.REQUEST_RESPONSE_BODIES, TrafficCopyPreset.REQUEST_RESPONSE_HEADERS_BODIES)
            return StudioCopyPayloadNeeds(both || preset in setOf(TrafficCopyPreset.REQUEST_BODY, TrafficCopyPreset.REQUEST_HEADERS_BODY, TrafficCopyPreset.REQUEST_BASE64, TrafficCopyPreset.REQUEST_HEX,
                TrafficCopyPreset.URL_REQUEST_BODY, TrafficCopyPreset.URL_REQUEST_HEADERS_BODY, TrafficCopyPreset.CURL, TrafficCopyPreset.GRAPHQL_QUERY, TrafficCopyPreset.GRAPHQL_VARIABLES,
                TrafficCopyPreset.GRAPHQL_EXTENSIONS, TrafficCopyPreset.GRAPHQL_OPERATION, TrafficCopyPreset.GRAPHQL_REQUEST),
                both || preset in setOf(TrafficCopyPreset.SSE_EVENTS, TrafficCopyPreset.SSE_DATA, TrafficCopyPreset.RESPONSE_BODY, TrafficCopyPreset.RESPONSE_ORIGINAL_TEXT, TrafficCopyPreset.RESPONSE_HEADERS_BODY, TrafficCopyPreset.RESPONSE_BASE64,
                    TrafficCopyPreset.RESPONSE_HEX, TrafficCopyPreset.URL_RESPONSE_BODY, TrafficCopyPreset.URL_RESPONSE_HEADERS_BODY),
                preset in setOf(TrafficCopyPreset.FULL, TrafficCopyPreset.STRUCTURED_JSON, TrafficCopyPreset.WEBSOCKET_MESSAGES, TrafficCopyPreset.WEBSOCKET_CLIENT_MESSAGES,
                    TrafficCopyPreset.WEBSOCKET_SERVER_MESSAGES, TrafficCopyPreset.GRPC_MESSAGES, TrafficCopyPreset.SSE_EVENTS, TrafficCopyPreset.SSE_DATA))
        }
    }
}
