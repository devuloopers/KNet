package com.devuloopers.knet.domain.proxy.model

/**
 * Operational state of the Netty MITM proxy engine.
 *
 * [Running] carries the active [port] to allow consumers to route traffic
 * through the proxy without hard-coding the port number independently.
 */
public sealed interface ProxyEngineState {
    public data object Stopped : ProxyEngineState
    public data object Starting : ProxyEngineState

    /**
     * Proxy engine is active and accepting connections.
     *
     * @param port The port on which the proxy server is bound and listening.
     */
    public data class Running(val port: Int) : ProxyEngineState

    public data object Stopping : ProxyEngineState
    public data class Error(val message: String) : ProxyEngineState
}
