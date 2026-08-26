package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.id.ExchangeId

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
    private val suggestionExtensions: List<BreakpointProtocolExtension>

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
        suggestionExtensions = extensions.sortedWith(
            compareByDescending<BreakpointProtocolExtension> { it.suggestionPriority }
                .thenBy { it.definition.protocolId.value },
        )
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

    /** Produces a compact observation for one framed message through its owning extension. */
    public fun inspectMessage(
        protocolId: BreakpointProtocolId,
        input: ProtocolMessageInspectionInput,
    ): ProtocolObservation? {
        val extension = extensionsById[protocolId] ?: return null
        return runCatching { extension.inspectMessage(input) }
            .getOrNull()
            ?.takeIf { it.protocolId == protocolId }
    }

    /** Releases framed-message state from every registered semantic extension. */
    public fun releaseMessages(exchangeId: ExchangeId) {
        extensionsById.values.forEach { extension ->
            runCatching { extension.releaseMessages(exchangeId) }
        }
    }

    /** Validates one replacement through the rule-winning protocol extension. */
    public fun validateMessageReplacement(
        protocolId: BreakpointProtocolId,
        input: ProtocolMessageInspectionInput,
        replacement: BreakpointBody,
    ): Boolean = extensionsById[protocolId]?.let { extension ->
        runCatching { extension.validateMessageReplacement(input, replacement) }.getOrDefault(false)
    } ?: false

    /** Returns the interception unit declared by a registered extension. */
    public fun interceptionUnit(protocolId: BreakpointProtocolId): BreakpointInterceptionUnit? =
        extensionsById[protocolId]?.definition?.interceptionUnit

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

    /**
     * Returns the highest-priority valid semantic criteria suggested for [input].
     *
     * HTTP is intentionally the caller-owned fallback and is not queried here. Invalid extension
     * output fails closed, allowing the next registered semantic extension to inspect the request.
     *
     * @param input Bounded canonical request being converted into a breakpoint rule draft.
     * @return Validated semantic criteria, or null when no extension recognizes the request.
     */
    public fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        suggestionExtensions.forEach { extension ->
            val criteria = runCatching { extension.suggestCriteria(input) }.getOrNull()
                ?.takeIf { it.protocolId == extension.definition.protocolId }
                ?: return@forEach
            val isValid = runCatching { extension.compile(criteria) }.getOrNull()
                ?.protocolId == criteria.protocolId
            if (isValid) return criteria
        }
        return null
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
