package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.OpaqueSecurityProtocol
import com.devuloopers.knet.traffic.model.OpaqueTransportProtocol
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficTypeFilterTest {
    @Test
    fun `semantic filters keep TLS unknown TCP and QUIC distinct`() {
        val tls = row(OpaqueTransportProtocol.TCP, OpaqueSecurityProtocol.TLS, policyRuleId = "play")
        val tcp = row(OpaqueTransportProtocol.TCP, OpaqueSecurityProtocol.UNKNOWN)
        val quic = row(OpaqueTransportProtocol.UDP, OpaqueSecurityProtocol.QUIC)

        assertTrue(TrafficTypeFilter.TLS_TUNNEL.matches(tls))
        assertTrue(TrafficTypeFilter.PROTECTED.matches(tls))
        assertFalse(TrafficTypeFilter.OPAQUE_TCP.matches(tls))
        assertTrue(TrafficTypeFilter.OPAQUE_TCP.matches(tcp))
        assertTrue(TrafficTypeFilter.UDP_QUIC.matches(quic))
    }

    @Test
    fun `failed filter uses typed terminal outcome`() {
        val failed = row(
            OpaqueTransportProtocol.TCP,
            OpaqueSecurityProtocol.TLS,
            terminalOutcome = ExchangeTerminalOutcome.Failed(
                TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
            ),
        )

        assertTrue(TrafficTypeFilter.FAILED.matches(failed))
        assertFalse(TrafficTypeFilter.DECRYPTED.matches(failed))
    }

    private fun row(
        transport: OpaqueTransportProtocol,
        security: OpaqueSecurityProtocol,
        policyRuleId: String? = null,
        terminalOutcome: ExchangeTerminalOutcome? = ExchangeTerminalOutcome.Completed,
    ): TrafficRowUiState = TrafficRowUiState(
        sequenceNumber = 1L,
        transactionId = "flow",
        method = "TLS tunnel",
        rowKind = TrafficRowKind.OPAQUE_FLOW,
        policyRuleId = policyRuleId,
        opaqueSecurity = security,
        opaqueTransport = transport,
        scheme = HttpScheme.fromToken("https"),
        host = "example.test:443",
        path = "Encrypted payload unavailable",
        status = 0,
        statusText = "Completed",
        protocol = ApplicationProtocol.fromToken("TLS tunnel"),
        timestamp = 1L,
        formattedTimestamp = "00:00:00",
        formattedTime = "1 ms",
        transferredBytes = 1L,
        responseBytes = 1L,
        formattedSize = "1 B",
        dateGroup = "1970-01-01",
        contentType = null,
        timings = ExchangeTimings(totalMillis = 1L),
        terminalOutcome = terminalOutcome,
    )
}
