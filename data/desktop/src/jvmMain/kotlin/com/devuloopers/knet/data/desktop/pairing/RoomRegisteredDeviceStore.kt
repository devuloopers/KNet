package com.devuloopers.knet.data.desktop.pairing

import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.identity.DeviceRegistrationKind
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.TrustedDevice
import com.devuloopers.knet.storage.device.dao.RegisteredDeviceDao
import com.devuloopers.knet.storage.device.entity.PairingInvitationEntity
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceCredentialEntity
import com.devuloopers.knet.storage.device.entity.TrustedDeviceRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Room-backed single source of truth for registered identities and cryptographic pairing state.
 *
 * The adapter stores only one-way credential/invitation digests. Plain issued credentials and invitation
 * secrets remain outside Room and are returned only through their short-lived application results.
 */
class RoomRegisteredDeviceStore(
    private val dao: RegisteredDeviceDao,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maximumPendingInvitations: Int = DEFAULT_MAXIMUM_PENDING_INVITATIONS,
) : RegisteredDeviceStore, TrustedDeviceStore {
    init {
        require(maximumPendingInvitations in 1..MAXIMUM_CONFIGURABLE_PENDING_INVITATIONS)
    }

    override suspend fun putRegisteredDevice(device: RegisteredDevice) {
        dao.upsertRegisteredDevice(device.toEntity())
    }

    override suspend fun getRegisteredDevice(id: RegisteredDeviceId): RegisteredDevice? =
        dao.getRegisteredDevice(id.value)?.toDomain()

    override suspend fun markRegisteredDeviceSeen(
        id: RegisteredDeviceId,
        seenAtEpochMillis: Long,
    ): Boolean = dao.markRegisteredDeviceSeen(id.value, seenAtEpochMillis) == 1

    override suspend fun revokeRegisteredDevice(
        id: RegisteredDeviceId,
        revokedAtEpochMillis: Long,
    ): Boolean = revokeIdentity(id, revokedAtEpochMillis)

    override fun observeRegisteredDevices(): Flow<List<RegisteredDevice>> =
        dao.observeRegisteredDevices().map { rows -> rows.map { row -> row.toDomain() } }

    override suspend fun putInvitation(invitation: PendingPairingInvitation) {
        dao.putInvitation(invitation.toEntity(), nowMillis(), maximumPendingInvitations)
    }

    override suspend fun claimInvitation(
        id: PairingInvitationId,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PendingPairingInvitation? = dao.claimInvitation(id.value, secretDigest, nowEpochMillis)?.toDomain()

    override suspend fun putDevice(device: TrustedDevice) {
        dao.putTrustedDevice(
            device = device.registeredDevice.toEntity(),
            credential = TrustedDeviceCredentialEntity(
                deviceId = device.id.value,
                publicKeyEncoded = device.publicKeyEncoded,
                credentialDigest = device.credentialDigest,
                scopesMask = device.scopes.toMask(),
                pairedAtEpochMillis = device.pairedAtEpochMillis,
                credentialExpiresAtEpochMillis = device.credentialExpiresAtEpochMillis,
            ),
        )
    }

    override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? =
        dao.getTrustedDevice(id.value)?.toDomain()

    override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean =
        revokeIdentity(id, revokedAtEpochMillis)

    override fun observeDevices(): Flow<List<TrustedDevice>> =
        dao.observeTrustedDevices().map { rows -> rows.map { row -> row.toDomain() } }

    private suspend fun revokeIdentity(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean {
        if (dao.revokeRegisteredDevice(id.value, revokedAtEpochMillis) == 1) return true
        return dao.getRegisteredDevice(id.value)?.revokedAtEpochMillis != null
    }

    private fun RegisteredDevice.toEntity(): RegisteredDeviceEntity = RegisteredDeviceEntity(
        id = id.value,
        displayName = displayName,
        registrationKind = registrationKind.name,
        registeredAtEpochMillis = registeredAtEpochMillis,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        revokedAtEpochMillis = revokedAtEpochMillis,
    )

    private fun RegisteredDeviceEntity.toDomain(): RegisteredDevice = RegisteredDevice(
        id = RegisteredDeviceId(id),
        displayName = displayName,
        registrationKind = DeviceRegistrationKind.valueOf(registrationKind),
        registeredAtEpochMillis = registeredAtEpochMillis,
        lastSeenAtEpochMillis = lastSeenAtEpochMillis,
        revokedAtEpochMillis = revokedAtEpochMillis,
    )

    private fun PendingPairingInvitation.toEntity(): PairingInvitationEntity = PairingInvitationEntity(
        id = id.value,
        secretDigest = secretDigest,
        expiresAtEpochMillis = expiresAtEpochMillis,
        scopesMask = scopes.toMask(),
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun PairingInvitationEntity.toDomain(): PendingPairingInvitation = PendingPairingInvitation(
        id = PairingInvitationId(id),
        secretDigest = secretDigest,
        expiresAtEpochMillis = expiresAtEpochMillis,
        scopes = scopesMask.toScopes(),
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun TrustedDeviceRecord.toDomain(): TrustedDevice = TrustedDevice(
        registeredDevice = RegisteredDevice(
            id = RegisteredDeviceId(deviceId),
            displayName = displayName,
            registrationKind = DeviceRegistrationKind.valueOf(registrationKind),
            registeredAtEpochMillis = registeredAtEpochMillis,
            lastSeenAtEpochMillis = lastSeenAtEpochMillis,
            revokedAtEpochMillis = revokedAtEpochMillis,
        ),
        publicKeyEncoded = publicKeyEncoded,
        credentialDigest = credentialDigest,
        scopes = scopesMask.toScopes(),
        pairedAtEpochMillis = pairedAtEpochMillis,
        credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
    )

    private fun Set<DeviceScope>.toMask(): Int {
        require(isNotEmpty()) { "At least one device scope is required." }
        return fold(0) { mask, scope -> mask or scope.bit }
    }

    private fun Int.toScopes(): Set<DeviceScope> {
        require(this and KNOWN_SCOPE_MASK == this && this != 0) { "Stored device scope mask is invalid." }
        return DeviceScope.entries.filterTo(linkedSetOf()) { scope -> this and scope.bit != 0 }
    }

    private val DeviceScope.bit: Int
        get() = when (this) {
            DeviceScope.PROXY_STREAM -> 1 shl 0
            DeviceScope.SETUP_ARTIFACT_READ -> 1 shl 1
            DeviceScope.TRAFFIC_METADATA_READ -> 1 shl 2
        }

    private companion object {
        const val DEFAULT_MAXIMUM_PENDING_INVITATIONS: Int = 128
        const val MAXIMUM_CONFIGURABLE_PENDING_INVITATIONS: Int = 4_096
        const val KNOWN_SCOPE_MASK: Int = (1 shl 0) or (1 shl 1) or (1 shl 2)
    }
}
