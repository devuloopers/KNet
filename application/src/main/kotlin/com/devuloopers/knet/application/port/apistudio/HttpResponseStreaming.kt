package com.devuloopers.knet.application.port.apistudio

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead

/**
 * One bounded protocol-neutral record derived from a live HTTP response body.
 *
 * @property sequence Monotonic one-based order within the current response.
 * @property title Short semantic label suitable for a timeline row.
 * @property attributes Ordered presentation metadata derived by the interpreter.
 * @property data Decoded record payload intended for detail inspection.
 * @property raw Complete bounded source record intended for evidence/copy actions.
 */
public data class HttpLiveResponseRecord(
    public val sequence: Long,
    public val title: String,
    public val attributes: List<Pair<String, String>>,
    public val data: String,
    public val raw: String,
) {
    init {
        require(sequence > 0L) { "Live HTTP response record sequence must be positive." }
        require(title.isNotBlank()) { "Live HTTP response record title must not be blank." }
    }
}

/** Bounded semantic updates emitted by one HTTP response interpreter. */
public sealed interface HttpLiveResponseUpdate {
    /** Delivers one complete semantic record. */
    public data class Record(public val value: HttpLiveResponseRecord) : HttpLiveResponseUpdate

    /** Reports bounded input that could not be represented as a record. */
    public data class Gap(
        public val reason: String,
        public val observedBytes: Long,
    ) : HttpLiveResponseUpdate
}

/** Stream-confined interpreter session created only after a matching response head is received. */
public interface HttpResponseStreamInterpreterSession {
    /** Short protocol label rendered by the generic response timeline. */
    public val protocolLabel: String

    /** Maximum number of records the presentation layer may retain for this session. */
    public val maximumRetainedRecords: Int

    /** Consumes one owned response-body chunk and returns zero or more ordered semantic updates. */
    public fun accept(bytes: ByteArray): List<HttpLiveResponseUpdate>

    /** Completes parsing and returns any final bounded updates. */
    public fun finish(): List<HttpLiveResponseUpdate>
}

/** Additive semantic contribution for one long-lived HTTP response media type. */
public interface HttpResponseStreamInterpreter {
    /** Stable contribution identity used to reject duplicate product bindings. */
    public val id: String

    /** Returns whether this contribution owns semantic interpretation of [head]. */
    public fun supports(head: HttpExecutionResponseHead): Boolean

    /** Opens one stream-confined session for a previously supported [head]. */
    public fun open(head: HttpExecutionResponseHead): HttpResponseStreamInterpreterSession
}

/** Immutable interpreter registry assembled at the product composition root. */
public class HttpResponseStreamInterpreterRegistry(
    interpreters: List<HttpResponseStreamInterpreter> = emptyList(),
) {
    private val ordered = interpreters.toList().also { values ->
        require(values.map(HttpResponseStreamInterpreter::id).distinct().size == values.size) {
            "HTTP response stream interpreter IDs must be unique."
        }
    }

    /** Opens the first registered interpreter that recognizes [head], or returns null for ordinary HTTP. */
    public fun open(head: HttpExecutionResponseHead): HttpResponseStreamInterpreterSession? =
        ordered.firstOrNull { it.supports(head) }?.open(head)
}
