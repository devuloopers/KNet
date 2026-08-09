package com.devuloopers.knet.domain.network.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for observing active host network interfaces and local IPv4 addresses.
 */
public interface NetworkRepository {

    /**
     * Emits active host IPv4 address reactively as a Flow stream.
     *
     * @param pollIntervalMs Polling ticker interval in milliseconds.
     */
    public fun observeLocalIp(pollIntervalMs: Long = 3000L): Flow<String>

    /**
     * Fetches instant single-shot host IPv4 address.
     */
    public suspend fun getLocalIp(): String
}
