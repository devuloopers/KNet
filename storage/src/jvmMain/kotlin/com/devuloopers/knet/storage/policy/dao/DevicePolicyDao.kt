package com.devuloopers.knet.storage.policy.dao

import androidx.room.*
import com.devuloopers.knet.storage.policy.entity.*
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity

/** Transaction-confined workspace and activation access; payload definitions stay in their typed tables. */
@Dao
interface DevicePolicyDao {
    /** Initializes publication order once. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedRevision(value: PolicyLibraryRevisionEntity)
    /** Advances order in the same transaction as the policy change. */
    @Query("UPDATE policy_library_revision SET revision = revision + 1 WHERE id = 1") suspend fun advanceRevision()
    /** Current committed sequence. */
    @Query("SELECT revision FROM policy_library_revision WHERE id = 1") suspend fun revision(): Long?

    /** Reads saved settings; missing newly paired workspaces use product defaults. */
    @Query("SELECT * FROM policy_workspaces") suspend fun workspaces(): List<PolicyWorkspaceEntity>
    /** Validates an immutable registered identity in the same transaction as a mutation. */
    @Query("SELECT * FROM registered_devices") suspend fun devices(): List<RegisteredDeviceEntity>
    /** Creates or updates exactly one workspace. */
    @Upsert suspend fun saveWorkspace(value: PolicyWorkspaceEntity)
    /** Deletes settings, owned private definitions and activation references through foreign keys. */
    @Query("DELETE FROM policy_workspaces WHERE `key` = :key") suspend fun forgetWorkspace(key: String)
    /** Explicit Forget removes the identity, cascading its credentials, private policy and activations atomically. */
    @Query("DELETE FROM registered_devices WHERE id = :id") suspend fun forgetRegisteredDevice(id: String)
    /** Reads independent breakpoint bindings. */
    @Query("SELECT * FROM policy_breakpoint_activations") suspend fun breakpointActivations(): List<BreakpointActivationEntity>
    /** Atomically upserts a workspace's breakpoint activation. */
    @Upsert suspend fun saveBreakpointActivation(value: BreakpointActivationEntity)
    /** Reads independent condition bindings. */
    @Query("SELECT * FROM policy_condition_activations") suspend fun conditionActivations(): List<ConditionActivationEntity>
    /** Atomically upserts a workspace's condition activation. */
    @Upsert suspend fun saveConditionActivation(value: ConditionActivationEntity)
    /** Reads independent protection bindings. */
    @Query("SELECT * FROM policy_protection_activations") suspend fun protectionActivations(): List<ProtectionActivationEntity>
    /** Atomically upserts a workspace's protection activation. */
    @Upsert suspend fun saveProtectionActivation(value: ProtectionActivationEntity)
}
