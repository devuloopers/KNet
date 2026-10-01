package com.devuloopers.knet.storage.protectedtraffic.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Singleton persisted protected-traffic fallback configuration.
 *
 * @property singletonId Fixed row identity.
 * @property defaultAction Strongly validated action name decoded by the data layer.
 */
@Entity(tableName = "protected_traffic_settings")
data class ProtectedTrafficSettingsEntity(
    @PrimaryKey val singletonId: Int = 1,
    val defaultAction: String,
)

/**
 * Persisted user-authored protected-traffic rule.
 *
 * @property id Stable rule identity.
 * @property enabled Whether the rule participates in evaluation.
 * @property action Selected action name.
 * @property sourceApplication Optional verified application selector.
 * @property destinationKind Optional exact, wildcard, or CIDR discriminator.
 * @property destinationValue Canonical destination value without kind adornment.
 * @property destinationPrefixLength CIDR prefix length when applicable.
 * @property destinationIpFamily CIDR address family when applicable.
 * @property port Optional destination port.
 * @property transport Optional transport name.
 * @property ipFamily Optional flow address-family selector.
 */
@Entity(
    tableName = "protected_traffic_rules",
    indices = [
        Index(
            value = [
                "sourceApplication",
                "destinationKind",
                "destinationValue",
                "destinationPrefixLength",
                "port",
                "transport",
                "ipFamily",
            ],
            unique = true,
        ),
    ],
)
data class ProtectedTrafficRuleEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean,
    val action: String,
    val sourceApplication: String?,
    val destinationKind: String?,
    val destinationValue: String?,
    val destinationPrefixLength: Int?,
    val destinationIpFamily: String?,
    val port: Int?,
    val transport: String?,
    val ipFamily: String?,
)

/**
 * Persisted enablement state for one versioned built-in compatibility group.
 *
 * @property groupId Stable built-in group identity.
 * @property enabled Whether the group participates in policy evaluation.
 */
@Entity(tableName = "protected_service_groups")
data class ProtectedServiceGroupEntity(
    @PrimaryKey val groupId: String,
    val enabled: Boolean,
)
