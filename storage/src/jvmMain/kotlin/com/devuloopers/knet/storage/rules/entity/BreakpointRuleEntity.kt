package com.devuloopers.knet.storage.rules.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database entity representing a persistent breakpoint rule.
 *
 * @property id Unique identifier of the rule.
 * @property urlPattern Regular expression pattern matching target request URLs.
 * @property method HTTP method filter (e.g. GET, POST, or null for ALL).
 * @property phase Interception phase filter (REQUEST, RESPONSE, BOTH).
 * @property enabled Whether this rule is active.
 * @property priority Rule evaluation priority order.
 */
@Entity(tableName = "breakpoint_rules")
data class BreakpointRuleEntity(
    @PrimaryKey val id: String,
    val urlPattern: String,
    val method: String?,
    val phase: String,
    val enabled: Boolean,
    val priority: Int = 0
)
