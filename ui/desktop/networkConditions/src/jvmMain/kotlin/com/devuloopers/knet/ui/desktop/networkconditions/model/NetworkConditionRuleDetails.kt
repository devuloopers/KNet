package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition

/** Readable rule evidence decoded by the application boundary, never from an opaque payload in a composable. */
data class NetworkConditionRuleDetails(val protocol: String, val criteria: String)

/** Preserves selector labels, including exact/prefix and message-direction distinctions, for every extension. */
internal fun networkConditionRuleDetails(
    definition: NetworkConditionProtocolDefinition?,
    values: List<ProtocolCriteriaValue>,
    transportOnly: Boolean,
): NetworkConditionRuleDetails {
    if (transportOnly) return NetworkConditionRuleDetails("All traffic", "Any request or connection")
    if (definition == null) return NetworkConditionRuleDetails("Unavailable protocol", "Open the rule to review its criteria")
    val byId = values.associate { it.fieldId to it.value }
    val details = definition.fields.mapNotNull { field ->
        val value = byId[field.id]?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val label = when (field) {
            is ProtocolCriteriaFieldDefinition.Text -> value
            is ProtocolCriteriaFieldDefinition.Choice -> field.options.firstOrNull { it.value == value }?.label
                ?: return@mapNotNull null
        }
        "${field.label}: $label"
    }
    return NetworkConditionRuleDetails(
        protocol = definition.displayName,
        criteria = details.joinToString(" · ").ifBlank { "No criteria to display" },
    )
}
