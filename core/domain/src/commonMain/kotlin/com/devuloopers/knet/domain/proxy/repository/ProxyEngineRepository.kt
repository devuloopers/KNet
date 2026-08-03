package com.devuloopers.knet.domain.proxy.repository

import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for controlling Netty Proxy Engine runtime lifecycle and observing state.
 */
public interface ProxyEngineRepository {

    /**
     * Starts the proxy engine on the given port.
     */
    public suspend fun start(port: Int = 8080)

    /**
     * Stops the running proxy engine.
     */
    public suspend fun stop()

    /**
     * Stream of operational engine states.
     */
    public fun engineState(): Flow<ProxyEngineState>
}
