package com.devuloopers.knet.ui.desktop.traffic.table

import com.devuloopers.knet.ui.desktop.traffic.model.nextSeenCheckpoint
import com.devuloopers.knet.ui.desktop.traffic.model.follows
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficAutoScrollPolicyTest {
    @Test
    fun `late visible opaque admission advances the snapshot without moving last seen backwards`() {
        val http = com.devuloopers.knet.application.contract.traffic.TrafficReadPoint(
            com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Exchange(com.devuloopers.knet.traffic.id.ExchangeId("http")),
            2_000, com.devuloopers.knet.application.contract.traffic.TrafficCaptureSequence(2))
        val opaque = com.devuloopers.knet.application.contract.traffic.TrafficReadPoint(
            com.devuloopers.knet.application.contract.traffic.TrafficRecordId.Opaque(com.devuloopers.knet.traffic.id.OpaqueFlowId("late-tls")),
            1_500, com.devuloopers.knet.application.contract.traffic.TrafficCaptureSequence(1))
        val old = com.devuloopers.knet.application.contract.traffic.TrafficArrivalCheckpoint(2, 0)
        val current = old.copy(opaqueFlows = 1)
        val reading = com.devuloopers.knet.ui.desktop.traffic.model.TrafficReadingState(
            acknowledged = com.devuloopers.knet.application.contract.traffic.TrafficArrivalCheckpoint(1, 0), observed = current,
            seenThrough = com.devuloopers.knet.application.contract.traffic.TrafficSeenCheckpoint(http, old))
        kotlin.test.assertNull(reading.nextSeenCheckpoint(listOf(http)), "An unseen late admission must remain unread")
        val updated = kotlin.test.assertNotNull(reading.nextSeenCheckpoint(listOf(http, opaque)))
        kotlin.test.assertEquals(http, updated.position)
        kotlin.test.assertEquals(current, updated.observedAt)
        kotlin.test.assertNull(reading.copy(seenThrough = updated).nextSeenCheckpoint(listOf(opaque)))
    }

    @Test
    fun `smart requires exact top while manual is controlled only by its switch`() {
        val settings = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings()
        val reading = com.devuloopers.knet.ui.desktop.traffic.model.TrafficReadingState()
        assertTrue(com.devuloopers.knet.ui.desktop.traffic.model.isTrafficAtTop(0, 0))
        assertFalse(com.devuloopers.knet.ui.desktop.traffic.model.isTrafficAtTop(0, 1))
        assertFalse(com.devuloopers.knet.ui.desktop.traffic.model.isTrafficAtTop(1, 0))
        assertTrue(settings.follows(reading))
        assertFalse(settings.follows(reading.copy(atTop = false)))
        val manual = settings.copy(mode = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.MANUAL)
        assertTrue(manual.follows(reading.copy(atTop = false)))
        assertFalse(manual.copy(manualEnabled = false).follows(reading))
    }

    @Test
    fun `loading older pages does not request a jump to the top`() {
        assertFalse(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 625L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
        assertFalse(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 425L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
    }

    @Test
    fun `new capture or explicit enablement requests the newest row`() {
        assertTrue(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 626L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
        assertTrue(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 625L,
                wasEnabled = false,
                isEnabled = true,
            ),
        )
    }
}
