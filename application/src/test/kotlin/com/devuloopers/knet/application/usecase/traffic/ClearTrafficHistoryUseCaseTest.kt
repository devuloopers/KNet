package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.port.traffic.TrafficMaintenancePort
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests ordering at the cross-capability traffic-clear boundary. */
class ClearTrafficHistoryUseCaseTest {
    /** Verifies capture rotates before terminal storage is removed. */
    @Test
    fun `clear rotates capture before deleting traffic`() = runTest {
        val operations = mutableListOf<String>()
        val useCase = ClearTrafficHistoryUseCase(
            captureSessionControl = object : CaptureSessionControlPort {
                override val captureState: StateFlow<CaptureSessionState> =
                    MutableStateFlow(CaptureSessionState.Inactive)

                override suspend fun pause(): CapturePauseResult = CapturePauseResult.PROXY_INACTIVE

                override suspend fun resume(): CaptureResumeResult = CaptureResumeResult.ProxyInactive

                override suspend fun rotateForTrafficClear(): CaptureClearPreparation {
                    operations += "rotate"
                    return CaptureClearPreparation.CANONICAL_SESSION_ROTATED
                }
            },
            trafficMaintenance = object : TrafficMaintenancePort {
                override suspend fun clearTerminalTraffic() {
                    operations += "clear"
                }
            },
        )

        val result = useCase.execute()

        assertEquals(CaptureClearPreparation.CANONICAL_SESSION_ROTATED, result)
        assertEquals(listOf("rotate", "clear"), operations)
    }
}
