package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.device.DevicePresenceReader
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry
import com.devuloopers.knet.application.contract.device.DeviceTrafficHistory
import com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first

/** Combines durable registration with ephemeral status without allowing heartbeats to reorder device tabs. */
public class ObserveDeviceWorkspacesUseCase(
    private val devices: RegisteredDeviceStore,
    private val presence: DevicePresenceReader,
    private val history: DeviceTrafficHistory,
    private val preferences: DeviceWorkspacePreferences,
) {
    /** Observes open tabs, online/grace-period devices and legacy offline history in registration order. */
    public fun execute(): Flow<List<DeviceWorkspaceEntry>> = combine(
        devices.observeRetainedDevices(), presence.presence, history.observeDeviceIdsWithTraffic(), preferences.openDevices,
    ) { registered, observed, retained, open ->
        registered.sortedWith(compareBy({ device -> device.registeredAtEpochMillis }, { device -> device.id.value }))
            .map { device -> DeviceWorkspaceEntry(device, if (device.isRevoked) DevicePresenceStatus.OFFLINE else
                observed[device.id]?.status ?: DevicePresenceStatus.OFFLINE) }
            .filter { entry -> entry.status != DevicePresenceStatus.OFFLINE || entry.device.id in retained || entry.device.id in open }
    }.distinctUntilChanged().onEach { entries ->
        // A connected tab remains open when its device later disconnects, even without traffic.
        val open = preferences.openDevices.first()
        entries.filter { it.status != DevicePresenceStatus.OFFLINE && it.device.id !in open }
            .forEach { preferences.setOpen(it.device.id, true) }
    }
}
