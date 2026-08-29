package com.devuloopers.knet.domain.network.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for observing active host network interfaces and local IPv4 addresses.
 */
interface NetworkRepository {

    /**
     * Emits active host IPv4 address reactively as a Flow stream.
     *
     */
    fun observeLocalIp(): Flow<String>

    /**
     * Fetches instant single-shot host IPv4 address.
     */
    suspend fun getLocalIp(): String
}
