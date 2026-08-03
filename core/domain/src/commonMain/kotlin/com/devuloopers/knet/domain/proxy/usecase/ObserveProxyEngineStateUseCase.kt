package com.devuloopers.knet.domain.proxy.usecase

import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain UseCase that provides a stream of Proxy Engine operational states.
 */
public class ObserveProxyEngineStateUseCase(
    private val repository: ProxyEngineRepository
) {
    public fun execute(): Flow<ProxyEngineState> {
        return repository.engineState()
    }
}
