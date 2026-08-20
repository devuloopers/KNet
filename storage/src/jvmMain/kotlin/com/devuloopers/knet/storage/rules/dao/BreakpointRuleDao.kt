package com.devuloopers.knet.storage.rules.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for managing persistent breakpoint rules.
 */
@Dao
interface BreakpointRuleDao {

    /**
     * Cold stream emitting all configured breakpoint rules sorted by execution priority.
     */
    @Query("SELECT * FROM breakpoint_rules ORDER BY priority ASC, id ASC")
    fun observeAllRules(): Flow<List<BreakpointRuleEntity>>

    /**
     * Synchronous lookup returning snapshot list of all breakpoint rules.
     */
    @Query("SELECT * FROM breakpoint_rules ORDER BY priority ASC, id ASC")
    suspend fun getAllRules(): List<BreakpointRuleEntity>

    /**
     * Inserts or updates a breakpoint rule entity.
     */
    @Upsert
    suspend fun upsertRule(rule: BreakpointRuleEntity)

    /**
     * Deletes a target breakpoint rule by ID.
     */
    @Query("DELETE FROM breakpoint_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    /**
     * Updates enabled status of a target breakpoint rule by ID.
     */
    @Query("UPDATE breakpoint_rules SET enabled = :enabled WHERE id = :id")
    suspend fun toggleRule(id: String, enabled: Boolean)

    /**
     * Clears all persistent breakpoint rules.
     */
    @Query("DELETE FROM breakpoint_rules")
    suspend fun clearAll()
}
