package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Tests portable monotonic capture-event validation. */
class CaptureEventsTest {

    /** Verifies only terminal lifecycle states can be published as exchange termination. */
    @Test
    fun `exchange termination rejects nonterminal state`() {
        assertFailsWith<IllegalArgumentException> {
            CaptureEvent.ExchangeTerminated(
                sessionId = CaptureSessionId("session"),
                connectionId = ConnectionId("connection"),
                sequence = 1L,
                occurredAtEpochMillis = 1L,
                exchangeId = ExchangeId("exchange"),
                exchangeVersion = 1L,
                state = ExchangeState.RESPONSE_HEADERS,
            )
        }
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
