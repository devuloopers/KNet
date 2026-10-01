package com.devuloopers.knet.storage.protectedtraffic.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedServiceGroupEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficRuleEntity
import com.devuloopers.knet.storage.protectedtraffic.entity.ProtectedTrafficSettingsEntity
import kotlinx.coroutines.flow.Flow

/** Atomic Room access for protected-traffic settings, user rules, and built-in group toggles. */
@Dao
interface ProtectedTrafficDao {
    /** Observes the singleton fallback settings row. */
    @Query("SELECT * FROM protected_traffic_settings WHERE singletonId = 1")
    fun observeSettings(): Flow<ProtectedTrafficSettingsEntity?>

    /** Reads the singleton fallback settings row once. */
    @Query("SELECT * FROM protected_traffic_settings WHERE singletonId = 1")
    suspend fun getSettings(): ProtectedTrafficSettingsEntity?

    /** Observes all user rules in stable identity order. */
    @Query("SELECT * FROM protected_traffic_rules ORDER BY id")
    fun observeRules(): Flow<List<ProtectedTrafficRuleEntity>>

    /** Reads all user rules in stable identity order. */
    @Query("SELECT * FROM protected_traffic_rules ORDER BY id")
    suspend fun getRules(): List<ProtectedTrafficRuleEntity>

    /** Observes every persisted built-in group toggle. */
    @Query("SELECT * FROM protected_service_groups ORDER BY groupId")
    fun observeGroups(): Flow<List<ProtectedServiceGroupEntity>>

    /** Writes the singleton fallback settings row. */
    @Upsert
    suspend fun upsertSettings(value: ProtectedTrafficSettingsEntity)

    /** Creates or replaces one user-authored rule. */
    @Upsert
    suspend fun upsertRule(value: ProtectedTrafficRuleEntity)

    /** Deletes a user-authored rule by stable [id]. */
    @Query("DELETE FROM protected_traffic_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    /** Creates or replaces one built-in compatibility-group toggle. */
    @Upsert
    suspend fun upsertGroup(value: ProtectedServiceGroupEntity)
}
