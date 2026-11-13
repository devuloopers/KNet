package com.devuloopers.knet.application.device

import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.*
import kotlin.test.*

/** Source classification depends on authenticated ingress, never on the shape of a display value or IP address. */
class TrafficSourceTest {
    @Test fun `only paired ingress categories classify a registered device`() {
        for (kind in listOf(IngressKind.LanPairedDevice, IngressKind.CompanionDirect, IngressKind.CompanionRelay)) {
            assertEquals(TrafficSource.Device(RegisteredDeviceId("same-name")),
                TrafficSource.fromIngress(IngressContext(kind, ClientIdentity("same-name"))))
            assertEquals(TrafficSource.Other, TrafficSource.fromIngress(IngressContext(kind)))
        }
        for (kind in listOf(IngressKind.Local, IngressKind.WifiLanClient, IngressKind.AdbDevice, IngressKind.Custom("LAN_PAIRED_DEVICE"))) {
            assertEquals(TrafficSource.Other, TrafficSource.fromIngress(IngressContext(kind, ClientIdentity("same-name"))))
        }
        assertEquals(TrafficSource.Other, TrafficSource.fromIngress(null))
    }
    @Test fun `source preferences round trip without treating malformed data as a device`() {
        for (source in listOf(TrafficSource.All, TrafficSource.Other, TrafficSource.Device(RegisteredDeviceId("device-a")))) {
            assertEquals(source, TrafficSource.fromPreference(source.preferenceKey))
        }
        for (value in listOf(null, "", "device:", "device:   ", "unknown")) assertEquals(TrafficSource.Other, TrafficSource.fromPreference(value))
    }
}
