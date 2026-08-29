package com.devuloopers.knet.data.desktop.network.repository

import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkRepositoryImplTest {
    @Test
    fun `repository exposes the monitor selected address without running another resolver`() = runTest {
        val preferred = NetworkAddress("wlan0", "192.168.1.25", NetworkAddressFamily.IPV4, loopback = false)
        val virtual = NetworkAddress("eth0", "172.28.224.1", NetworkAddressFamily.IPV4, loopback = false)
        val snapshots = MutableStateFlow(
            snapshot(addresses = listOf(virtual, preferred), preferred = preferred),
        )
        val repository = NetworkRepositoryImpl(snapshots)

        assertEquals(preferred.address, repository.getLocalIp())
        assertEquals(preferred.address, repository.observeLocalIp().first())
    }

    @Test
    fun `repository exposes loopback while no LAN address is selected`() = runTest {
        val repository = NetworkRepositoryImpl(MutableStateFlow(snapshot(emptyList(), preferred = null)))

        assertEquals("127.0.0.1", repository.getLocalIp())
    }

    private fun snapshot(
        addresses: List<NetworkAddress>,
        preferred: NetworkAddress?,
    ): NetworkSnapshot = NetworkSnapshot(
        version = 1L,
        addresses = addresses,
        defaultRouteAvailable = preferred != null,
        vpnActive = false,
        observedAtEpochMillis = 1L,
        preferredLanAddress = preferred,
    )
}
