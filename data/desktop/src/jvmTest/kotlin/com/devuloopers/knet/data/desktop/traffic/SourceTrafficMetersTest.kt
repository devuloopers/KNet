package com.devuloopers.knet.data.desktop.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.*
import kotlin.test.*

/** Byte counters have no dependence on captured history, hostnames, IP reuse or policy UI selection. */
class SourceTrafficMetersTest {
    @Test fun `sources sharing addresses stay isolated and lifecycle close is idempotent`() {
        var now = 0L
        val meters = SourceTrafficMeters({ ++now })
        val first = meters.open(ingress("a"))
        val second = meters.open(ingress("b"))
        val other = meters.open(IngressContext(IngressKind.Local))
        first.uploaded(125_000); first.downloaded(250_000)
        second.downloaded(1_000); other.uploaded(5)
        val a = meters.snapshot(TrafficSource.Device(RegisteredDeviceId("a")))
        assertEquals(125_000, a.uploadedBytes)
        assertEquals(250_000, a.downloadedBytes)
        assertEquals(1, a.activeConnections)
        assertEquals(1_000, meters.snapshot(TrafficSource.Device(RegisteredDeviceId("b"))).downloadedBytes)
        assertEquals(5, meters.snapshot(TrafficSource.Other).uploadedBytes)
        assertEquals(251_000, meters.snapshot(TrafficSource.All).downloadedBytes)
        first.close(); first.close(); first.uploaded(500)
        assertEquals(0, meters.snapshot(TrafficSource.Device(RegisteredDeviceId("a"))).activeConnections)
        assertEquals(125_000, meters.snapshot(TrafficSource.Device(RegisteredDeviceId("a"))).uploadedBytes)
        assertFailsWith<IllegalArgumentException> { second.downloaded(-1) }
        second.close(); other.close()
    }

    @Test fun `bounded capacity retains live peers and reuses inactive slots with a fresh baseline`() {
        val meters = SourceTrafficMeters({ 0 }, maximumSources = 1)
        val a = meters.open(ingress("a")); a.downloaded(42)
        val dropped = meters.open(ingress("b")); dropped.downloaded(99); dropped.close()
        assertEquals(42, meters.snapshot(TrafficSource.All).downloadedBytes)
        a.close()
        val b = meters.open(ingress("b")); b.downloaded(7)
        assertEquals(0, meters.snapshot(TrafficSource.Device(RegisteredDeviceId("a"))).downloadedBytes)
        assertEquals(7, meters.snapshot(TrafficSource.All).downloadedBytes)
        b.close()
    }
    private fun ingress(id: String) = IngressContext(IngressKind.CompanionRelay, clientIdentity = ClientIdentity(id))
}
