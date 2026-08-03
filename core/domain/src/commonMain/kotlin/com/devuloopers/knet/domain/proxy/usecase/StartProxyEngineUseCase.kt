package com.devuloopers.knet.domain.proxy.usecase

import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository

/**
 * Domain UseCase that starts the Netty Proxy Engine on the specified port.
 */
public class StartProxyEngineUseCase(
    private val repository: ProxyEngineRepository
) {
    public suspend fun execute(port: Int = 8080) {
        repository.start(port)
    }
}
