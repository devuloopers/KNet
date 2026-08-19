package com.devuloopers.knet.application.usecase.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria

/**
 * UI-neutral workflow for listing protocol rule schemas and translating their editor values.
 *
 * Presentation depends on this use case rather than concrete protocol engines or persistence
 * codecs. Runtime matching and editor encoding therefore use the same registered extensions.
 */
public class BreakpointProtocolRuleUseCase(
    private val registry: BreakpointProtocolRegistry,
) {
    /** Returns all currently installed breakpoint protocol definitions. */
    public fun definitions(): List<BreakpointProtocolDefinition> = registry.definitions

    /** Decodes one rule's criteria into values for its registered editor schema. */
    public fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> =
        registry.editorValues(criteria)

    /** Validates editor values and creates one persistable extension-owned criterion. */
    public fun createCriteria(
        protocolId: BreakpointProtocolId,
        values: List<ProtocolCriteriaValue>,
    ): ProtocolMatchCriteria? = registry.createCriteria(protocolId, values)
}
