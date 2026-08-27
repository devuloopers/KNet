package com.devuloopers.knet.companion.connectivity.transport

import java.net.DatagramSocket
import java.net.Socket

/** Android VPN callback that keeps companion carrier sockets outside the captured tunnel. */
public interface AndroidSocketProtector {
    /** Excludes one TCP socket from the active VPN before it is connected. */
    public fun protect(socket: Socket): Boolean

    /** Excludes one UDP socket from the active VPN before it is connected or used. */
    public fun protect(socket: DatagramSocket): Boolean
}
