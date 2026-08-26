package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ControlContractsTest {
    @Test
    fun authorizationRejectsCredentialHeaderInjection() {
        assertFailsWith<IllegalArgumentException> {
            CompanionControlAuthorization(
                RegisteredDeviceId("device-1"),
                "valid-prefix\r\nInjected: yes",
            )
        }
    }

    @Test
    fun authorizationRejectsUnsafeDeviceIdentity() {
        assertFailsWith<IllegalArgumentException> {
            CompanionControlAuthorization(
                RegisteredDeviceId("device:second-token"),
                "valid-credential-value",
            )
        }
    }
}
