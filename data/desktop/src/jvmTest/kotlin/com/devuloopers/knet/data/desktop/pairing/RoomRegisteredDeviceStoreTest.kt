package com.devuloopers.knet.data.desktop.pairing

import com.devuloopers.knet.identity.DeviceRegistrationKind
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.TrustedDevice
import com.devuloopers.knet.storage.database.DatabaseFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomRegisteredDeviceStoreTest {
    @Test
    fun `registered and trusted identity survives database restart`() = runTest {
        val root = Files.createTempDirectory("knet-room-devices-")
        val databaseFile = root.resolve("knet.db").toFile()
        val identity = RegisteredDevice(
            id = RegisteredDeviceId("device-1"),
            displayName = "Test phone",
            registrationKind = DeviceRegistrationKind.PAIRED_COMPANION,
            registeredAtEpochMillis = 1_000L,
            lastSeenAtEpochMillis = 2_000L,
        )
        val trusted = TrustedDevice(
            registeredDevice = identity,
            publicKeyEncoded = "public-key",
            credentialDigest = "credential-digest",
            scopes = setOf(DeviceScope.PROXY_STREAM, DeviceScope.SETUP_ARTIFACT_READ),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 90_000L,
        )

        val initialDatabase = DatabaseFactory.create(databaseFile)
        try {
            val database = initialDatabase
            RoomRegisteredDeviceStore(database.registeredDeviceDao(), { 2_000L }).putDevice(trusted)
        } finally {
            initialDatabase.close()
        }

        val reopenedDatabase = DatabaseFactory.create(databaseFile)
        try {
            val database = reopenedDatabase
            val reloaded = RoomRegisteredDeviceStore(database.registeredDeviceDao(), { 3_000L })
            assertEquals(identity, reloaded.observeRegisteredDevices().first().single())
            assertEquals(trusted, reloaded.getDevice(identity.id))
            assertTrue(reloaded.revoke(identity.id, 3_000L))
            assertTrue(reloaded.getDevice(identity.id)?.isRevoked == true)
            assertTrue(reloaded.observeRegisteredDevices().first().isEmpty())
        } finally {
            reopenedDatabase.close()
        }

        root.toFile().deleteRecursively()
    }

    @Test
    fun `pairing invitation claim is one shot and expiration aware`() = runTest {
        val root = Files.createTempDirectory("knet-room-invitations-")
        val database = DatabaseFactory.create(root.resolve("knet.db").toFile())
        try {
            val store = RoomRegisteredDeviceStore(database.registeredDeviceDao(), { 1_000L })
            val invitation = PendingPairingInvitation(
                id = PairingInvitationId("invitation-1"),
                secretDigest = "digest-1",
                expiresAtEpochMillis = 5_000L,
                scopes = setOf(DeviceScope.PROXY_STREAM),
                createdAtEpochMillis = 1_000L,
            )
            store.putInvitation(invitation)

            assertNull(store.claimInvitation(invitation.id, "incorrect", 2_000L))
            assertEquals(invitation, store.claimInvitation(invitation.id, invitation.secretDigest, 2_000L))
            assertNull(store.claimInvitation(invitation.id, invitation.secretDigest, 2_000L))

            val expired = invitation.copy(
                id = PairingInvitationId("invitation-2"),
                expiresAtEpochMillis = 3_000L,
            )
            store.putInvitation(expired)
            assertNull(store.claimInvitation(expired.id, expired.secretDigest, 3_000L))
            assertFalse(database.registeredDeviceDao().observeRegisteredDevices().first().isNotEmpty())
        } finally {
            database.close()
            root.toFile().deleteRecursively()
        }
    }
}
