package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrafficTerminationTest {
    @Test
    fun `known reason round trips through stable code`() {
        val reason = TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED

        assertEquals(reason, TrafficTerminationReason.fromCode(reason.code.value))
    }

    @Test
    fun `protocol reason remains namespaced and forward compatible`() {
        val reason = TrafficTerminationReason.fromCode("grpc_future_failure")

        assertIs<TrafficTerminationReason.Protocol>(reason)
        assertEquals(MessageProtocolId.GRPC, reason.protocol)
        assertEquals("grpc_future_failure", reason.code.value)
    }

    @Test
    fun `unknown future reason is preserved`() {
        val reason = TrafficTerminationReason.fromCode("future-runtime-reason")

        assertIs<TrafficTerminationReason.Unknown>(reason)
        assertEquals("future-runtime-reason", reason.code.value)
    }

    @Test
    fun `legacy terminal rows receive typed unspecified reasons`() {
        val failed = ExchangeTerminalOutcome.fromPersisted(ExchangeState.FAILED, null)
        val cancelled = ExchangeTerminalOutcome.fromPersisted(ExchangeState.CANCELLED, null)

        assertEquals(TrafficTerminationReason.Unspecified.FAILURE, failed?.reason)
        assertEquals(TrafficTerminationReason.Unspecified.CANCELLATION, cancelled?.reason)
        assertNull(ExchangeTerminalOutcome.fromPersisted(ExchangeState.WAITING_FOR_RESPONSE, null))
    }

    @Test
    fun `unsafe termination code is rejected at boundary`() {
        assertFailsWith<IllegalArgumentException> {
            TrafficTerminationCode("UPSTREAM CONNECT FAILED")
        }
    }
}
