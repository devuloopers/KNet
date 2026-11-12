package com.devuloopers.knet.ui.desktop.traffic.protection

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.flow.*

/** Bounded fake canonical metadata and policy stores used by behavioral tests. */
internal class ProtectionFixture {
    val captured = OpaqueFlowSnapshot(OpaqueFlowId("flow"), ConnectionId("connection"), TrafficEndpoint("2001:db8::10",443),
        "play-fe.googleapis.com", OpaqueTransportProtocol.TCP, OpaqueSecurityProtocol.TLS, null, null,
        policyAction=OpaqueFlowPolicyAction.INSPECT, protectionOrigin=ProtectionDecisionOrigin.GLOBAL,
        inspectionFailureReason=InspectionFailureReason.CERTIFICATE_REJECTED, startedAtEpochMillis=1, completedAtEpochMillis=2,
        uploadedBytes=0, downloadedBytes=0, state=OpaqueFlowState.FAILED,
        terminalOutcome=ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DOWNSTREAM_TLS_HANDSHAKE_FAILED))
    var record: OpaqueFlowSnapshot? = captured
    var context: TrafficConnectionIngress? = TrafficConnectionIngress(IngressContext(IngressKind.LanPairedDevice,ClientIdentity("device-a")),"127.0.0.1",true)
    val fallbackState = MutableStateFlow(AutomaticTlsFallbackState())
    val fallback = object : AutomaticTlsFallback {
        override val state = fallbackState
        override fun activateSession(sessionId: CaptureSessionId?) { fallbackState.value = AutomaticTlsFallbackState(sessionId, state.value.generation + 1) }
        override fun authorize(configuration: ProtectedTrafficConfiguration, authorityIdentity: String?) = state.value.generation
        override fun record(key: AutomaticTlsFallbackKey, generation: Long): Boolean {
            fallbackState.value = state.value.copy(keys = state.value.keys + key); return true
        }
        override fun forget(rule: ProtectedTrafficRule) { clear() }
        override fun clear() { fallbackState.value = state.value.copy(generation = state.value.generation + 1, keys = emptySet()) }
    }
    var reads=0
    var beforeRead: suspend (OpaqueFlowId)->Unit = {}
    var beforeSave: suspend ()->Unit = {}
    var writes=0
    val repository = Repository()
    inner class Repository : ProtectedTrafficRepository {
        override val configuration=MutableStateFlow(ProtectedTrafficConfiguration())
        override suspend fun setDefaultAction(action:ProtectedTrafficAction, automaticFallbackEnabled:Boolean) { configuration.value=configuration.value.copy(defaultAction=action, automaticTlsFallbackEnabled=automaticFallbackEnabled) }
        override suspend fun upsertRule(rule:ProtectedTrafficRule) { beforeSave(); writes++; configuration.value=configuration.value.copy(rules=configuration.value.rules.filterNot { it.id==rule.id }+rule) }
        override suspend fun deleteRule(ruleId:ProtectedTrafficRuleId) { configuration.value=configuration.value.copy(rules=configuration.value.rules.filterNot { it.id==ruleId }) }
    }
    val query=Query()
    inner class Query:TrafficQuery {
        override val generations=emptyFlow<TrafficGeneration>()
        override suspend fun query(query:TrafficPageQuery)=TrafficPage(emptyList(),nextCursor=null,totalCount=0,generation=0)
        override suspend fun getExchange(exchangeId:ExchangeId):HttpExchangeSnapshot?=null
        override suspend fun getOpaqueFlow(flowId:OpaqueFlowId):OpaqueFlowSnapshot? { reads++; beforeRead(flowId); return record?.copy(id=flowId) }
        override suspend fun getConnectionIngress(connectionId:ConnectionId)=context
        override suspend fun readBody(bodyId:BodyId,range:BodyRange)=BodyChunk(byteArrayOf(),range.offset,true)
    }
}
