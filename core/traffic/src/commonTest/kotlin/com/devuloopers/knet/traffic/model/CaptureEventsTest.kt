package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests portable monotonic capture-event validation. */
class CaptureEventsTest {

    /** Verifies the event derives its terminal state from an unambiguous outcome. */
    @Test
    fun `exchange termination derives state from outcome`() {
        val event = CaptureEvent.ExchangeTerminated(
            sessionId = CaptureSessionId("session"),
            connectionId = ConnectionId("connection"),
            sequence = 1L,
            occurredAtEpochMillis = 1L,
            exchangeId = ExchangeId("exchange"),
            exchangeVersion = 1L,
            outcome = ExchangeTerminalOutcome.Cancelled(
                TrafficTerminationReason.Lifecycle.PROXY_STOPPED,
            ),
        )

        assertEquals(ExchangeState.CANCELLED, event.state)
        assertEquals(TrafficTerminationReason.Lifecycle.PROXY_STOPPED, event.outcome.reason)
    }

    /** Verifies a capture gap must describe actual metadata or body loss. */
    @Test
    fun `capture gap rejects zero loss`() {
        assertFailsWith<IllegalArgumentException> {
            CaptureEvent.GapObserved(
                sessionId = CaptureSessionId("session"),
                connectionId = ConnectionId("connection"),
                sequence = 1L,
                occurredAtEpochMillis = 1L,
                droppedEvents = 0L,
                droppedBodyBytes = 0L,
                reasonCode = "saturated",
            )
        }
    }
}
