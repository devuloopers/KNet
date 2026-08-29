package com.devuloopers.knet.connectivity.desktop.network

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkInterfaceKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLanAddressSelectorTest {
    @Test
    fun `Windows virtual adapter never outranks the routed physical interface`() {
        val virtual = address("eth0", "172.28.224.1", NetworkInterfaceKind.VIRTUAL)
        val wifi = address("wlan0", "192.168.1.25", NetworkInterfaceKind.PHYSICAL)

        val selection = DesktopLanAddressSelector.select(listOf(virtual, wifi), wifi.address)

        assertEquals(wifi, selection?.address)
        assertEquals(DesktopLanAddressSelectionReason.DEFAULT_ROUTE, selection?.reason)
    }

    @Test
    fun `physical LAN wins when the OS default route belongs to a VPN`() {
        val vpn = address("tun0", "100.64.0.2", NetworkInterfaceKind.VPN)
        val ethernet = address("eth0", "10.0.0.12", NetworkInterfaceKind.PHYSICAL)

        val selection = DesktopLanAddressSelector.select(listOf(vpn, ethernet), vpn.address)

        assertEquals(ethernet, selection?.address)
        assertEquals(DesktopLanAddressSelectionReason.PHYSICAL_INTERFACE, selection?.reason)
    }

    @Test
    fun `legitimate physical 172 private network remains selectable`() {
        val ethernet = address("eth0", "172.20.4.8", NetworkInterfaceKind.PHYSICAL)
        val virtual = address("vEthernet", "192.168.56.1", NetworkInterfaceKind.VIRTUAL)

        val selection = DesktopLanAddressSelector.select(listOf(virtual, ethernet), ethernet.address)

        assertEquals(ethernet, selection?.address)
        assertEquals(DesktopLanAddressSelectionReason.DEFAULT_ROUTE, selection?.reason)
    }

    @Test
    fun `multicast physical interface wins without a known default route`() {
        val disconnectedEthernet = address(
            id = "eth0",
            value = "192.168.2.2",
            kind = NetworkInterfaceKind.PHYSICAL,
            multicast = false,
        )
        val wifi = address("wlan0", "192.168.1.7", NetworkInterfaceKind.PHYSICAL)

        val selection = DesktopLanAddressSelector.select(listOf(disconnectedEthernet, wifi), null)

        assertEquals(wifi, selection?.address)
        assertEquals(DesktopLanAddressSelectionReason.PHYSICAL_INTERFACE, selection?.reason)
    }

    @Test
    fun `Windows Hyper-V and WSL display names are classified as virtual`() {
        assertEquals(
            NetworkInterfaceKind.VIRTUAL,
            classifyDesktopNetworkInterface(
                interfaceId = "eth3",
                displayName = "Hyper-V Virtual Ethernet Adapter",
                virtual = false,
                pointToPoint = false,
                loopback = false,
            ),
        )
        assertEquals(
            NetworkInterfaceKind.VIRTUAL,
            classifyDesktopNetworkInterface(
                interfaceId = "eth4",
                displayName = "vEthernet (WSL)",
                virtual = false,
                pointToPoint = false,
                loopback = false,
            ),
        )
    }

    private fun address(
        id: String,
        value: String,
        kind: NetworkInterfaceKind,
        multicast: Boolean = true,
    ): NetworkAddress = NetworkAddress(
        interfaceId = id,
        interfaceDisplayName = id,
        address = value,
        family = NetworkAddressFamily.IPV4,
        loopback = false,
        interfaceKind = kind,
        multicastSupported = multicast,
    )
}
