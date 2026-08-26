package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Stable identifier for one editable field contributed by a protocol extension. */
@JvmInline
public value class ProtocolCriteriaFieldId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Protocol criteria field ID must not be blank." }
    }
}

/** One selectable value displayed by a protocol criteria choice field. */
public data class ProtocolCriteriaOption(
    /** Stable extension-owned value persisted into the editor draft. */
    public val value: String,
    /** User-facing label for [value]. */
    public val label: String,
) {
    init {
        require(value.isNotBlank()) { "Protocol criteria option value must not be blank." }
        require(label.isNotBlank()) { "Protocol criteria option label must not be blank." }
    }
}

/**
 * UI-neutral field schema supplied by a breakpoint protocol extension.
 *
 * Desktop presentation renders these standard field kinds without importing engine types.
 */
public sealed interface ProtocolCriteriaFieldDefinition {
    /** Stable field identity used when submitting editor values. */
    public val id: ProtocolCriteriaFieldId

    /** User-facing field label. */
    public val label: String

    /** Optional user-facing explanation of the matching behavior. */
    public val description: String?

    /** Single-line textual matching value. */
    public data class Text(
        override val id: ProtocolCriteriaFieldId,
        override val label: String,
        override val description: String? = null,
        /** Hint displayed when the field is empty. */
        public val placeholder: String = "",
        /** Whether an empty value is valid and has extension-defined meaning. */
        public val optional: Boolean = true,
    ) : ProtocolCriteriaFieldDefinition

    /** Closed choice whose values and labels are supplied by the extension. */
    public data class Choice(
        override val id: ProtocolCriteriaFieldId,
        override val label: String,
        override val description: String? = null,
        /** Values available to the user. */
        public val options: List<ProtocolCriteriaOption>,
        /** Value used for a newly created rule. */
        public val defaultValue: String,
    ) : ProtocolCriteriaFieldDefinition {
        init {
            require(options.isNotEmpty()) { "A protocol criteria choice requires at least one option." }
            require(options.any { it.value == defaultValue }) {
                "The protocol criteria default must be one of its declared options."
            }
        }
    }
}

/** Immutable protocol option and criteria schema exposed to breakpoint rule editors. */
public data class BreakpointProtocolDefinition(
    /** Stable protocol identity used by persisted rules and runtime lookup. */
    public val protocolId: BreakpointProtocolId,
    /** User-facing protocol name. */
    public val displayName: String,
    /** Version of the extension-owned encoded criteria contract. */
    public val criteriaVersion: Int,
    /** Interception unit owned by this protocol. HTTP bodies and framed messages use different gates. */
    public val interceptionUnit: BreakpointInterceptionUnit = BreakpointInterceptionUnit.HTTP_EXCHANGE,
    /** Standard editor fields supported by this protocol. */
    public val fields: List<ProtocolCriteriaFieldDefinition>,
) {
    init {
        require(displayName.isNotBlank()) { "Breakpoint protocol display name must not be blank." }
        require(criteriaVersion > 0) { "Breakpoint protocol criteria version must be positive." }
        require(fields.distinctBy { it.id }.size == fields.size) {
            "Breakpoint protocol criteria field IDs must be unique."
        }
    }
}

/** Stable interception granularity declared by a protocol extension. */
public enum class BreakpointInterceptionUnit {
    HTTP_EXCHANGE,
    PROTOCOL_MESSAGE,
}

/** One UI-entered value identified without engine-specific presentation types. */
public data class ProtocolCriteriaValue(
    /** Field receiving the value. */
    public val fieldId: ProtocolCriteriaFieldId,
    /** User-entered or selected value. */
    public val value: String,
)

/**
 * Bounded canonical request offered to protocol extensions when creating a rule from captured traffic.
 *
 * The application layer owns body loading and supplies at most its configured preview limit. Extensions
 * must treat an incomplete body as a detection hint only and must not retain the request or body after
 * returning from the suggestion call.
 *
 * @property request Canonical captured request metadata.
 * @property requestBody Defensive-copy body preview, or null when the request has no readable body.
 * @property requestBodyComplete Whether [requestBody] contains the complete captured request body.
 * @property response Canonical response metadata when the source exchange has received a response.
 */
public data class BreakpointRuleSuggestionInput(
    public val request: HttpRequestSnapshot,
    public val requestBody: BreakpointBody?,
    public val requestBodyComplete: Boolean,
    public val response: HttpResponseSnapshot? = null,
)

