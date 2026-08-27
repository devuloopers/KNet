package com.devuloopers.knet.companion.data.device

import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionDeviceDisplayNameFormatterTest {
    @Test
    fun platformLabelIsNormalizedAndIncludesAStableIdentitySuffix() {
        val result = formatCompanionDeviceDisplayName(
            platformLabel = "  Samsung   Galaxy S24  ",
            fallbackLabel = "Android device",
            deviceId = RegisteredDeviceId("device-a7f2"),
        )

        assertEquals("Samsung Galaxy S24 · A7F2", result.value)
    }

    @Test
    fun blankPlatformLabelUsesThePlatformFallback() {
        val result = formatCompanionDeviceDisplayName(
            platformLabel = " \n ",
            fallbackLabel = "iOS device",
            deviceId = RegisteredDeviceId("device-31bc"),
        )

        assertEquals("iOS device · 31BC", result.value)
    }

    @Test
    fun longPlatformLabelRemainsAValidStronglyTypedNameWithoutLosingItsSuffix() {
        val result = formatCompanionDeviceDisplayName(
            platformLabel = "Android ".repeat(40),
            fallbackLabel = "Android device",
            deviceId = RegisteredDeviceId("device-a7f2"),
        )

        assertTrue(result.value.length <= CompanionDeviceDisplayName.MAXIMUM_LENGTH)
        assertTrue(result.value.endsWith(" · A7F2"))
    }
}
