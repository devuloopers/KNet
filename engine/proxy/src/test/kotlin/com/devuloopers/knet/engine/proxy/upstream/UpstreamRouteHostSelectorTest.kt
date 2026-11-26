package com.devuloopers.knet.engine.proxy.upstream

import kotlin.test.Test
import kotlin.test.assertEquals

class UpstreamRouteHostSelectorTest {
    @Test
    fun `classified tunnels add only a valid DNS name to an IPv6 literal route`() {
        val target = "2001:db8::10"
        assertEquals(UpstreamRouteHostSelection(target, "origin.example"),
            UpstreamRouteHostSelector.selectClassifiedTunnel(target, "Origin.Example"))
        for (name in listOf(null, "https://origin.example", "127.0.0.1")) {
            assertEquals(UpstreamRouteHostSelection(target, null), UpstreamRouteHostSelector.selectClassifiedTunnel(target, name))
        }
        assertEquals(UpstreamRouteHostSelection("192.0.2.10", null),
            UpstreamRouteHostSelector.selectClassifiedTunnel("192.0.2.10", "origin.example"))
    }

    @Test
    fun `tls dns name supplements an ipv6 CONNECT literal`() {
        val selection = UpstreamRouteHostSelector.select(
            connectHost = "2600:140f:6:184::ed2",
            tlsServerName = "stg-04astra.cnbc.com",
            isTls = true,
        )

        assertEquals("2600:140f:6:184::ed2", selection.primaryHost)
        assertEquals("stg-04astra.cnbc.com", selection.fallbackDnsHost)
    }

    @Test
    fun `tls dns name supplements an ipv4 CONNECT literal`() {
        val selection = UpstreamRouteHostSelector.select(
            connectHost = "23.41.120.10",
            tlsServerName = "stg-04astra.cnbc.com",
            isTls = true,
        )

        assertEquals("23.41.120.10", selection.primaryHost)
        assertEquals("stg-04astra.cnbc.com", selection.fallbackDnsHost)
    }

    @Test
    fun `hostname CONNECT keeps its original routing identity`() {
        val selection = UpstreamRouteHostSelector.select(
            connectHost = "origin.example",
            tlsServerName = "origin.example",
            isTls = true,
        )

        assertEquals("origin.example", selection.primaryHost)
        assertEquals(null, selection.fallbackDnsHost)
    }

    @Test
    fun `ip CONNECT without dns SNI remains an exact IP route`() {
        val selection = UpstreamRouteHostSelector.select(
            connectHost = "2001:db8::1",
            tlsServerName = "2001:db8::1",
            isTls = true,
        )

        assertEquals("2001:db8::1", selection.primaryHost)
        assertEquals(null, selection.fallbackDnsHost)
    }

    @Test
    fun `cleartext IP route is never rewritten from a host hint`() {
        val selection = UpstreamRouteHostSelector.select(
            connectHost = "192.0.2.10",
            tlsServerName = "origin.example",
            isTls = false,
        )

        assertEquals("192.0.2.10", selection.primaryHost)
        assertEquals(null, selection.fallbackDnsHost)
    }
}
