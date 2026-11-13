package com.devuloopers.knet.application.contract.device

import com.devuloopers.knet.application.contract.traffic.TrafficSource
import kotlinx.coroutines.flow.Flow

/** Non-secret desktop workspace selection, independent of pairing and online state. */
public interface DeviceWorkspacePreferences {
    /** Source preference restored across process restarts. */
    public val selectedSource: Flow<TrafficSource>
    /** Persists a typed source; no captured rows, bodies, IP addresses or credentials are accepted. */
    public suspend fun select(source: TrafficSource)
}
