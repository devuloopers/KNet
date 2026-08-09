package com.devuloopers.knet.data.desktop.network.repository

import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.engine.proxy.network.LocalIpResolver
import kotlinx.coroutines.flow.Flow

/**
 * Desktop repository implementation consuming [LocalIpResolver] engine to provide reactive local IP data streams.
 *
 * @param localIpResolver Engine component for host system network interface resolution.
 */
public class NetworkRepositoryImpl(
    private val localIpResolver: LocalIpResolver
) : NetworkRepository {

    override fun observeLocalIp(pollIntervalMs: Long): Flow<String> {
        return localIpResolver.observeLocalIpAddress(pollIntervalMs)
    }

    override suspend fun getLocalIp(): String {
        return localIpResolver.getLocalIpAddress()
    }
}
