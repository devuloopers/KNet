package com.devuloopers.knet.connectivity.desktop.network

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkInterfaceKind
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Injectable OS routing-table probe. Connecting a UDP socket selects a route without sending data. */
internal fun interface DesktopDefaultRouteAddressProvider {
    fun resolveIpv4Address(): String?
}

/** JVM implementation shared by Windows, Linux, and macOS desktop products. */
internal class JvmDesktopNetworkScanner(
    private val defaultRoute: DesktopDefaultRouteAddressProvider = JvmDefaultRouteAddressProvider,
) : DesktopNetworkScanner {
    override fun scan(): DesktopNetworkObservation {
        val routeAddress = defaultRoute.resolveIpv4Address()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { networkInterface -> networkInterface.safeBoolean(NetworkInterface::isUp) }
        val addresses = interfaces.flatMap { networkInterface ->
            val displayName = networkInterface.displayName?.takeIf(String::isNotBlank) ?: networkInterface.name
            val interfaceKind = classifyDesktopNetworkInterface(
                interfaceId = networkInterface.name,
                displayName = displayName,
                virtual = networkInterface.safeBoolean(NetworkInterface::isVirtual),
                pointToPoint = networkInterface.safeBoolean(NetworkInterface::isPointToPoint),
                loopback = networkInterface.safeBoolean(NetworkInterface::isLoopback),
            )
            val multicastSupported = networkInterface.safeBoolean(NetworkInterface::supportsMulticast)
            networkInterface.inetAddresses.toList().mapNotNull { address ->
                val family = when (address) {
                    is Inet4Address -> NetworkAddressFamily.IPV4
                    is Inet6Address -> NetworkAddressFamily.IPV6
                    else -> return@mapNotNull null
                }
                NetworkAddress(
                    interfaceId = networkInterface.name,
                    interfaceDisplayName = displayName,
                    address = address.hostAddress.substringBefore('%'),
                    family = family,
                    loopback = address.isLoopbackAddress,
                    interfaceKind = interfaceKind,
                    multicastSupported = multicastSupported,
                )
            }
        }.sortedWith(compareBy(NetworkAddress::interfaceId, NetworkAddress::family, NetworkAddress::address))
        val selection = DesktopLanAddressSelector.select(addresses, routeAddress)
        return DesktopNetworkObservation(
            addresses = addresses,
            defaultRouteAvailable = routeAddress != null,
            vpnActive = addresses.any { address -> address.interfaceKind == NetworkInterfaceKind.VPN },
            selection = selection,
        )
    }
}

private object JvmDefaultRouteAddressProvider : DesktopDefaultRouteAddressProvider {
    override fun resolveIpv4Address(): String? = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(DEFAULT_ROUTE_PROBE_ADDRESS), DEFAULT_ROUTE_PROBE_PORT)
            socket.localAddress
                ?.takeIf { address -> address is Inet4Address && !address.isAnyLocalAddress && !address.isLoopbackAddress }
                ?.hostAddress
        }
    }.getOrNull()

    private const val DEFAULT_ROUTE_PROBE_ADDRESS: String = "8.8.8.8"
    private const val DEFAULT_ROUTE_PROBE_PORT: Int = 53
}

internal fun classifyDesktopNetworkInterface(
    interfaceId: String,
    displayName: String,
    virtual: Boolean,
    pointToPoint: Boolean,
    loopback: Boolean,
): NetworkInterfaceKind {
    val identity = "$interfaceId $displayName".lowercase()
    return when {
        pointToPoint || VPN_MARKERS.any(identity::contains) -> NetworkInterfaceKind.VPN
        virtual || VIRTUAL_MARKERS.any(identity::contains) -> NetworkInterfaceKind.VIRTUAL
        loopback -> NetworkInterfaceKind.UNKNOWN
        else -> NetworkInterfaceKind.PHYSICAL
    }
}

private fun NetworkInterface.safeBoolean(property: NetworkInterface.() -> Boolean): Boolean =
    runCatching { property() }.getOrDefault(false)

private val VPN_MARKERS: Set<String> = setOf(
    "tailscale",
    "tap-windows",
    "utun",
    "vpn",
    "wireguard",
    "zerotier",
)

private val VIRTUAL_MARKERS: Set<String> = setOf(
    "awdl",
    "bridge",
    "docker",
    "gif",
    "host-only",
    "hyper-v",
    "llw",
    "parallels",
    "p2p",
    "stf",
    "vbox",
    "vethernet",
    "veth",
    "virtualbox",
    "vmnet",
    "vmware",
    "wsl",
)
