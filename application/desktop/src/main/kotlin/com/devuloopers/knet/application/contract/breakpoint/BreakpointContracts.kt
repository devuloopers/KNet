package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.TrafficOrigin
import kotlinx.coroutines.flow.StateFlow

/** Bounded immutable body value exposed to an authorized breakpoint editor. */
public class BreakpointBody(bytes: ByteArray) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(content.size <= 64 * 1024 * 1024) { "Breakpoint body exceeds the application hard limit." }
    }

    /** Number of editable bytes. */
    public val size: Int
        get() = content.size

    /** Returns a defensive copy owned by the caller. */
    public fun copyBytes(): ByteArray = content.copyOf()

    /**
     * Returns a defensive prefix whose size does not exceed [maximumBytes].
     *
     * @param maximumBytes Positive upper bound for the returned copy.
     * @return Independent byte array containing at most the requested prefix.
     * @throws IllegalArgumentException when [maximumBytes] is not positive.
     */
    public fun copyBytes(maximumBytes: Int): ByteArray {
        require(maximumBytes > 0) { "Maximum breakpoint body copy size must be positive." }
        return content.copyOf(minOf(content.size, maximumBytes))
    }

    public override fun equals(other: Any?): Boolean =
        other is BreakpointBody && content.contentEquals(other.content)

    public override fun hashCode(): Int = content.contentHashCode()

    public override fun toString(): String = "BreakpointBody(size=$size)"
}

/** One engine-owned candidate presented to the application breakpoint coordinator. */
public data class BreakpointCandidate(
    public val exchangeId: ExchangeId,
    public val phase: BreakpointPhase,
    public val request: HttpRequestSnapshot,
    public val requestBody: BreakpointBody? = null,
    public val requestObservedBodyBytes: Long = requestBody?.size?.toLong() ?: 0L,
    public val response: HttpResponseSnapshot? = null,
    public val responseBody: BreakpointBody? = null,
    public val responseObservedBodyBytes: Long = responseBody?.size?.toLong() ?: 0L,
    public val retainedTransportBytes: Long = 0L,
    public val origin: TrafficOrigin = TrafficOrigin.ProxyClient,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(requestObservedBodyBytes >= 0L) { "Observed request body bytes must not be negative." }
        require(responseObservedBodyBytes >= 0L) { "Observed response body bytes must not be negative." }
        require(retainedTransportBytes >= 0L) { "Retained transport bytes must not be negative." }
        require(phase != BreakpointPhase.BOTH) { "A breakpoint candidate must have one concrete phase." }
        require(phase != BreakpointPhase.RESPONSE || response != null) {
            "A response breakpoint candidate requires response metadata."
        }
    }

    /** Total bytes retained while this candidate is pending. */
    public val retainedBytes: Long
        get() = (requestBody?.size ?: 0).toLong() +
            (responseBody?.size ?: 0).toLong() +
            retainedTransportBytes
}

/** Explicit ownership intent for an intercepted message body. */
public sealed interface BreakpointBodyEdit {
    /** Preserve the original transport bytes without decoding or copying them into an edit. */
    public data object Unchanged : BreakpointBodyEdit

    /** Replace the original body, including with an intentionally empty body. */
    public data class Replace(
        public val body: BreakpointBody,
    ) : BreakpointBodyEdit
}

/** Validated request replacement returned to the transport. */
public data class BreakpointRequestEdit(
    public val request: HttpRequestSnapshot,
    public val body: BreakpointBodyEdit = BreakpointBodyEdit.Unchanged,
)

/** Validated response replacement returned to the transport. */
public data class BreakpointResponseEdit(
    public val response: HttpResponseSnapshot,
    public val body: BreakpointBodyEdit = BreakpointBodyEdit.Unchanged,
)

/** Terminal decision for one matched breakpoint candidate. */
public sealed interface BreakpointDecision {
    /** Resume with the original message. */
    public data object ContinueUnchanged : BreakpointDecision

