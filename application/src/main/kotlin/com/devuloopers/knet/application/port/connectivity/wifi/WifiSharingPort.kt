package com.devuloopers.knet.application.port.connectivity.wifi

import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only application boundary for the automatically managed stock-phone Wi-Fi connection path.
 *
 * The desktop connectivity adapter follows proxy and network lifecycle state itself. UI and application
 * features can observe the result but cannot independently create a second LAN lifecycle.
 */
public interface WifiSharingPort {
    /** Current Wi-Fi listener, endpoint, setup-page, and bounded connection-metric state. */
    public val state: StateFlow<WifiSharingState>
}
