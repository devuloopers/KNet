package com.devuloopers.knet.domain.proxy.usecase

import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository

/**
 * Domain UseCase that stops the running Netty Proxy Engine cleanly.
 */
public class StopProxyEngineUseCase(
    private val repository: ProxyEngineRepository
) {
    public suspend fun execute() {
        repository.stop()
    }
}
