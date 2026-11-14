package com.devuloopers.knet.storage.policy.entity

import androidx.room.*
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficRuleEntity

/** Saved device execution settings. Removing a registration cascades only its own workspace. */
@Entity(tableName = "policy_workspaces", foreignKeys = [ForeignKey(entity = RegisteredDeviceEntity::class,
    parentColumns = ["id"], childColumns = ["deviceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["deviceId"], unique = true)])
data class PolicyWorkspaceEntity(
    @PrimaryKey val key: String,
    val deviceId: String?,
    @ColumnInfo(defaultValue = "0") val networkEnabled: Boolean = false,
    val profileId: String? = null,
    val quickAddProfileId: String? = null,
    @ColumnInfo(defaultValue = "1") val interceptionEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "'INSPECT'") val protectedAction: String = "INSPECT",
    @ColumnInfo(defaultValue = "1") val automaticTlsFallback: Boolean = true,
)

/** Independent activation with real foreign keys to both the definition and its execution workspace. */
@Entity(tableName = "policy_breakpoint_activations", primaryKeys = ["ruleId", "workspaceKey"],
    foreignKeys = [ForeignKey(entity = BreakpointRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PolicyWorkspaceEntity::class, parentColumns = ["key"], childColumns = ["workspaceKey"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workspaceKey")])
data class BreakpointActivationEntity(val ruleId: String, val workspaceKey: String, val enabled: Boolean)

/** Independent activation with real foreign keys to both the definition and its execution workspace. */
@Entity(tableName = "policy_condition_activations", primaryKeys = ["ruleId", "workspaceKey"],
    foreignKeys = [ForeignKey(entity = NetworkConditionRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PolicyWorkspaceEntity::class, parentColumns = ["key"], childColumns = ["workspaceKey"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workspaceKey")])
data class ConditionActivationEntity(val ruleId: String, val workspaceKey: String, val enabled: Boolean)

/** Independent activation with real foreign keys to both the definition and its execution workspace. */
@Entity(tableName = "policy_protection_activations", primaryKeys = ["ruleId", "workspaceKey"],
    foreignKeys = [ForeignKey(entity = ProtectedTrafficRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PolicyWorkspaceEntity::class, parentColumns = ["key"], childColumns = ["workspaceKey"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workspaceKey")])
data class ProtectionActivationEntity(val ruleId: String, val workspaceKey: String, val enabled: Boolean)

/** Committed publication order across repository instances. */
@Entity(tableName = "policy_library_revision")
data class PolicyLibraryRevisionEntity(@PrimaryKey val id: Int = 1, val revision: Long = 0)
