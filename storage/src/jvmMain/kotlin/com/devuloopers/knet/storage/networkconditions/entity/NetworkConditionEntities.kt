package com.devuloopers.knet.storage.networkconditions.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "network_condition_settings")
data class NetworkConditionSettingsEntity(
    @PrimaryKey val singletonId: Int = 1,
    val enabled: Boolean,
    val globalProfileId: String?,
    val lastQuickAddProfileId: String?,
)

@Entity(tableName = "network_condition_profiles")
data class NetworkConditionProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val downloadBitsPerSecond: Long?,
    val downloadUtilizationPercent: Int,
    val uploadBitsPerSecond: Long?,
    val uploadUtilizationPercent: Int,
    val latencyMillis: Long,
    val jitterMillis: Long,
    val virtualMtuBytes: Int,
    val failureKind: String,
    val failureProbabilityPercent: Int?,
    val failureSeed: Long?,
    val packetLossPercent: Int,
    val packetDuplicationPercent: Int,
    val packetReorderingPercent: Int,
)

@Entity(
    tableName = "network_condition_rules",
    indices = [Index(value = ["normalizedHost", "wildcard", "port", "protocolId"])],
)
data class NetworkConditionRuleEntity(
    @PrimaryKey val id: String,
    val normalizedHost: String,
    val wildcard: Boolean,
    val port: Int?,
    val profileId: String,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    @ColumnInfo(defaultValue = "'transport'") val protocolId: String = "transport",
    @ColumnInfo(defaultValue = "''") val protocolCriteriaPayload: String = "",
)
