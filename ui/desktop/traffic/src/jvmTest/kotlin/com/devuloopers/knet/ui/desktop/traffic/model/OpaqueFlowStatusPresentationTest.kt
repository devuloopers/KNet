package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.ui.desktop.traffic.inspector.opaqueDiagnostic
import com.devuloopers.knet.ui.desktop.traffic.protection.ProtectionFixture
import kotlin.test.*

/** Encrypted connection lifetime must not be presented as HTTP or application completion. */
class OpaqueFlowStatusPresentationTest {
    @Test fun `open and closed tunnels have consistent transport labels without HTTP success`() {
        val open = ProtectionFixture().captured.copy(state = OpaqueFlowState.ACTIVE, completedAtEpochMillis = null,
            terminalOutcome = null, policyAction = OpaqueFlowPolicyAction.TUNNEL, inspectionFailureReason = null,
            uploadedBytes = 120, downloadedBytes = 450)
        val active = open.toTrafficRowUiState(1)
        assertEquals("Active", active.statusText); assertEquals("Active", active.displayStatusLabel)
        assertEquals(0, active.status); assertNull(active.terminalOutcome)
        assertEquals(570L, active.transferredBytes)
        val closed = open.copy(state = OpaqueFlowState.COMPLETED, completedAtEpochMillis = 10,
            terminalOutcome = ExchangeTerminalOutcome.Completed).toTrafficRowUiState(1)
        assertEquals("Closed", closed.statusText); assertEquals("Closed", closed.displayStatusLabel)
        assertEquals(0, closed.status)
    }

    @Test fun `rejection cancellation and ordinary HTTP statuses keep their own meanings`() {
        val rejected = ProtectionFixture().captured.toTrafficRowUiState(1)
        assertEquals("TLS Rejected", rejected.displayStatusLabel)
        val cancelled = ProtectionFixture().captured.copy(state = OpaqueFlowState.CANCELLED,
            terminalOutcome = ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED))
            .toTrafficRowUiState(1)
        assertEquals("Cancelled", cancelled.displayStatusLabel)
        val http = rejected.copy(rowKind = TrafficRowKind.HTTP_EXCHANGE, status = 200, statusText = "OK",
            terminalOutcome = null, formattedTime = "-")
        assertEquals("200", http.displayStatusLabel)
        assertEquals("In Progress", http.copy(status = 0).displayStatusLabel)
    }

    @Test fun `typed tunnel timeouts explain their deadline without claiming application failure`() {
        for ((reason, title) in listOf(
            TrafficTerminationReason.Transport.READ_TIMED_OUT to "Tunnel inactivity timeout",
            TrafficTerminationReason.Transport.WRITE_TIMED_OUT to "Tunnel write timed out",
        )) {
            val row = failedTunnel(reason)
            assertEquals("Timed Out", row.displayStatusLabel)
            assertEquals(570L, row.transferredBytes)
            assertEquals(0, row.status)
            val diagnostic = opaqueDiagnostic(row)
            assertEquals(title, diagnostic.title)
            assertTrue(diagnostic.failure)
            assertTrue(diagnostic.explanation.contains("encrypted"))
        }
    }

    @Test fun `historical generic IO failure stays a failure without guessing an inactivity timeout`() {
        val row = failedTunnel(TrafficTerminationReason.Transport.DUPLEX_IO_FAILED)
        assertEquals("Failed", row.displayStatusLabel)
        val diagnostic = opaqueDiagnostic(row)
        assertEquals("Tunnel connection failed", diagnostic.title)
        assertTrue(diagnostic.failure)
        assertFalse(diagnostic.explanation.contains("inactivity"))
    }

    private fun failedTunnel(reason: TrafficTerminationReason) = ProtectionFixture().captured.copy(
        state = OpaqueFlowState.FAILED, completedAtEpochMillis = 10,
        terminalOutcome = ExchangeTerminalOutcome.Failed(reason), policyAction = OpaqueFlowPolicyAction.TUNNEL,
        inspectionFailureReason = null, uploadedBytes = 120, downloadedBytes = 450,
    ).toTrafficRowUiState(1)
}
