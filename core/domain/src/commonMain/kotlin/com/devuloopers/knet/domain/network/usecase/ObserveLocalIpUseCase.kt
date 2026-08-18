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
     * @param pollIntervalMs Ticker polling interval in milliseconds. Defaults to 3000ms.
     */
    fun execute(pollIntervalMs: Long = 3000L): Flow<String> {
        return repository.observeLocalIp(pollIntervalMs)
    }
}
