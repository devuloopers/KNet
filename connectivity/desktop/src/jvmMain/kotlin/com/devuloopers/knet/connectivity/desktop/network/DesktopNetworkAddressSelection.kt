package com.devuloopers.knet.connectivity.desktop.network

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkInterfaceKind

/** Returns stable LAN candidates ordered for display; runtime selection remains route-aware. */
internal fun List<NetworkAddress>.availableLanAddresses(): List<NetworkAddress> =
    filter { address -> !address.loopback && address.family == NetworkAddressFamily.IPV4 }
        .distinctBy { address -> address.interfaceId to address.address }
        .sortedWith(
            compareBy<NetworkAddress> { address -> address.interfaceKind.priority }
                .thenBy { address -> if (address.multicastSupported) 0 else 1 }
                .thenBy(NetworkAddress::interfaceId)
                .thenBy(NetworkAddress::address),
        )

private val NetworkInterfaceKind.priority: Int
    get() = when (this) {
        NetworkInterfaceKind.PHYSICAL -> 0
        NetworkInterfaceKind.UNKNOWN -> 1
        NetworkInterfaceKind.VIRTUAL -> 2
        NetworkInterfaceKind.VPN -> 3
    }
