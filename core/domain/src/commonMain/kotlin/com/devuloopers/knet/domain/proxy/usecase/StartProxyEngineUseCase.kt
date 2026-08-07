package com.devuloopers.knet.domain.proxy.usecase

import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository

/**
 * Domain UseCase that starts the Netty Proxy Engine on the specified port.
 */
public class StartProxyEngineUseCase(
    private val repository: ProxyEngineRepository
) {
    public companion object {
        /**
         * Default local proxy port used by KNet's Netty MITM proxy engine.
         * All consumers (TrafficViewModel, ApiStudioViewModel, etc.) must reference
         * this constant as the single source of truth for the proxy port.
         */
        public const val DEFAULT_PORT: Int = 8080
    }

    public suspend fun execute(port: Int = DEFAULT_PORT) {
        repository.start(port)
    }
}

