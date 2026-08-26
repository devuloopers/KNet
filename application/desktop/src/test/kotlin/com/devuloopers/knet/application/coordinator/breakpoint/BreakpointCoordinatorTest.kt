package com.devuloopers.knet.application.coordinator.breakpoint

import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
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

    @Test
    fun `disabling interception immediately continues every pending exchange`() = runTest {
        val coordinator = BreakpointCoordinator().also { value ->
            value.replaceRules(
                listOf(BreakpointRule(id = "global-toggle", phase = BreakpointPhase.REQUEST)),
            )
        }
        val first = async { coordinator.intercept(candidate("first-disabled", byteArrayOf(1))) }
        val second = async { coordinator.intercept(candidate("second-disabled", byteArrayOf(2))) }
        runCurrent()
        assertEquals(2, coordinator.pendingBreakpoints.value.size)

        coordinator.setEnabled(false)

        assertSame(BreakpointDecision.ContinueUnchanged, first.await())
        assertSame(BreakpointDecision.ContinueUnchanged, second.await())
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        assertFalse(coordinator.requirements.value.hasRequestRules)
    }

    @Test
    fun `phase-specific decision rejects a request edit for a response pause`() = runTest {
        val coordinator = BreakpointCoordinator().also { value ->
            value.replaceRules(
                listOf(BreakpointRule(id = "response", phase = BreakpointPhase.RESPONSE, urlPattern = "*")),
            )
        }
        val responseCandidate = candidate("response-exchange", byteArrayOf()).copy(
            phase = BreakpointPhase.RESPONSE,
            response = HttpResponseSnapshot(
                ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(200),
                    reasonPhrase = "OK",
                    headers = emptyList(),
                ),
            ),
        )
        val decision = async { coordinator.intercept(responseCandidate) }
        runCurrent()
        val pending = coordinator.pendingBreakpoints.value.single()

        assertFalse(
            coordinator.resolve(
                pending.id,
                BreakpointDecision.ResumeRequest(BreakpointRequestEdit(responseCandidate.request)),
            ),
        )
        val responseEdit = BreakpointResponseEdit(requireNotNull(responseCandidate.response))
        assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ResumeResponse(responseEdit)))
        assertEquals(BreakpointDecision.ResumeResponse(responseEdit), decision.await())
    }

    @Test
    fun `pending budget includes the retained transport message`() = runTest {
        val coordinator = BreakpointCoordinator(
            BreakpointLimits(maxPendingBytes = 4L, maxEditableBodyBytes = 4),
        ).also { value ->
            value.replaceRules(listOf(BreakpointRule(id = "all", phase = BreakpointPhase.REQUEST)))
        }

        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(
                candidate("double-owned", byteArrayOf(1, 2)).copy(retainedTransportBytes = 3L),
            ),
        )
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
    }

    @Test
    fun `lower priority rule wins with id as deterministic tie breaker`() = runTest {
        val coordinator = BreakpointCoordinator().also { value ->
            value.replaceRules(
                listOf(
                    BreakpointRule(id = "later", priority = 20),
                    BreakpointRule(id = "first", priority = 10),
                ),
            )
        }
        val decision = async { coordinator.intercept(candidate("priority", byteArrayOf())) }
        runCurrent()

        assertEquals("first", coordinator.pendingBreakpoints.value.single().ruleId)
        coordinator.clear()
        assertSame(BreakpointDecision.Drop, decision.await())
    }

    @Test
    fun `request edit exceeding header bounds is rejected without resolving the pause`() = runTest {
        val coordinator = BreakpointCoordinator(
            BreakpointLimits(maxEditedHeaderCount = 1, maxEditedHeaderBytes = 8),
        ).also { value ->
            value.replaceRules(listOf(BreakpointRule(id = "bounded", phase = BreakpointPhase.REQUEST)))
        }
        val candidate = candidate("header-limit", byteArrayOf())
        val decision = async { coordinator.intercept(candidate) }
        runCurrent()
        val pending = coordinator.pendingBreakpoints.value.single()
        val oversizedEdit = BreakpointRequestEdit(
            candidate.request.copy(
                head = candidate.request.head.copy(
                    headers = listOf(
                        HeaderField(HeaderName("X-One"), "1"),
                        HeaderField(HeaderName("X-Two"), "2"),
                    ),
                ),
            ),
        )
        val oversizedBytesEdit = BreakpointRequestEdit(
            candidate.request.copy(
                head = candidate.request.head.copy(
                    headers = listOf(HeaderField(HeaderName("X"), "12345678")),
                ),
            ),
        )

        assertFalse(coordinator.resolve(pending.id, BreakpointDecision.ResumeRequest(oversizedEdit)))
        assertFalse(coordinator.resolve(pending.id, BreakpointDecision.ResumeRequest(oversizedBytesEdit)))
        assertEquals(pending.id, coordinator.pendingBreakpoints.value.single().id)
        assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged))
        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
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
