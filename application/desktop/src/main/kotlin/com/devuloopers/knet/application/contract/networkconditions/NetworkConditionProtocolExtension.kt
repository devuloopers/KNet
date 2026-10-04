package com.devuloopers.knet.application.contract.networkconditions

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Stable shaping boundary declared by a protocol-aware Network Conditions extension. */
public enum class NetworkConditionInterceptionUnit {
    /** One decoded HTTP request and the response correlated with it. */
    HTTP_EXCHANGE,

    /** One complete framed message on an upgraded duplex connection. */
    PROTOCOL_MESSAGE,
}

/**
 * UI-neutral schema and runtime ownership declaration for one semantic condition protocol.
 *
 * @property protocolId Stable persisted identity owned by the extension.
 * @property displayName User-facing protocol label.
 * @property criteriaVersion Positive version of the extension-owned payload contract.
 * @property interceptionUnit Transport boundary at which matching is safe.
 * @property requiresRequestBody Whether live HTTP matching requires bounded complete-body aggregation.
 * @property fields Generic editor fields rendered by the Network Conditions presentation.
 */
public data class NetworkConditionProtocolDefinition(
    public val protocolId: NetworkConditionProtocolId,
    public val displayName: String,
    public val criteriaVersion: Int,
    public val interceptionUnit: NetworkConditionInterceptionUnit,
    public val requiresRequestBody: Boolean = false,
    public val fields: List<ProtocolCriteriaFieldDefinition>,
) {
    init {
        require(displayName.isNotBlank()) { "Network condition protocol display name must not be blank." }
        require(criteriaVersion > 0) { "Network condition protocol criteria version must be positive." }
        require(fields.distinctBy { it.id }.size == fields.size) {
            "Network condition protocol criteria field IDs must be unique."
        }
    }
}

/** Defensive bounded body value passed to semantic condition extensions. */
public class NetworkConditionBody(bytes: ByteArray) {
    private val value: ByteArray = bytes.copyOf()

    /** Number of retained payload bytes. */
    public val size: Int
        get() = value.size

    /** Returns an independently owned body copy. */
    public fun copyBytes(maximumBytes: Int = value.size): ByteArray {
        require(maximumBytes >= 0) { "Maximum copied network condition body bytes must not be negative." }
        return value.copyOf(minOf(value.size, maximumBytes))
    }
}

/**
 * Bounded canonical HTTP request offered to semantic condition extensions.
 *
 * [trafficAnnotations] is populated only for Traffic quick-add. Live matching leaves it empty and derives compact
 * observations directly from the bounded request.
 */
public data class NetworkConditionHttpInspectionInput(
    public val request: HttpRequestSnapshot,
    public val requestBody: NetworkConditionBody?,
    public val requestBodyComplete: Boolean,
    public val trafficAnnotations: List<InspectionAnnotation> = emptyList(),
)

/** Complete bounded framed message offered to a protocol-aware condition extension. */
public data class NetworkConditionMessageInspectionInput(
    public val exchangeId: ExchangeId,
    public val request: HttpRequestSnapshot,
    public val messageId: ProtocolMessageId,
    public val kind: ProtocolMessageKind,
    public val negotiatedSubprotocol: String?,
    public val direction: TrafficDirection,
    public val sequence: Long,
    public val declaredBytes: Long,
    public val compressed: Boolean,
    public val compressionEncoding: String?,
    public val body: NetworkConditionBody,
) {
    init {
        require(sequence > 0L) { "Network condition message sequence must be positive." }
        require(declaredBytes >= 0L) { "Network condition declared message bytes must not be negative." }
    }
}

/** Compact extension-owned facts produced by bounded semantic inspection. */
public interface NetworkConditionProtocolObservation {
    /** Extension identity that owns this observation. */
    public val protocolId: NetworkConditionProtocolId
}

/** Strongly validated predicate compiled from one persisted semantic criterion. */
public interface CompiledNetworkConditionProtocolCriteria {
    /** Extension identity that owns this predicate. */
    public val protocolId: NetworkConditionProtocolId

