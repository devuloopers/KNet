package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BreakpointCoordinatorTest {
    @Test
    fun `compiled rule publishes bounded pending record and resolves once`() = runTest {
        val coordinator = BreakpointCoordinator(
            BreakpointLimits(maxPendingConnections = 1, maxPendingBytes = 16L, maxEditableBodyBytes = 8),
        )
        coordinator.replaceRules(
            listOf(
                BreakpointRule(
                    id = "request-rule",
                    phase = BreakpointPhase.REQUEST,
                    urlPattern = "https://api.example.com/*",
                    method = HttpMethod.POST,
                ),
            ),
        )
        val pendingDecision = async { coordinator.intercept(candidate("exchange-1", byteArrayOf(1, 2, 3))) }
        runCurrent()

        val pending = coordinator.pendingBreakpoints.value.single()
        assertEquals("request-rule", pending.ruleId)
        assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged))
        assertFalse(coordinator.resolve(pending.id, BreakpointDecision.Drop))
        assertSame(BreakpointDecision.ContinueUnchanged, pendingDecision.await())
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
    }

    @Test
    fun `oversized body and exhausted pending budget bypass without retaining state`() = runTest {
        val coordinator = BreakpointCoordinator(
            BreakpointLimits(maxPendingConnections = 1, maxPendingBytes = 4L, maxEditableBodyBytes = 4),
        )
        coordinator.replaceRules(
            listOf(BreakpointRule(id = "all", phase = BreakpointPhase.BOTH, urlPattern = "*")),
        )

        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(candidate("oversized", ByteArray(5))),
        )
        val first = async { coordinator.intercept(candidate("first", ByteArray(4))) }
        runCurrent()
        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(candidate("second", byteArrayOf(1))),
        )
        coordinator.clear()
        assertSame(BreakpointDecision.Drop, first.await())
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
    }

    @Test
    fun `missing protocol extension fails closed instead of becoming an HTTP rule`() = runTest {
        val coordinator = BreakpointCoordinator()
        coordinator.replaceRules(
            listOf(
                BreakpointRule(
                    id = "unavailable-protocol",
                    urlPattern = "*",
                    protocolCriteria = ProtocolMatchCriteria(
                        protocolId = BreakpointProtocolId("future-format"),
                        encodedPayload = "{\"version\":1}",
                    ),
                ),
            ),
        )

        assertFalse(coordinator.requirements.value.hasRequestRules)
        assertFalse(coordinator.requirements.value.hasResponseRules)
        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(candidate("unavailable", byteArrayOf(1))),
        )
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
    }

    @Test
    fun `capture pause continues pending decisions without changing pipeline requirements`() = runTest {
        val coordinator = BreakpointCoordinator()
        coordinator.replaceRules(
            listOf(BreakpointRule(id = "pause-rule", phase = BreakpointPhase.REQUEST, urlPattern = "*")),
        )
        val requirementsBeforePause = coordinator.requirements.value
        val decision = async { coordinator.intercept(candidate("pause-candidate", byteArrayOf(1))) }
        runCurrent()
        assertEquals(1, coordinator.pendingBreakpoints.value.size)

        coordinator.setCaptureAvailable(false)

        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        assertEquals(requirementsBeforePause, coordinator.requirements.value)
        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(candidate("paused-candidate", byteArrayOf(1))),
        )
    }

    private fun candidate(id: String, body: ByteArray) = BreakpointCandidate(
        exchangeId = ExchangeId(id),
        phase = BreakpointPhase.REQUEST,
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.fromToken("POST"),
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.com"),
                    pathAndQuery = "/v1/items",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
        ),
        requestBody = BreakpointBody(body),
        requestObservedBodyBytes = body.size.toLong(),
        startedAtEpochMillis = 1L,
    )
}
