package com.devuloopers.knet.application.usecase.connectivity.wifi

import com.devuloopers.knet.application.contract.connectivity.wifi.WifiSharing
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertSame

class WifiSharingUseCasesTest {
    @Test
    fun `observe use case exposes the runtime state flow without another lifecycle`() {
        val wifiSharing = RecordingWifiSharing()

        assertSame(wifiSharing.state, ObserveWifiSharingUseCase(wifiSharing).execute())
    }

    private class RecordingWifiSharing : WifiSharing {
        override val state: StateFlow<WifiSharingState> =
            MutableStateFlow(WifiSharingState.Disabled(emptyList()))
    }
}
