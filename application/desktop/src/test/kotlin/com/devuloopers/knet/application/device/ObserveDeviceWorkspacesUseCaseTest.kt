package com.devuloopers.knet.application.device

import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.usecase.device.ObserveDeviceWorkspacesUseCase
import com.devuloopers.knet.identity.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Tab membership uses canonical history and authenticated presence, never filtered page contents. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveDeviceWorkspacesUseCaseTest {
    @Test fun `only online grace period or retained history qualify and revocation cannot imply online`() = runTest {
        val devices = (1..5).map { index ->
            RegisteredDevice(RegisteredDeviceId("device-$index"), "Same name", DeviceRegistrationKind.PAIRED_COMPANION,
                index.toLong(), index.toLong(), revokedAtEpochMillis = if (index == 5) 10L else null)
        }
        val registry = object : RegisteredDeviceStore {
            override suspend fun putRegisteredDevice(device: RegisteredDevice) = error("Read-only")
            override suspend fun getRegisteredDevice(id: RegisteredDeviceId) = devices.find { device -> device.id == id }
            override suspend fun markRegisteredDeviceSeen(id: RegisteredDeviceId, seenAtEpochMillis: Long) = error("Read-only")
            override suspend fun revokeRegisteredDevice(id: RegisteredDeviceId, revokedAtEpochMillis: Long) = error("Must retain pairing")
            override fun observeRegisteredDevices() = flowOf(devices.reversed())
        }
        val observed = MutableStateFlow(mapOf(
            devices[0].id to DevicePresence(DevicePresenceStatus.CONNECTED, "run-a", 1),
            devices[1].id to DevicePresence(DevicePresenceStatus.RECONNECTING, "run-b", 1),
            devices[4].id to DevicePresence(DevicePresenceStatus.CONNECTED, "stale-revoked-run", 1),
        ))
        val presence = object : DevicePresenceReader { override val presence = observed }
        val retained = MutableStateFlow(setOf(devices[2].id, RegisteredDeviceId("unknown")))
        val history = object : DeviceTrafficHistory { override fun observeDeviceIdsWithTraffic() = retained }
        var latest = emptyList<DeviceWorkspaceEntry>()
        var emissions = 0
        backgroundScope.launch {
            ObserveDeviceWorkspacesUseCase(registry, presence, history).execute().collect { entries ->
                latest = entries; emissions++
            }
        }
        runCurrent()
        assertEquals(devices.take(3).map { device -> device.id }, latest.map { entry -> entry.device.id })
        assertEquals(listOf(DevicePresenceStatus.CONNECTED, DevicePresenceStatus.RECONNECTING, DevicePresenceStatus.OFFLINE), latest.map { entry -> entry.status })

        observed.value = observed.value.mapValues { (_, value) -> value.copy(lastHeartbeatMillis = 2) }
        runCurrent()
        assertEquals(1, emissions, "Heartbeats without presentation changes must not redraw the tab list")
        retained.value += devices[4].id
        runCurrent()
        assertEquals(DevicePresenceStatus.OFFLINE, latest.last().status)
        retained.value = emptySet()
        runCurrent()
        assertEquals(devices.take(2).map { device -> device.id }, latest.map { entry -> entry.device.id })
        observed.value = emptyMap()
        runCurrent()
        assertTrue(latest.isEmpty())
        retained.value = setOf(devices[3].id)
        runCurrent()
        assertEquals(devices[3].id, latest.single().device.id, "History arriving while offline must reveal its tab")
    }
}
