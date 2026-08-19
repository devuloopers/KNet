package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.TrafficMaintenancePort

/**
 * Clears stored traffic without stopping the proxy or closing connected client transports.
 *
 * Capture rotation completes first. If deletion later fails, the previous terminal session remains
 * retryable and the replacement session continues capturing without a dual-writer interval.
 */
public class ClearTrafficHistoryUseCase(
    private val captureSessionControl: CaptureSessionControlPort,
    private val trafficMaintenance: TrafficMaintenancePort,
) {
    /**
     * Rotates capture ownership when necessary and then clears only terminal traffic storage.
     *
     * @return Capture preparation performed before deletion.
     */
    public suspend fun execute(): CaptureClearPreparation {
        val preparation = captureSessionControl.rotateForTrafficClear()
        trafficMaintenance.clearTerminalTraffic()
        return preparation
    }
}
