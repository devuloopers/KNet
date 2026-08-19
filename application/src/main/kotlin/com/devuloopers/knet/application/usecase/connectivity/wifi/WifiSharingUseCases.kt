package com.devuloopers.knet.application.usecase.connectivity.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.StateFlow

/** Observes the Wi-Fi gateway that automatically follows the desktop proxy lifecycle. */
public class ObserveWifiSharingUseCase(private val sharing: WifiSharingPort) {
    /** Returns the serialized Wi-Fi sharing state without exposing the platform runtime. */
    public fun execute(): StateFlow<WifiSharingState> = sharing.state
}
