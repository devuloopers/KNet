package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.contract.traffic.CaptureSessionControl
import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Clears stored traffic without stopping the proxy or closing connected client transports.
 *
 * Capture rotation completes first. If deletion later fails, the previous terminal session remains
 * retryable and the replacement session continues capturing without a dual-writer interval.
 */
public class ClearTrafficHistoryUseCase(
    private val captureSessionControl: CaptureSessionControl,
    private val trafficMaintenance: TrafficMaintenance,
) {
    private val operationMutex = Mutex()

    /**
     * Rotates capture ownership when necessary and then clears only terminal traffic storage.
     *
     * @return Capture preparation performed before deletion.
     */
    public suspend fun execute(): CaptureClearPreparation = operationMutex.withLock {
        val preparation = captureSessionControl.rotateForTrafficClear()
        trafficMaintenance.clearTerminalTraffic()
        preparation
    }
}
