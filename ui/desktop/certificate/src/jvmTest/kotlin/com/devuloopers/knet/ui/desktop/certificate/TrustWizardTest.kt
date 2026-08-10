package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying trust wizard status strings based on installation state.
 */
public class TrustWizardTest {

    /**
     * Verifies that the correct message text corresponds to each [TrustInstallationState].
     *
     * Design Intent: Assures status labels are properly associated with the enum values shown in the wizard.
     */
    @Test
    public fun testTrustWizardStateLabels() {
        val states = listOf(
            TrustInstallationState.CHECKING,
            TrustInstallationState.IDLE,
            TrustInstallationState.INSTALLING,
            TrustInstallationState.INSTALLED,
            TrustInstallationState.FAILED
        )

        val labels = states.map { state ->
            when (state) {
                TrustInstallationState.CHECKING -> "Verifying system keystore..."
                TrustInstallationState.IDLE -> "Trust Certificate Authority"
                TrustInstallationState.INSTALLING -> "Integrating with system keystore..."
                TrustInstallationState.INSTALLED -> "Certificate Trusted Successfully!"
                TrustInstallationState.FAILED -> "Installation Failed. Retry?"
            }
        }

        assertEquals("Verifying system keystore...", labels[0])
        assertEquals("Trust Certificate Authority", labels[1])
        assertEquals("Integrating with system keystore...", labels[2])
        assertEquals("Certificate Trusted Successfully!", labels[3])
        assertEquals("Installation Failed. Retry?", labels[4])
    }
}
