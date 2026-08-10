package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying the enum values and transitions of [TrustInstallationState].
 */
public class TrustInstallationStateTest {

    /**
     * Verifies that the [TrustInstallationState] enum contains all expected progression states.
     *
     * Design Intent: UI status indicators depend on these state definitions during CA trust store installation.
     */
    @Test
    public fun testTrustInstallationStateEnumValues() {
        val states = TrustInstallationState.entries
        assertTrue(states.contains(TrustInstallationState.CHECKING))
        assertTrue(states.contains(TrustInstallationState.IDLE))
        assertTrue(states.contains(TrustInstallationState.INSTALLING))
        assertTrue(states.contains(TrustInstallationState.INSTALLED))
        assertTrue(states.contains(TrustInstallationState.FAILED))
        assertEquals(5, states.size)
    }
}
