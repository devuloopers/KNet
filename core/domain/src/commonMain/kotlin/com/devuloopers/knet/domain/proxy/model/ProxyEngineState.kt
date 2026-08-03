package com.devuloopers.knet.domain.proxy.model

/**
 * Operational state of the Netty MITM proxy engine.
 */
public sealed interface ProxyEngineState {
    public data object Stopped : ProxyEngineState
    public data object Starting : ProxyEngineState
    public data object Running : ProxyEngineState
    public data object Stopping : ProxyEngineState
    public data class Error(val message: String) : ProxyEngineState
}
