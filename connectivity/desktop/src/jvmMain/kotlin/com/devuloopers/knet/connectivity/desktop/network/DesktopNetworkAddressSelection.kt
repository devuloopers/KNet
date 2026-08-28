package com.devuloopers.knet.connectivity.desktop.network

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkSnapshot

/** Returns stable LAN candidates ordered ahead of virtual and publicly routed interfaces. */
internal fun List<NetworkAddress>.availableLanAddresses(): List<NetworkAddress> =
    filter { address -> !address.loopback && address.family == NetworkAddressFamily.IPV4 }
        .distinctBy { address -> address.interfaceId to address.address }
        .sortedWith(
            compareBy<NetworkAddress> { address -> address.interfaceId.virtualInterfacePriority() }
                .thenBy { address -> address.address.privateAddressPriority() }
                .thenBy(NetworkAddress::interfaceId)
                .thenBy(NetworkAddress::address),
        )

/** Selects the preferred address shared by companion discovery and manual Wi-Fi proxy setup. */
internal fun NetworkSnapshot.preferredLanAddress(): NetworkAddress? = addresses.availableLanAddresses().firstOrNull()

private fun String.virtualInterfacePriority(): Int {
    val normalized = lowercase()
    return if (VIRTUAL_INTERFACE_PREFIXES.any(normalized::startsWith)) 1 else 0
}

private fun String.privateAddressPriority(): Int {
    val octets = split('.').mapNotNull(String::toIntOrNull)
    if (octets.size != 4) return 1
    val local = octets[0] == 10 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 169 && octets[1] == 254)
    return if (local) 0 else 1
}

private val VIRTUAL_INTERFACE_PREFIXES: List<String> = listOf(
    "awdl",
    "bridge",
    "docker",
    "gif",
    "llw",
    "p2p",
    "stf",
    "tap",
    "tun",
    "utun",
    "vbox",
    "veth",
    "vmnet",
)
