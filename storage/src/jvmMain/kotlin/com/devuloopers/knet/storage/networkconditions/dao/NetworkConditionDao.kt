package com.devuloopers.knet.storage.networkconditions.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionProfileEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity
import com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkConditionDao {
    @Query("SELECT * FROM network_condition_settings WHERE singletonId = 1")
    fun observeSettings(): Flow<NetworkConditionSettingsEntity?>

    @Query("SELECT * FROM network_condition_settings WHERE singletonId = 1")
    suspend fun getSettings(): NetworkConditionSettingsEntity?

    @Query("SELECT * FROM network_condition_profiles ORDER BY name COLLATE NOCASE, id")
    fun observeProfiles(): Flow<List<NetworkConditionProfileEntity>>

    @Query("SELECT * FROM network_condition_profiles ORDER BY name COLLATE NOCASE, id")
    suspend fun getProfiles(): List<NetworkConditionProfileEntity>

    @Query("SELECT * FROM network_condition_rules ORDER BY priority DESC, normalizedHost, port, protocolId, id")
    fun observeRules(): Flow<List<NetworkConditionRuleEntity>>

    @Query("SELECT * FROM network_condition_rules ORDER BY priority DESC, normalizedHost, port, protocolId, id")
    suspend fun getRules(): List<NetworkConditionRuleEntity>

    @Upsert
    suspend fun upsertSettings(value: NetworkConditionSettingsEntity)

    @Upsert
    suspend fun upsertProfile(value: NetworkConditionProfileEntity)

    @Query("DELETE FROM network_condition_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Upsert
    suspend fun upsertRule(value: NetworkConditionRuleEntity)

    @Query("DELETE FROM network_condition_rules WHERE id = :id")
    suspend fun deleteRule(id: String)
}
