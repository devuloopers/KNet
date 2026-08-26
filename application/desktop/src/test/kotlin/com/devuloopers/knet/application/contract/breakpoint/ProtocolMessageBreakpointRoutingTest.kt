package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolMessageBreakpointRoutingTest {
    @Test
    fun `semantic and transport rules create one pause for one wire message`() = runTest {
        val semantic = MessageProtocolExtension(SEMANTIC_PROTOCOL)
        val transport = MessageProtocolExtension(TRANSPORT_PROTOCOL)
        val coordinator = BreakpointCoordinator(
            protocolRegistry = BreakpointProtocolRegistry(listOf(semantic, transport)),
        )
        coordinator.replaceRules(
            listOf(
                rule(id = "transport", priority = 20, protocolId = TRANSPORT_PROTOCOL),
                rule(id = "semantic", priority = 10, protocolId = SEMANTIC_PROTOCOL),
            ),
        )

        val decision = async { coordinator.interceptMessage(candidate()) }
        runCurrent()

        val pending = coordinator.pendingProtocolMessages.value.single()
        assertEquals("semantic", pending.ruleId)
        assertEquals(SEMANTIC_PROTOCOL, pending.matchedProtocolId)
        assertEquals(1, semantic.inspectionCount)
        assertEquals(1, transport.inspectionCount)
        assertTrue(coordinator.resolveProtocolMessage(
            pending.id,
            ProtocolMessageBreakpointDecision.ContinueUnchanged,
        ))
        assertSame(ProtocolMessageBreakpointDecision.ContinueUnchanged, decision.await())
        assertTrue(coordinator.pendingProtocolMessages.value.isEmpty())
    }

    private fun rule(
        id: String,
        priority: Int,
        protocolId: BreakpointProtocolId,
    ) = BreakpointRule(
        id = id,
        priority = priority,
        phase = BreakpointPhase.BOTH,
        protocolCriteria = ProtocolMatchCriteria(protocolId, MATCH_ALL),
    )

    private fun candidate() = ProtocolMessageBreakpointCandidate(
        exchangeId = ExchangeId("layered-message-exchange"),
        messageId = ProtocolMessageId("layered-message"),
        protocolRoute = listOf(SEMANTIC_PROTOCOL, TRANSPORT_PROTOCOL),
        kind = ProtocolMessageKind.TEXT,
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.GET,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("example.test"),
                    pathAndQuery = "/graphql",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
        ),
        negotiatedSubprotocol = "graphql-transport-ws",
        direction = TrafficDirection.CLIENT_TO_SERVER,
        sequence = 1L,
        declaredBytes = 2L,
        compressed = false,
        compressionEncoding = null,
        body = BreakpointBody("{}".encodeToByteArray()),
        startedAtEpochMillis = 1L,
    )

    private companion object {
        val SEMANTIC_PROTOCOL = BreakpointProtocolId("semantic-message")
        val TRANSPORT_PROTOCOL = BreakpointProtocolId("transport-message")
    }
}

private const val MATCH_ALL: String = "match-all"

private data class MessageProtocolObservation(
    override val protocolId: BreakpointProtocolId,
) : ProtocolObservation

private class MessageProtocolExtension(
    private val protocolId: BreakpointProtocolId,
) : BreakpointProtocolExtension {
    var inspectionCount: Int = 0
        private set

    override val definition = BreakpointProtocolDefinition(
        protocolId = protocolId,
        displayName = protocolId.value,
        criteriaVersion = 1,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = emptyList(),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? =
        criteria.takeIf { value ->
            value.protocolId == protocolId && value.encodedPayload == MATCH_ALL
        }?.let { MessageCompiledCriteria(protocolId) }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation {
        inspectionCount += 1
        return MessageProtocolObservation(protocolId)
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> = emptyList()

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria =
        ProtocolMatchCriteria(protocolId, MATCH_ALL)
}

private class MessageCompiledCriteria(
    override val protocolId: BreakpointProtocolId,
) : CompiledProtocolCriteria {
    override fun matches(observation: ProtocolObservation?): Boolean =
        observation?.protocolId == protocolId
}
