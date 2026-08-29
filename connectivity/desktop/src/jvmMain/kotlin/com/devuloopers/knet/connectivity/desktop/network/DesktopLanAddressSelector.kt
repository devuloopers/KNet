package com.devuloopers.knet.connectivity.desktop.network

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkInterfaceKind

/** Typed explanation retained for diagnostics and deterministic selection tests. */
public enum class DesktopLanAddressSelectionReason {
    DEFAULT_ROUTE,
    PHYSICAL_INTERFACE,
    UNKNOWN_INTERFACE,
    VIRTUAL_FALLBACK,
    VPN_FALLBACK,
}

/** One authoritative desktop LAN selection and the reason it won. */
public data class DesktopLanAddressSelection(
    public val address: NetworkAddress,
    public val reason: DesktopLanAddressSelectionReason,
)

/**
 * Selects a LAN-reachable address without treating any private IPv4 range as inherently physical.
 *
 * A physical default route wins. If the OS route points at a VPN or virtual adapter, a physical
 * multicast-capable adapter remains preferred so local companions are not advertised into an
 * isolated overlay network.
 */
internal object DesktopLanAddressSelector {
    fun select(
        addresses: List<NetworkAddress>,
        defaultRouteAddress: String?,
    ): DesktopLanAddressSelection? {
        val candidates = addresses.availableLanAddresses()
        val selected = candidates.minWithOrNull(
            compareBy<NetworkAddress> { candidate -> candidate.selectionPriority(defaultRouteAddress) }
                .thenBy { candidate -> if (candidate.address.isLinkLocalIpv4()) 1 else 0 }
                .thenBy(NetworkAddress::interfaceId)
                .thenBy(NetworkAddress::address),
        ) ?: return null
        return DesktopLanAddressSelection(
            address = selected,
            reason = selected.selectionReason(defaultRouteAddress),
        )
    }
}

private fun NetworkAddress.selectionPriority(defaultRouteAddress: String?): Int = when {
    address == defaultRouteAddress && interfaceKind == NetworkInterfaceKind.PHYSICAL -> 0
    interfaceKind == NetworkInterfaceKind.PHYSICAL && multicastSupported -> 1
    address == defaultRouteAddress && interfaceKind == NetworkInterfaceKind.UNKNOWN -> 2
    interfaceKind == NetworkInterfaceKind.UNKNOWN && multicastSupported -> 3
    interfaceKind == NetworkInterfaceKind.PHYSICAL -> 4
    interfaceKind == NetworkInterfaceKind.UNKNOWN -> 5
    address == defaultRouteAddress && interfaceKind == NetworkInterfaceKind.VIRTUAL -> 6
    interfaceKind == NetworkInterfaceKind.VIRTUAL -> 7
    else -> 8
}

private fun NetworkAddress.selectionReason(defaultRouteAddress: String?): DesktopLanAddressSelectionReason = when {
    address == defaultRouteAddress && interfaceKind != NetworkInterfaceKind.VPN ->
        DesktopLanAddressSelectionReason.DEFAULT_ROUTE
    interfaceKind == NetworkInterfaceKind.PHYSICAL -> DesktopLanAddressSelectionReason.PHYSICAL_INTERFACE
    interfaceKind == NetworkInterfaceKind.UNKNOWN -> DesktopLanAddressSelectionReason.UNKNOWN_INTERFACE
    interfaceKind == NetworkInterfaceKind.VIRTUAL -> DesktopLanAddressSelectionReason.VIRTUAL_FALLBACK
    interfaceKind == NetworkInterfaceKind.VPN -> DesktopLanAddressSelectionReason.VPN_FALLBACK
    else -> error("Every network interface kind must have a selection reason.")
}

private fun String.isLinkLocalIpv4(): Boolean = startsWith("169.254.")