/**
 * Compact extension-owned semantic result for one exchange.
 *
 * Observations are ephemeral and must not retain request or response bodies. They can be cached by
 * exchange identity so response rules can evaluate the request that produced the response.
 */
public interface ProtocolObservation {
    /** Protocol extension that owns and understands this observation. */
    public val protocolId: BreakpointProtocolId
}

/** Bounded canonical input offered to a protocol extension during breakpoint evaluation. */
public data class ProtocolInspectionInput(
    /** Current request or response interception candidate. */
    public val candidate: BreakpointCandidate,
    /** Compact request observation retained for the same exchange, when available. */
    public val requestObservation: ProtocolObservation? = null,
)

/** Bounded input offered to an extension for one fully framed live protocol message. */
public data class ProtocolMessageInspectionInput(
    /** Canonical parent exchange used for bounded connection-scoped semantic correlation. */
    public val exchangeId: ExchangeId,
    public val request: HttpRequestSnapshot,
    public val messageId: ProtocolMessageId,
    public val kind: ProtocolMessageKind,
    /** Server-selected application subprotocol, when available for this framed transport. */
    public val negotiatedSubprotocol: String? = null,
    public val direction: TrafficDirection,
    public val sequence: Long,
    public val declaredBytes: Long,
    public val compressed: Boolean,
    public val compressionEncoding: String?,
    public val body: BreakpointBody,
) {
    init {
        require(sequence > 0L) { "Protocol message sequence must be positive." }
        require(declaredBytes >= 0L) { "Declared protocol message bytes must not be negative." }
    }
}

/** Strongly compiled protocol predicate produced from persisted extension criteria. */
public interface CompiledProtocolCriteria {
    /** Protocol extension that owns this predicate. */
    public val protocolId: BreakpointProtocolId

    /** Returns whether the extension-owned [observation] satisfies the compiled predicate. */
    public fun matches(observation: ProtocolObservation?): Boolean
}

/**
 * Additive breakpoint extension implemented by protocol modules.
 *
 * Implementations own criteria encoding, validation, bounded inspection, and semantic matching.
 * The proxy engine and application coordinator remain unaware of concrete protocol types.
 */
public interface BreakpointProtocolExtension {
    /** Protocol identity, display name, version, and standard editor fields. */
    public val definition: BreakpointProtocolDefinition

    /**
     * Relative ordering used only when several extensions can suggest a rule for the same request.
     *
     * Higher values are evaluated first. Runtime breakpoint matching does not use this priority.
     */
    public val suggestionPriority: Int
        get() = 0

    /**
     * Validates and compiles persisted [criteria].
     *
     * @return a matcher when the payload is valid for this extension, or null to fail closed.
     */
    public fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria?

    /**
     * Inspects one bounded canonical candidate.
     *
     * @return compact typed facts when the candidate belongs to this protocol, or null otherwise.
     */
    public fun inspect(input: ProtocolInspectionInput): ProtocolObservation?

    /**
     * Inspects one framed message. HTTP/GraphQL extensions keep the safe default; framed-protocol
     * extensions opt in without adding protocol branches to the application coordinator.
     */
    public fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation? = null

    /** Releases any compact framed-message correlation state retained for [exchangeId]. */
    public fun releaseMessages(exchangeId: ExchangeId): Unit = Unit

    /**
     * Validates a complete replacement before transport reconstruction.
     *
     * The safe default preserves existing framed protocols; semantic extensions override this when
     * changing identity or lifecycle fields would corrupt connection state.
     */
    public fun validateMessageReplacement(
        input: ProtocolMessageInspectionInput,
        replacement: BreakpointBody,
    ): Boolean = true

    /** Decodes a valid persisted criterion into UI-neutral editor values. */
    public fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue>

    /**
     * Validates editor [values] and encodes one persistable criterion.
     *
     * @return encoded criteria, or null when required or selected values are invalid.
     */
    public fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria?

    /**
     * Suggests extension-owned criteria for a rule created from captured traffic.
     *
     * Returning null means this extension did not confidently recognize the request. The application
     * registry validates the returned criteria through [compile] before exposing it to presentation.
     *
     * @param input Bounded canonical request and optional body preview.
     * @return Persistable protocol criteria, or null when the request is not recognized.
     */
    public fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? = null
}
