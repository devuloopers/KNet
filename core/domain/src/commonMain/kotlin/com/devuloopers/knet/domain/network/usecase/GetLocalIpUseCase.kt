package com.devuloopers.knet.domain.network.usecase

import com.devuloopers.knet.domain.network.repository.NetworkRepository

/**
 * Use case to fetch instant single-shot host IPv4 address.
 *
 * @param repository The network repository interface.
 */
public class GetLocalIpUseCase(
    private val repository: NetworkRepository
) {
    /**
     * Executes single-shot local IPv4 address resolution.
     */
    public suspend fun execute(): String {
        return repository.getLocalIp()
    }
}