    /** Returns whether [observation] satisfies the compiled selector. */
    public fun matches(observation: NetworkConditionProtocolObservation?): Boolean
}

/**
 * Additive semantic extension for protocol-aware Network Conditions.
 *
 * Implementations own criteria encoding, validation, bounded parsing, and compact correlation state. They never
 * schedule bytes, mutate transport state, or depend on presentation classes.
 */
public interface NetworkConditionProtocolExtension {
    /** Protocol definition exposed to runtime and editor workflows. */
    public val definition: NetworkConditionProtocolDefinition

    /** Higher values are considered first when Traffic quick-add asks for a semantic suggestion. */
    public val suggestionPriority: Int
        get() = 0

    /** Lower values place the protocol earlier in the Network Conditions rule editor. */
    public val editorOrder: Int
        get() = 0

    /** Validates and compiles [criteria], returning null to fail closed. */
    public fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria?

    /** Inspects one complete bounded HTTP request, or returns null when the protocol is not recognized. */
    public fun inspectHttp(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolObservation? = null

    /**
     * Returns whether a header-only [input] could require complete-body inspection for this protocol.
     *
     * Body-dependent extensions may narrow the default using safe header and request-target evidence so unrelated
     * streaming protocols on the same destination are never buffered merely because a rule exists.
     */
    public fun shouldInspectHttpBody(input: NetworkConditionHttpInspectionInput): Boolean =
        definition.requiresRequestBody

    /** Inspects one complete framed message, or returns null when the protocol is not recognized. */
    public fun inspectMessage(
        input: NetworkConditionMessageInspectionInput,
    ): NetworkConditionProtocolObservation? = null

    /** Releases bounded message-correlation state after an upgraded connection terminates. */
    public fun releaseMessages(exchangeId: ExchangeId): Unit = Unit

    /** Decodes [criteria] into editor values owned by [definition]. */
    public fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue>

    /** Validates editor [values] and returns a persistable criterion. */
    public fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria?

    /** Suggests semantic criteria for Traffic quick-add, or null when [input] is not recognized. */
    public fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? = null
}

/**
 * Validated registry of protocol-aware condition extensions.
 *
 * Invalid payloads, unknown identities, mismatched observations, and extension failures all fail closed. The
 * built-in transport definition remains payload blind and is handled by the domain matcher.
 */
public class NetworkConditionProtocolRegistry(
    extensions: List<NetworkConditionProtocolExtension> = emptyList(),
) {
    private val extensionsById: Map<NetworkConditionProtocolId, NetworkConditionProtocolExtension>
    private val suggestionExtensions: List<NetworkConditionProtocolExtension>

    /** Editor definitions with the built-in destination rule first. */
    public val definitions: List<NetworkConditionProtocolDefinition>

    init {
        val duplicateIds = extensions.groupBy { it.definition.protocolId }
            .filterValues { values -> values.size > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate network condition protocol extensions: ${duplicateIds.joinToString { it.value }}"
        }
        require(extensions.none { it.definition.protocolId == NetworkConditionProtocolId.TRANSPORT }) {
            "The transport network condition definition is built in."
        }
        extensionsById = extensions.associateBy { it.definition.protocolId }
        suggestionExtensions = extensions.sortedWith(
            compareByDescending<NetworkConditionProtocolExtension> { it.suggestionPriority }
                .thenBy { extension -> extension.definition.protocolId.value },
        )
        definitions = listOf(TRANSPORT_DEFINITION) + extensions
            .sortedWith(
                compareBy<NetworkConditionProtocolExtension> { extension -> extension.editorOrder }
                    .thenBy { extension -> extension.definition.displayName },
            )
            .map(NetworkConditionProtocolExtension::definition)
    }

    /** Returns the transport boundary declared by [protocolId], or null when unavailable. */
    public fun interceptionUnit(protocolId: NetworkConditionProtocolId): NetworkConditionInterceptionUnit? =
        if (protocolId == NetworkConditionProtocolId.TRANSPORT) {
            NetworkConditionInterceptionUnit.HTTP_EXCHANGE
        } else {
            extensionsById[protocolId]?.definition?.interceptionUnit
        }

    /** Returns whether [protocolId] requires complete bounded HTTP request-body aggregation. */
    public fun requiresRequestBody(protocolId: NetworkConditionProtocolId): Boolean =
        extensionsById[protocolId]?.definition?.requiresRequestBody == true

    /** Safely asks [protocolId] whether the header-only [input] may require complete-body inspection. */
    public fun shouldInspectHttpBody(
        protocolId: NetworkConditionProtocolId,
        input: NetworkConditionHttpInspectionInput,
    ): Boolean = extensionsById[protocolId]?.let { extension ->
        extension.definition.requiresRequestBody &&
            runCatching { extension.shouldInspectHttpBody(input) }.getOrDefault(false)
    } == true

    /** Compiles one persisted semantic criterion, returning null for transport-only or invalid input. */
    public fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria? {
        if (criteria.isTransportOnly) return null
        val extension = extensionsById[criteria.protocolId] ?: return null
        return runCatching { extension.compile(criteria) }.getOrNull()
            ?.takeIf { compiled -> compiled.protocolId == criteria.protocolId }
    }

    /** Safely evaluates [criteria] against an extension-owned [observation]. */
    public fun matches(
        criteria: CompiledNetworkConditionProtocolCriteria,
        observation: NetworkConditionProtocolObservation?,
    ): Boolean = runCatching { criteria.matches(observation) }.getOrDefault(false)

    /** Inspects one HTTP request through the extension identified by [protocolId]. */
    public fun inspectHttp(
        protocolId: NetworkConditionProtocolId,
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolObservation? = extensionsById[protocolId]?.let { extension ->
        runCatching { extension.inspectHttp(input) }.getOrNull()
            ?.takeIf { observation -> observation.protocolId == protocolId }
    }

    /** Inspects one framed message through the extension identified by [protocolId]. */
    public fun inspectMessage(
        protocolId: NetworkConditionProtocolId,
        input: NetworkConditionMessageInspectionInput,
    ): NetworkConditionProtocolObservation? = extensionsById[protocolId]?.let { extension ->
        runCatching { extension.inspectMessage(input) }.getOrNull()
            ?.takeIf { observation -> observation.protocolId == protocolId }
    }

    /** Releases message correlation from every installed extension. */
    public fun releaseMessages(exchangeId: ExchangeId) {
        extensionsById.values.forEach { extension -> runCatching { extension.releaseMessages(exchangeId) } }
    }

    /** Returns editor values decoded by the owning extension. */
    public fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> = when {
        criteria.isTransportOnly -> emptyList()
        else -> extensionsById[criteria.protocolId]?.let { extension ->
            runCatching { extension.editorValues(criteria) }.getOrNull()
        }.orEmpty()
    }

    /** Creates validated criteria through the selected extension. */
    public fun createCriteria(
        protocolId: NetworkConditionProtocolId,
        values: List<ProtocolCriteriaValue>,
    ): NetworkConditionProtocolCriteria? {
        if (protocolId == NetworkConditionProtocolId.TRANSPORT) {
            return NetworkConditionProtocolCriteria.TransportDefault.takeIf { values.isEmpty() }
        }
        return extensionsById[protocolId]?.let { extension ->
            runCatching { extension.createCriteria(values) }.getOrNull()
                ?.takeIf { criteria -> criteria.protocolId == protocolId && extension.compile(criteria) != null }
        }
    }

    /** Returns the highest-priority valid semantic Traffic quick-add suggestion. */
    public fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        suggestionExtensions.forEach { extension ->
            val criteria = runCatching { extension.suggestCriteria(input) }.getOrNull()
                ?.takeIf { value -> value.protocolId == extension.definition.protocolId }
                ?: return@forEach
            if (runCatching { extension.compile(criteria) }.getOrNull() != null) return criteria
        }
        return null
    }

    private companion object {
        val TRANSPORT_DEFINITION: NetworkConditionProtocolDefinition = NetworkConditionProtocolDefinition(
            protocolId = NetworkConditionProtocolId.TRANSPORT,
            displayName = "All traffic",
            criteriaVersion = 1,
            interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
            fields = emptyList(),
        )
    }
}
