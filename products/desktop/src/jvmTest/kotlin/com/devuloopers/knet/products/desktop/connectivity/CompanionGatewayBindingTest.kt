package com.devuloopers.knet.products.desktop.connectivity

import com.devuloopers.knet.products.desktop.di.connectivity.COMPANION_LAN_BIND_HOST
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertTrue

class CompanionGatewayBindingTest {
    @Test
    fun `companion gateways bind every local interface`() {
        assertTrue(
            InetAddress.getByName(COMPANION_LAN_BIND_HOST).isAnyLocalAddress,
            "Companion gateways must be reachable from paired LAN devices, not only loopback.",
        )
    }
}
