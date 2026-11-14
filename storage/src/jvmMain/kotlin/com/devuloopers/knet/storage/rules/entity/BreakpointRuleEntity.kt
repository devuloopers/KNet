package com.devuloopers.knet.storage.rules.entity


import androidx.room.ForeignKey
import com.devuloopers.knet.storage.policy.entity.PolicyWorkspaceEntity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database entity representing a persistent breakpoint rule.
 *
 * @property id Unique identifier of the rule.
 * @property urlPattern Regular expression pattern matching target request URLs.
 * @property port Exact destination port, or null to match every port.
 * @property method HTTP method filter (e.g. GET, POST, or null for ALL).
 * @property phase Interception phase filter (REQUEST, RESPONSE, BOTH).
 * @property enabled Whether this rule is active.
 * @property priority Rule evaluation priority order.
 * @property protocolCriteriaType Open normalized identifier owned by a registered protocol extension.
 * @property protocolCriteriaData Opaque versioned payload owned by that protocol extension.
 */
@Entity(tableName = "breakpoint_rules", foreignKeys = [ForeignKey(entity = PolicyWorkspaceEntity::class, parentColumns = ["key"], childColumns = ["libraryOwnerKey"], onDelete = ForeignKey.CASCADE)], indices = [Index("libraryOwnerKey")])
data class BreakpointRuleEntity(
    @PrimaryKey val id: String,
    val urlPattern: String,
    val port: Int? = null,
    val method: String?,
    val phase: String,
    val enabled: Boolean,
    val priority: Int = 0,
    val protocolCriteriaType: String = "http",
    val protocolCriteriaData: String? = null,
    @ColumnInfo(defaultValue = "NULL") val libraryOwnerKey: String? = null,
    @ColumnInfo(defaultValue = "1") val libraryRevision: Long = 1L,
)
