package com.devuloopers.knet.data.desktop.network.repository

import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Desktop repository projection of the single route-aware connectivity snapshot.
 */
class NetworkRepositoryImpl(
    private val snapshots: StateFlow<NetworkSnapshot>,
) : NetworkRepository {

    override fun observeLocalIp(): Flow<String> = snapshots
        .map(NetworkSnapshot::localIpAddress)
        .distinctUntilChanged()

    override suspend fun getLocalIp(): String = snapshots.value.localIpAddress()
}

private fun NetworkSnapshot.localIpAddress(): String = preferredLanAddress?.address ?: LOOPBACK_ADDRESS

private const val LOOPBACK_ADDRESS: String = "127.0.0.1"
