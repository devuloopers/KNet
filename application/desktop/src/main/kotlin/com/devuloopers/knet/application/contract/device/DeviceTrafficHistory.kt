package com.devuloopers.knet.application.contract.device

import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.flow.Flow

/** Payload-free membership of verified device identities with locally retained canonical traffic. */
public interface DeviceTrafficHistory {
    /**
     * Observes distinct identities with at least one HTTP exchange or opaque flow across retained sessions.
     * Search filters, byte counts and transport completion do not affect membership. Connection records
     * without traffic do not qualify. Implementations observe storage changes without loading traffic rows.
     */
    public fun observeDeviceIdsWithTraffic(): Flow<Set<RegisteredDeviceId>>
}
