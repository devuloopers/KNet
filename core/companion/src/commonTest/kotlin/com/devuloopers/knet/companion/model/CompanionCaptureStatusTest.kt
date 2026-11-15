package com.devuloopers.knet.companion.model

import kotlin.test.*

/** Version, size, identity-free payload and compatibility tests for capture readiness. */
class CompanionCaptureStatusTest {
    @Test fun `every readiness state has a bounded unambiguous round trip`() {
        CompanionDesktopCaptureState.entries.forEach { state ->
            val bytes = CompanionCaptureStatusCodec.encode(state)
            assertTrue(bytes.size <= 64)
            assertEquals(state, CompanionCaptureStatusCodec.decode(bytes))
        }
        listOf("", "knet-capture-v2\nCAPTURING\n", "knet-capture-v1\nACTIVE\n", "knet-capture-v1\nPAUSED\nextra", "x".repeat(65))
            .forEach { assertFails { CompanionCaptureStatusCodec.decode(it.encodeToByteArray()) } }
        assertFails { CompanionCaptureStatusCodec.decode(byteArrayOf(-1)) }
    }

    @Test fun `legacy control messages do not acquire fields rejected by old strict clients`() {
        val start = CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.START, "run"))
        assertFalse(start.contains("captureStatus"))
        val update = CompanionSessionMessage(CompanionSessionMessageKind.CAPTURE_STATUS, "run", revision = 2, captureStatus = CompanionDesktopCaptureState.CAPTURING)
        assertEquals(update, CompanionSessionProtocol.decode(CompanionSessionProtocol.encode(update)))
        assertFalse(CompanionSessionProtocol.allowsClient(update.kind))
        assertFails { CompanionSessionMessage(update.kind, "run", captureStatus = CompanionDesktopCaptureState.CAPTURING) }
        assertFails { CompanionSessionMessage(CompanionSessionMessageKind.HEARTBEAT, "run", captureStatus = CompanionDesktopCaptureState.PAUSED) }
    }
}
