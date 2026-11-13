package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.device.SourceTrafficSnapshot
import com.devuloopers.knet.application.contract.device.SourceTrafficTelemetry
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Subscriber-owned one-second clock shared with the existing throughput presentation calculation. */
public class ObserveSourceTrafficUseCase(private val telemetry: SourceTrafficTelemetry) {
    /** Emits immediately for a zero baseline, then once per second. Extra byte/configuration events do not tick. */
    public fun execute(source: TrafficSource): Flow<SourceTrafficSnapshot> = flow {
        while (true) { emit(telemetry.snapshot(source)); delay(1000L) }
    }
}
