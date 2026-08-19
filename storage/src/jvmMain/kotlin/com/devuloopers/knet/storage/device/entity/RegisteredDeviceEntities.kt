package com.devuloopers.knet.storage.device.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row containing durable user-visible identity independently from runtime network authorization. */
@Entity(tableName = "registered_devices")
data class RegisteredDeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val registrationKind: String,
    val registeredAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
)

/** Room row containing the non-plaintext authentication material for a cryptographically paired device. */
@Entity(
    tableName = "trusted_device_credentials",
    foreignKeys = [
        ForeignKey(
            entity = RegisteredDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deviceId"], unique = true)],
)
data class TrustedDeviceCredentialEntity(
    @PrimaryKey val deviceId: String,
    val publicKeyEncoded: String,
    val credentialDigest: String,
    val scopesMask: Int,
    val pairedAtEpochMillis: Long,
    val credentialExpiresAtEpochMillis: Long,
)

/** Room row containing only the digest and lifecycle data for one one-time pairing invitation. */
@Entity(tableName = "pairing_invitations")
data class PairingInvitationEntity(
    @PrimaryKey val id: String,
    val secretDigest: String,
    val expiresAtEpochMillis: Long,
    val scopesMask: Int,
    val createdAtEpochMillis: Long,
)

/** Joined Room projection used to reconstruct one trusted device without exposing entities to application code. */
data class TrustedDeviceRecord(
    val deviceId: String,
    val displayName: String,
    val registrationKind: String,
    val registeredAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?,
    val publicKeyEncoded: String,
    val credentialDigest: String,
    val scopesMask: Int,
    val pairedAtEpochMillis: Long,
    val credentialExpiresAtEpochMillis: Long,
)
