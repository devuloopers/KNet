package com.devuloopers.knet.companion.android.scanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleDeliveryQrGateTest {
    @Test
    fun onlyOneFrameMayBeAnalyzedAtATime() {
        val gate = SingleDeliveryQrGate()

        assertTrue(gate.tryBeginAnalysis())
        assertFalse(gate.tryBeginAnalysis())
        gate.finishAnalysis()
        assertTrue(gate.tryBeginAnalysis())
    }

    @Test
    fun deliveredPayloadPermanentlyClosesTheScannerSession() {
        val gate = SingleDeliveryQrGate()

        assertTrue(gate.tryBeginAnalysis())
        assertTrue(gate.tryDeliver())
        assertFalse(gate.tryDeliver())
        gate.finishAnalysis()
        assertFalse(gate.tryBeginAnalysis())
    }
}
