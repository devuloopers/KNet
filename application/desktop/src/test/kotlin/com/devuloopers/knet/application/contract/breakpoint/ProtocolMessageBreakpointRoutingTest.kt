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
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolMessageBreakpointRoutingTest {
    @Test fun `source recording pause gates new framed messages without resolving existing pauses`() = runTest {
        val owner = PolicyWorkspace.Device(RegisteredDeviceId("owner"))
        val sibling = PolicyWorkspace.Device(RegisteredDeviceId("sibling"))
        val coordinator = BreakpointCoordinator(protocolRegistry = BreakpointProtocolRegistry(listOf(MessageProtocolExtension(SEMANTIC_PROTOCOL))))
        val shared = rule("shared", 1, SEMANTIC_PROTOCOL)
        coordinator.replaceWorkspaceRules(mapOf(owner to listOf(shared), sibling to listOf(shared)))
        val previous = async { coordinator.interceptMessage(candidate().copy(workspace = owner)) }
        runCurrent()
        val retained = coordinator.pendingProtocolMessages.value.single()
        coordinator.setCaptureAdmission(com.devuloopers.knet.application.contract.traffic.CaptureAdmissionPolicy(false, mapOf(sibling to true)))
        assertEquals(retained, coordinator.pendingProtocolMessages.value.single())
        kotlin.test.assertFalse(coordinator.mayInterceptMessageFor(owner, candidate().request,
            candidate().protocolRoute, TrafficDirection.CLIENT_TO_SERVER))
        assertTrue(coordinator.mayInterceptMessageFor(sibling, candidate().request,
            candidate().protocolRoute, TrafficDirection.CLIENT_TO_SERVER))
        assertSame(ProtocolMessageBreakpointDecision.ContinueUnchanged,
            coordinator.interceptMessage(candidate().copy(workspace = owner, messageId = ProtocolMessageId("while-paused"))))
        assertTrue(coordinator.resolveProtocolMessage(retained.id, ProtocolMessageBreakpointDecision.ContinueUnchanged))
        assertSame(ProtocolMessageBreakpointDecision.ContinueUnchanged, previous.await())
    }

    @Test
    fun `one thousand shared rules compile once across ten workspaces and prefilters never recompile`() {
        val extension = MessageProtocolExtension(SEMANTIC_PROTOCOL)
        val coordinator = BreakpointCoordinator(protocolRegistry=BreakpointProtocolRegistry(listOf(extension)))
        val devices = (1..10).map { PolicyWorkspace.Device(RegisteredDeviceId("device-$it")) }
        val definitions = (1..1_000).map { index -> rule("shared-$index",index,SEMANTIC_PROTOCOL)
            .copy(urlPattern="https://host-$index.test/*") }
        coordinator.replaceWorkspaceRules(devices.associateWith { definitions })
        assertEquals(1_000,extension.compilationCount)
        repeat(100) { index ->
            kotlin.test.assertFalse(coordinator.mayInterceptMessageFor(devices[index % devices.size],candidate().request,
                listOf(SEMANTIC_PROTOCOL),TrafficDirection.CLIENT_TO_SERVER))
        }
        assertEquals(1_000,extension.compilationCount)
        assertTrue(coordinator.pendingBreakpoints.value.isEmpty())
        assertTrue(coordinator.pendingProtocolMessages.value.isEmpty())
    }


    @Test
    fun `message pauses and targeted drop retain sibling ownership after shared rules are disabled`() = runTest {
        val owner = PolicyWorkspace.Device(RegisteredDeviceId("owner"))
        val sibling = PolicyWorkspace.Device(RegisteredDeviceId("sibling"))
        val coordinator = BreakpointCoordinator(protocolRegistry = BreakpointProtocolRegistry(listOf(MessageProtocolExtension(SEMANTIC_PROTOCOL))))
        val shared = rule("shared", 1, SEMANTIC_PROTOCOL)
        coordinator.replaceWorkspaceRules(mapOf(owner to listOf(shared), sibling to listOf(shared)))
        val first = async { coordinator.interceptMessage(candidate().copy(workspace=owner)) }
        val second = async { coordinator.interceptMessage(candidate().copy(workspace=sibling, exchangeId=ExchangeId("sibling"), messageId=ProtocolMessageId("sibling"))) }
        runCurrent()
        assertEquals(setOf(owner,sibling), coordinator.pendingProtocolMessages.value.map { it.candidate.workspace }.toSet())
        coordinator.replaceWorkspaceRules(emptyMap())
        val ownerPending = coordinator.pendingProtocolMessages.value.single { it.candidate.workspace == owner }
        assertTrue(com.devuloopers.knet.application.usecase.breakpoint.ResolveProtocolMessageBreakpointUseCase(coordinator).dropStream(ownerPending.id))
        runCurrent()
        assertSame(ProtocolMessageBreakpointDecision.DropStream, first.await())
        val pending = coordinator.pendingProtocolMessages.value.single()
        assertEquals(sibling,pending.candidate.workspace)
        assertTrue(coordinator.resolveProtocolMessage(pending.id,ProtocolMessageBreakpointDecision.ContinueUnchanged))
        assertSame(ProtocolMessageBreakpointDecision.ContinueUnchanged,second.await())
    }

    @Test
    fun `global pending cap counts HTTP and framed messages together while reserving sibling capacity`() = runTest {
        val devices = (1..4).map { PolicyWorkspace.Device(RegisteredDeviceId("device-$it")) }
        val coordinator = BreakpointCoordinator(
            limits=BreakpointLimits(maxPendingConnections=3,maxPendingBytes=100,maxEditableBodyBytes=16),
            protocolRegistry=BreakpointProtocolRegistry(listOf(MessageProtocolExtension(SEMANTIC_PROTOCOL))))
        val rules = listOf(rule("message",1,SEMANTIC_PROTOCOL),BreakpointRule("http",urlPattern="*",phase=BreakpointPhase.REQUEST))
        coordinator.replaceWorkspaceRules(devices.associateWith { rules })
        fun http(index:Int) = BreakpointCandidate(ExchangeId("http-$index"),BreakpointPhase.REQUEST,candidate().request,
            startedAtEpochMillis=1,workspace=devices[index])
        val first = async { coordinator.interceptMessage(candidate().copy(workspace=devices[0])) }
        val second = async { coordinator.intercept(http(1)) }
        val third = async { coordinator.interceptMessage(candidate().copy(workspace=devices[2],exchangeId=ExchangeId("third"),messageId=ProtocolMessageId("third"))) }
        runCurrent()
        assertEquals(2,coordinator.pendingProtocolMessages.value.size)
        assertEquals(1,coordinator.pendingBreakpoints.value.size)
        assertSame(BreakpointDecision.ContinueUnchanged,coordinator.intercept(http(3)))
        assertSame(ProtocolMessageBreakpointDecision.ContinueUnchanged,coordinator.interceptMessage(candidate().copy(workspace=devices[3])))
        coordinator.clear()
        assertSame(ProtocolMessageBreakpointDecision.DropStream,first.await())
        assertSame(BreakpointDecision.Drop,second.await())
        assertSame(ProtocolMessageBreakpointDecision.DropStream,third.await())
    }


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
    var compilationCount: Int = 0
        private set

    override val definition = BreakpointProtocolDefinition(
        protocolId = protocolId,
        displayName = protocolId.value,
        criteriaVersion = 1,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = emptyList(),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        compilationCount++
        return criteria.takeIf { value ->
            value.protocolId == protocolId && value.encodedPayload == MATCH_ALL
        }?.let { MessageCompiledCriteria(protocolId) }
    }

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
