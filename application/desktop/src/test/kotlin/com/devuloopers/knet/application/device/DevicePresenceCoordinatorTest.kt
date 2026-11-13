package com.devuloopers.knet.application.device

import com.devuloopers.knet.application.coordinator.device.DevicePresenceCoordinator
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DevicePolicyObservation
import com.devuloopers.knet.companion.model.CompanionPolicyAcknowledgement
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlin.test.*

/** Deterministic lease boundary, concurrency-generation and resource-limit regressions. */
class DevicePresenceCoordinatorTest {
    private val device = RegisteredDeviceId("device-a")

    @Test fun `reconnect fences stale heartbeat disconnect stop and acknowledgements`() {
        var now = 0L
        val coordinator = DevicePresenceCoordinator({ now })
        val old = coordinator.open(device, "run-one", 1)
        now = 100
        val current = coordinator.open(device, "run-two", 1)
        assertFalse(coordinator.heartbeat(device, old, "run-one", 2))
        coordinator.disconnected(device, old)
        assertFalse(coordinator.stop(device, old))
        assertFalse(coordinator.acknowledge(device, old, DevicePolicyObservation(1, CompanionPolicyAcknowledgement.APPLIED)))
        assertTrue(coordinator.isCurrent(device, current))
        assertEquals(DevicePresenceStatus.CONNECTED, coordinator.presence.value[device]?.status)
        assertEquals("run-two", coordinator.presence.value[device]?.runId)
    }

    @Test fun `duplicate heartbeat cannot extend exact expiry boundary`() {
        var now = 0L
        val coordinator = DevicePresenceCoordinator({ now }, leaseMillis = 1000)
        val token = coordinator.open(device, "run", 1)
        now = 999
        assertFalse(coordinator.heartbeat(device, token, "run", 1))
        assertFalse(coordinator.heartbeat(device, token, "foreign", 2))
        assertTrue(coordinator.isCurrent(device, token))
        now = 1000
        assertFalse(coordinator.isCurrent(device, token))
        assertTrue(coordinator.presence.value.isEmpty())
        assertFalse(coordinator.heartbeat(device, token, "run", 2))
    }

    @Test fun `unexpected control loss reconnects until lease expires while normal stop is immediate`() {
        var now = 0L
        val coordinator = DevicePresenceCoordinator({ now }, leaseMillis = 1000)
        val token = coordinator.open(device, "run", 1)
        coordinator.disconnected(device, token)
        assertEquals(DevicePresenceStatus.RECONNECTING, coordinator.presence.value[device]?.status)
        now = 500
        assertTrue(coordinator.heartbeat(device, token, "run", 2))
        now = 1499
        coordinator.expire()
        assertEquals(DevicePresenceStatus.CONNECTED, coordinator.presence.value[device]?.status)
        now = 1500
        coordinator.expire()
        assertTrue(coordinator.presence.value.isEmpty())
        val replacement = coordinator.open(device, "new-run", 1)
        assertTrue(coordinator.stop(device, replacement))
        assertTrue(coordinator.presence.value.isEmpty())
    }

    @Test fun `capacity never evicts another live device and expired slots become reusable`() {
        var now = 0L
        val coordinator = DevicePresenceCoordinator({ now }, leaseMillis = 1000, maximumDevices = 1)
        coordinator.open(device, "run", 1)
        assertFailsWith<IllegalStateException> { coordinator.open(RegisteredDeviceId("b"), "run", 1) }
        assertEquals(setOf(device), coordinator.presence.value.keys)
        now = 1000
        coordinator.open(RegisteredDeviceId("b"), "run", 1)
        assertEquals(setOf(RegisteredDeviceId("b")), coordinator.presence.value.keys)
        coordinator.clear()
        assertTrue(coordinator.presence.value.isEmpty())
    }

    @Test fun `policy application is revisioned and never renews presence`() {
        var now = 0L
        val coordinator = DevicePresenceCoordinator({ now }, leaseMillis = 1000)
        val token = coordinator.open(device, "run", 1)
        now = 900
        assertTrue(coordinator.acknowledge(device, token, DevicePolicyObservation(3, CompanionPolicyAcknowledgement.UNSUPPORTED)))
        assertFalse(coordinator.acknowledge(device, token, DevicePolicyObservation(2, CompanionPolicyAcknowledgement.APPLIED)))
        assertEquals(CompanionPolicyAcknowledgement.UNSUPPORTED, coordinator.presence.value[device]?.policy?.result)
        now = 1000
        coordinator.expire()
        assertTrue(coordinator.presence.value.isEmpty())
    }

    @Test fun `invalid run sequence capacity and lease policy are rejected`() {
        assertFailsWith<IllegalArgumentException> { DevicePresenceCoordinator({ 0 }, 0) }
        assertFailsWith<IllegalArgumentException> { DevicePresenceCoordinator({ 0 }, maximumDevices = 0) }
        val coordinator = DevicePresenceCoordinator({ 0 })
        for (run in listOf("", " ", "x\n", "x".repeat(129), "x/y")) {
            assertFailsWith<IllegalArgumentException> { coordinator.open(device, run, 0) }
        }
        assertFailsWith<IllegalArgumentException> { coordinator.open(device, "run", -1) }
    }
}
