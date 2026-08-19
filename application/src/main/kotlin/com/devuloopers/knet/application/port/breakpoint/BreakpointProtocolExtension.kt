package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria

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

/** One UI-entered value identified without engine-specific presentation types. */
public data class ProtocolCriteriaValue(
    /** Field receiving the value. */
    public val fieldId: ProtocolCriteriaFieldId,
    /** User-entered or selected value. */
    public val value: String,
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

    /** Decodes a valid persisted criterion into UI-neutral editor values. */
    public fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue>

    /**
     * Validates editor [values] and encodes one persistable criterion.
     *
     * @return encoded criteria, or null when required or selected values are invalid.
     */
    public fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria?
}

/**
 * Application-owned registry for built-in HTTP matching and additive protocol extensions.
 *
 * Duplicate identities are rejected at composition time. Missing extensions, invalid payloads,
 * and mismatched observations all fail closed.
 */
public class BreakpointProtocolRegistry(
    extensions: List<BreakpointProtocolExtension> = emptyList(),
) {
    private val extensionsById: Map<BreakpointProtocolId, BreakpointProtocolExtension>

    /** Protocol definitions presented to rule-management workflows, with HTTP first. */
    public val definitions: List<BreakpointProtocolDefinition>

    init {
        val allExtensions = listOf(HttpBreakpointProtocolExtension) + extensions
        val duplicateIds = allExtensions.groupBy { it.definition.protocolId }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate breakpoint protocol extensions: ${duplicateIds.joinToString { it.value }}"
        }
        extensionsById = allExtensions.associateBy { it.definition.protocolId }
        definitions = allExtensions.map(BreakpointProtocolExtension::definition)
    }

    /** Compiles one persisted criterion, returning null when its extension or payload is invalid. */
    public fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        val extension = extensionsById[criteria.protocolId] ?: return null
        return runCatching { extension.compile(criteria) }
            .getOrNull()
            ?.takeIf { it.protocolId == criteria.protocolId }
    }

    /** Evaluates a compiled extension predicate and converts extension failure into no match. */
    public fun matches(
        criteria: CompiledProtocolCriteria,
        observation: ProtocolObservation?,
    ): Boolean = runCatching { criteria.matches(observation) }.getOrDefault(false)

    /** Produces a compact observation using only the extension identified by [protocolId]. */
    public fun inspect(
        protocolId: BreakpointProtocolId,
        input: ProtocolInspectionInput,
    ): ProtocolObservation? {
        val extension = extensionsById[protocolId] ?: return null
        return runCatching { extension.inspect(input) }
            .getOrNull()
            ?.takeIf { it.protocolId == protocolId }
    }

    /** Returns editor values decoded by the owning extension, or an empty list when unavailable. */
    public fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> =
        extensionsById[criteria.protocolId]?.let { extension ->
            runCatching { extension.editorValues(criteria) }.getOrNull()
        }.orEmpty()

    /** Builds persistable criteria through the owning extension, failing closed when unavailable. */
    public fun createCriteria(
        protocolId: BreakpointProtocolId,
        values: List<ProtocolCriteriaValue>,
    ): ProtocolMatchCriteria? {
        val extension = extensionsById[protocolId] ?: return null
        return runCatching { extension.createCriteria(values) }
            .getOrNull()
            ?.takeIf { it.protocolId == protocolId }
    }
}

/** Built-in transport-only HTTP behavior registered without a protocol engine dependency. */
private object HttpBreakpointProtocolExtension : BreakpointProtocolExtension {
    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = BreakpointProtocolId.HTTP,
        displayName = "HTTP / REST",
        criteriaVersion = 1,
        fields = emptyList(),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? =
        HttpCompiledCriteria.takeIf {
            criteria.protocolId == BreakpointProtocolId.HTTP && criteria.encodedPayload.isBlank()
        }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> = emptyList()

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? =
        ProtocolMatchCriteria.HttpDefault.takeIf { values.isEmpty() }
}

/** Transport-only predicate that intentionally requires no semantic observation. */
private object HttpCompiledCriteria : CompiledProtocolCriteria {
    override val protocolId: BreakpointProtocolId = BreakpointProtocolId.HTTP

    override fun matches(observation: ProtocolObservation?): Boolean = observation == null
}
