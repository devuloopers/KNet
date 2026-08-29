package com.devuloopers.knet.domain.network.usecase

import com.devuloopers.knet.domain.network.repository.NetworkRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe active host IPv4 address reactively.
 *
 * @param repository The network repository interface.
 */
class ObserveLocalIpUseCase(
    private val repository: NetworkRepository
) {
    /**
     * Executes reactive observation of local IPv4 address.
     *
     */
    fun execute(): Flow<String> = repository.observeLocalIp()
}
