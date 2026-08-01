package com.devuloopers.knet.domain.rules.model

/**
 * Represents an active interceptor or modifier rule.
 *
 * @property id Unique rule identifier.
 * @property name The display name of the rule.
 * @property type The target context type (Request/Response).
 * @property condition Description of the triggering matching criteria.
 * @property action The action execution type.
 * @property enabled Whether this rule is currently active.
 * @property hitCount How many times this rule has matched.
 * @property lastHit Timestamp of the most recent match.
 */
data class RuleModel(
    val id: String = "",
    val name: String,
    val type: String,
    val condition: String,
    val action: String,
    val enabled: Boolean = true,
    val hitCount: Int = 0,
    val lastHit: String = "-"
)
