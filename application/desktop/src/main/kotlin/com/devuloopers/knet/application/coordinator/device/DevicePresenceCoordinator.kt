package com.devuloopers.knet.application.coordinator.device

import com.devuloopers.knet.application.contract.device.DevicePresence
import com.devuloopers.knet.application.contract.device.DevicePresenceReader
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DevicePolicyObservation
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded, synchronized lease state machine. Authentication is performed before [open] by connectivity.
 * A token fences every update and close against the current channel generation, including A/B/A reconnects.
 * [nowMillis] must use one monotonic desktop clock; wall-clock changes cannot extend a lease.
 */
public class DevicePresenceCoordinator(
    private val nowMillis: () -> Long,
    private val leaseMillis: Long = 60_000L,
    private val maximumDevices: Int = 128,
) : DevicePresenceReader {
    private data class Lease(val token: Long, val sequence: Long, val observation: DevicePresence)
    private val lock = Any()
    private val leases = linkedMapOf<RegisteredDeviceId, Lease>()
    private val mutablePresence = MutableStateFlow<Map<RegisteredDeviceId, DevicePresence>>(emptyMap())
    private var nextToken = 0L

    /** Read-only immutable snapshots; socket objects and credentials never enter presentation. */
    override val presence: StateFlow<Map<RegisteredDeviceId, DevicePresence>> = mutablePresence.asStateFlow()

    init { require(leaseMillis > 0L && maximumDevices in 1..4096) }

    /** Starts a new authoritative run and returns its generation; rejects overflow rather than evicting a live peer. */
    public fun open(deviceId: RegisteredDeviceId, runId: String, sequence: Long): Long = synchronized(lock) {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && sequence >= 0L)
        expireLocked()
        check(deviceId in leases || leases.size < maximumDevices) { "Device presence capacity reached." }
        check(nextToken < Long.MAX_VALUE) { "Device presence generation exhausted." }
        val token = ++nextToken
        leases[deviceId] = Lease(token, sequence, DevicePresence(DevicePresenceStatus.CONNECTED, runId, nowMillis()))
        publishLocked()
        token
    }

    /** Renews only a newer application heartbeat on the current channel. Duplicates never extend expiry. */
    public fun heartbeat(deviceId: RegisteredDeviceId, token: Long, runId: String, sequence: Long): Boolean = synchronized(lock) {
        expireLocked()
        val lease = leases[deviceId] ?: return@synchronized false
        if (lease.token != token || lease.observation.runId != runId || sequence <= lease.sequence) return@synchronized false
        leases[deviceId] = lease.copy(sequence = sequence,
            observation = lease.observation.copy(status = DevicePresenceStatus.CONNECTED, lastHeartbeatMillis = nowMillis()))
        publishLocked()
        true
    }

    /** Stops an explicit current run. A late close from an older socket cannot remove a newer connection. */
    public fun stop(deviceId: RegisteredDeviceId, token: Long): Boolean = synchronized(lock) {
        if (leases[deviceId]?.token != token) return@synchronized false
        leases.remove(deviceId)
        publishLocked()
        true
    }

    /** Records unexpected control loss without closing the device's data-plane streams. */
    public fun disconnected(deviceId: RegisteredDeviceId, token: Long): Unit = synchronized(lock) {
        val lease = leases[deviceId]
        if (lease?.token == token) {
            leases[deviceId] = lease.copy(observation = lease.observation.copy(status = DevicePresenceStatus.RECONNECTING))
            expireLocked()
            publishLocked()
        }
    }

    /** Checks a socket's authority and expires stale observations at the exact lease boundary. */
    public fun isCurrent(deviceId: RegisteredDeviceId, token: Long): Boolean = synchronized(lock) {
        expireLocked()
        leases[deviceId]?.token == token
    }

    /** Called by the process-owned timer even after the last socket has disconnected. */
    public fun expire(): Unit = synchronized(lock) { expireLocked() }

    /** Records actual application without renewing the independent VPN presence lease. Stale runs cannot overwrite it. */
    public fun acknowledge(deviceId: RegisteredDeviceId, token: Long, observation: DevicePolicyObservation): Boolean = synchronized(lock) {
        require(observation.revision > 0L)
        expireLocked()
        val lease = leases[deviceId] ?: return@synchronized false
        if (lease.token != token || observation.revision < (lease.observation.policy?.revision ?: 0L)) return@synchronized false
        leases[deviceId] = lease.copy(observation = lease.observation.copy(policy = observation))
        publishLocked()
        true
    }

    /** Clears ephemeral status during desktop shutdown. Durable names/history are untouched. */
    public fun clear(): Unit = synchronized(lock) { leases.clear(); publishLocked() }

    private fun expireLocked() {
        val now = nowMillis()
        val removed = leases.entries.removeAll { (_, lease) -> now - lease.observation.lastHeartbeatMillis >= leaseMillis }
        if (removed) publishLocked()
    }

    private fun publishLocked() { mutablePresence.value = leases.mapValues { (_, lease) -> lease.observation } }
}
