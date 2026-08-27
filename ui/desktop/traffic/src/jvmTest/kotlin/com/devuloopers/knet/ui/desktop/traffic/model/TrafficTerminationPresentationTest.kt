package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficTerminationCode
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficTerminationPresentationTest {
    @Test
    fun `lifecycle and timeout reasons have meaningful stable labels`() {
        assertEquals(
            "Interrupted",
            ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Lifecycle.PROCESS_INTERRUPTED)
                .toTrafficStatusLabel(),
        )
        assertEquals(
            "Proxy Stopped",
            ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Lifecycle.PROXY_STOPPED)
                .toTrafficStatusLabel(),
        )
        assertEquals(
            "Timed Out",
            ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.READ_TIMED_OUT)
                .toTrafficStatusLabel(),
        )
    }

    @Test
    fun `future failure reason keeps the generic terminal label`() {
        val reason = TrafficTerminationReason.Unknown(TrafficTerminationCode("future_failure"))

        assertEquals("Failed", ExchangeTerminalOutcome.Failed(reason).toTrafficStatusLabel())
    }
}
