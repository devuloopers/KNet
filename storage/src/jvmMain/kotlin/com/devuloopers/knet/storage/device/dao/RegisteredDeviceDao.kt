package com.devuloopers.knet.storage.device.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.devuloopers.knet.storage.device.entity.PairingInvitationEntity
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceCredentialEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceRecord
import kotlinx.coroutines.flow.Flow

/** Room access boundary for registered identity, trusted credentials, and one-time pairing invitations. */
@Dao
abstract class RegisteredDeviceDao {
    /** Inserts or replaces one registered identity. */
    @Upsert
    abstract suspend fun upsertRegisteredDevice(device: RegisteredDeviceEntity)

    /** Returns one identity regardless of revocation state. */
    @Query("SELECT * FROM registered_devices WHERE id = :id")
    abstract suspend fun getRegisteredDevice(id: String): RegisteredDeviceEntity?

    /** Observes selectable, non-revoked identities ordered by most recent use. */
    @Query(
        "SELECT * FROM registered_devices WHERE revokedAtEpochMillis IS NULL " +
            "ORDER BY lastSeenAtEpochMillis DESC, displayName COLLATE NOCASE ASC",
    )
    abstract fun observeRegisteredDevices(): Flow<List<RegisteredDeviceEntity>>

    /** Updates last-seen time only when the identity is present and active. */
    @Query(
        "UPDATE registered_devices SET lastSeenAtEpochMillis = :seenAtEpochMillis " +
            "WHERE id = :id AND revokedAtEpochMillis IS NULL",
    )
    abstract suspend fun markRegisteredDeviceSeen(id: String, seenAtEpochMillis: Long): Int

    /** Revokes one identity while retaining its audit and credential record. */
    @Query(
        "UPDATE registered_devices SET revokedAtEpochMillis = :revokedAtEpochMillis " +
            "WHERE id = :id AND revokedAtEpochMillis IS NULL",
    )
    abstract suspend fun revokeRegisteredDevice(id: String, revokedAtEpochMillis: Long): Int

    /** Inserts or replaces one trusted credential row. */
    @Upsert
    protected abstract suspend fun upsertTrustedCredential(credential: TrustedDeviceCredentialEntity)

    /** Atomically persists the identity and its trusted credential. */
    @Transaction
    open suspend fun putTrustedDevice(
        device: RegisteredDeviceEntity,
        credential: TrustedDeviceCredentialEntity,
    ) {
        upsertRegisteredDevice(device)
        upsertTrustedCredential(credential)
    }

    /** Returns one joined trusted-device projection by stable identity. */
    @Query(TRUSTED_DEVICE_SELECT + " WHERE registered.id = :id")
    abstract suspend fun getTrustedDevice(id: String): TrustedDeviceRecord?

    /** Observes all joined trusted devices, including revoked devices needed for active-stream termination. */
    @Query(TRUSTED_DEVICE_SELECT + " ORDER BY registered.registeredAtEpochMillis ASC")
    abstract fun observeTrustedDevices(): Flow<List<TrustedDeviceRecord>>

    /** Inserts or replaces one pending invitation row. */
    @Upsert
    protected abstract suspend fun upsertInvitation(invitation: PairingInvitationEntity)

    /** Removes invitations that can no longer be claimed. */
    @Query("DELETE FROM pairing_invitations WHERE expiresAtEpochMillis <= :nowEpochMillis")
    protected abstract suspend fun deleteExpiredInvitations(nowEpochMillis: Long)

    /** Retains only the newest bounded invitation rows. */
    @Query(
        "DELETE FROM pairing_invitations WHERE id NOT IN " +
            "(SELECT id FROM pairing_invitations ORDER BY createdAtEpochMillis DESC LIMIT :maximumCount)",
    )
    protected abstract suspend fun trimInvitations(maximumCount: Int)

    /** Atomically inserts an invitation after expiry cleanup and bounded retention. */
    @Transaction
    open suspend fun putInvitation(
        invitation: PairingInvitationEntity,
        nowEpochMillis: Long,
        maximumCount: Int,
    ) {
        deleteExpiredInvitations(nowEpochMillis)
        upsertInvitation(invitation)
        trimInvitations(maximumCount)
    }

    /** Returns an invitation only when its digest matches and it has not expired. */
    @Query(
        "SELECT * FROM pairing_invitations WHERE id = :id AND secretDigest = :secretDigest " +
            "AND expiresAtEpochMillis > :nowEpochMillis",
    )
    protected abstract suspend fun getClaimableInvitation(
        id: String,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PairingInvitationEntity?

    /** Deletes an exact invitation/digest pair and reports the affected row count. */
    @Query("DELETE FROM pairing_invitations WHERE id = :id AND secretDigest = :secretDigest")
    protected abstract suspend fun deleteInvitation(id: String, secretDigest: String): Int

    /** Atomically consumes a valid invitation, preventing successful replay. */
    @Transaction
    open suspend fun claimInvitation(
        id: String,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PairingInvitationEntity? {
        val invitation = getClaimableInvitation(id, secretDigest, nowEpochMillis) ?: return null
        return invitation.takeIf { deleteInvitation(id, secretDigest) == 1 }
    }

    private companion object {
        const val TRUSTED_DEVICE_SELECT: String =
            "SELECT registered.id AS deviceId, registered.displayName AS displayName, " +
                "registered.registrationKind AS registrationKind, " +
                "registered.registeredAtEpochMillis AS registeredAtEpochMillis, " +
                "registered.lastSeenAtEpochMillis AS lastSeenAtEpochMillis, " +
                "registered.revokedAtEpochMillis AS revokedAtEpochMillis, " +
                "credential.publicKeyEncoded AS publicKeyEncoded, " +
                "credential.credentialDigest AS credentialDigest, credential.scopesMask AS scopesMask, " +
                "credential.pairedAtEpochMillis AS pairedAtEpochMillis, " +
                "credential.credentialExpiresAtEpochMillis AS credentialExpiresAtEpochMillis " +
                "FROM registered_devices AS registered INNER JOIN trusted_device_credentials AS credential " +
                "ON credential.deviceId = registered.id"
    }
}
