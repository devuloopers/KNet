package com.devuloopers.knet.application.usecase.connectivity.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertSame

class WifiSharingUseCasesTest {
    @Test
    fun `observe use case exposes the runtime state flow without another lifecycle`() {
        val port = RecordingWifiSharingPort()

        assertSame(port.state, ObserveWifiSharingUseCase(port).execute())
    }

    private class RecordingWifiSharingPort : WifiSharingPort {
        override val state: StateFlow<WifiSharingState> =
            MutableStateFlow(WifiSharingState.Disabled(emptyList()))
    }
}
