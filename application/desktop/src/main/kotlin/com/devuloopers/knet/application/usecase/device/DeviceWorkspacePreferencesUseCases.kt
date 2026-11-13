package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import kotlinx.coroutines.flow.Flow

/** Observes the persisted device-tab selection through the application boundary. */
public class ObserveDeviceWorkspaceSelectionUseCase(private val preferences: DeviceWorkspacePreferences) {
    /** Returns typed, non-secret selection updates. */
    public fun execute(): Flow<TrafficSource> = preferences.selectedSource
}

/** Persists user selection independently of any device connection action. */
public class SelectDeviceWorkspaceUseCase(private val preferences: DeviceWorkspacePreferences) {
    /** Saves the selected source; All remains reserved for administrative consumers. */
    public suspend fun execute(source: TrafficSource) {
        require(source != TrafficSource.All)
        preferences.select(source)
    }
}
