package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.device.DevicePresenceReader
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry
import com.devuloopers.knet.application.contract.device.DeviceTrafficHistory
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Combines durable registration with ephemeral status without allowing heartbeats to reorder device tabs. */
public class ObserveDeviceWorkspacesUseCase(
    private val devices: RegisteredDeviceStore,
    private val presence: DevicePresenceReader,
    private val history: DeviceTrafficHistory,
) {
    /** Observes online/grace-period devices and offline history in stable registration order. */
    public fun execute(): Flow<List<DeviceWorkspaceEntry>> = combine(
        devices.observeRetainedDevices(), presence.presence, history.observeDeviceIdsWithTraffic(),
    ) { registered, observed, retained ->
        registered.sortedWith(compareBy({ device -> device.registeredAtEpochMillis }, { device -> device.id.value }))
            .map { device -> DeviceWorkspaceEntry(device, if (device.isRevoked) DevicePresenceStatus.OFFLINE else
                observed[device.id]?.status ?: DevicePresenceStatus.OFFLINE) }
            .filter { entry -> entry.status != DevicePresenceStatus.OFFLINE || entry.device.id in retained }
    }.distinctUntilChanged()
}