    /** Resume a request-phase interception with a validated request edit. */
    public data class ResumeRequest(
        public val edit: BreakpointRequestEdit,
    ) : BreakpointDecision

    /** Resume a response-phase interception with a validated response edit. */
    public data class ResumeResponse(
        public val edit: BreakpointResponseEdit,
    ) : BreakpointDecision

    /** Drop the exchange and close its transport. */
    public data object Drop : BreakpointDecision
}

/** Immutable pending record safe to expose to an authorized presentation. */
public data class PendingBreakpoint(
    public val id: String,
    public val ruleId: String,
    public val candidate: BreakpointCandidate,
)

/** Aggregate requirements read synchronously by the proxy pipeline at connection setup. */
public data class BreakpointRequirements(
    public val hasRequestRules: Boolean,
    public val hasResponseRules: Boolean,
    public val maxEditableBodyBytes: Int,
)

/** Independently configurable limits for the intentional breakpoint pause path. */
public data class BreakpointLimits(
    public val maxPendingConnections: Int = 32,
    public val maxPendingBytes: Long = 32L * 1024L * 1024L,
    public val maxEditableBodyBytes: Int = 10 * 1024 * 1024,
    public val maxEditedHeaderCount: Int = 512,
    public val maxEditedHeaderBytes: Int = 1024 * 1024,
    public val maxTrackedProtocolExchanges: Int = 1_024,
    public val decisionTimeoutMillis: Long = 120_000L,
) {
    init {
        require(maxPendingConnections in 1..10_000) { "Pending breakpoint limit is invalid." }
        require(maxPendingBytes in 1L..(1024L * 1024L * 1024L)) { "Pending byte limit is invalid." }
        require(maxEditableBodyBytes in 1..(64 * 1024 * 1024)) { "Editable body limit is invalid." }
        require(maxEditedHeaderCount in 1..10_000) { "Edited header count limit is invalid." }
        require(maxEditedHeaderBytes in 1..(16 * 1024 * 1024)) { "Edited header byte limit is invalid." }
        require(maxTrackedProtocolExchanges in 1..100_000) {
            "Tracked protocol exchange limit is invalid."
        }
        require(decisionTimeoutMillis in 100L..3_600_000L) { "Breakpoint timeout is invalid." }
    }
}

/** Engine-facing application contract containing no Netty, persistence, or UI types. */
public interface BreakpointGate {
    /** Current immutable aggregation/body requirements. */
    public val requirements: StateFlow<BreakpointRequirements>

    /** Returns whether any enabled rule can match this request at [phase] before body inspection. */
    public fun mayIntercept(request: HttpRequestSnapshot, phase: BreakpointPhase): Boolean

    /** Matches, admits, publishes, awaits, and terminally removes one candidate. */
    public suspend fun intercept(candidate: BreakpointCandidate): BreakpointDecision

    /** Cancels pending decisions and releases protocol observations for a disconnected exchange. */
    public fun cancelExchange(exchangeId: ExchangeId)

    /** Releases protocol observations after an exchange completes without a full response candidate. */
    public fun releaseExchange(exchangeId: ExchangeId)
}

/** Presentation-facing contract for rules and pending decisions. */
public interface BreakpointControl {
    public val pendingBreakpoints: StateFlow<List<PendingBreakpoint>>
    public val isEnabled: StateFlow<Boolean>

    public fun replaceRules(rules: List<BreakpointRule>)
    public suspend fun setEnabled(enabled: Boolean)
    public fun setDecisionTimeoutMillis(timeoutMillis: Long)
    public suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean
    public suspend fun dropMatching(url: String, method: String): Int
    public suspend fun clear(): Int
}

/** Runtime-facing contract that prevents breakpoint suspension while capture is detached. */
public interface BreakpointCaptureAvailability {
    /**
     * Updates whether captured exchanges may enter breakpoint suspension.
     *
     * Disabling availability continues every pending decision unchanged. It deliberately does not
     * alter [BreakpointGate.requirements], so transport pipelines and client connections stay stable.
     */
    public suspend fun setCaptureAvailable(available: Boolean)
}
