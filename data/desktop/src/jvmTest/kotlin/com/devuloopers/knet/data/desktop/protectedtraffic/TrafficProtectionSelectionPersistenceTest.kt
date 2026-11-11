package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.usecase.protectedtraffic.*
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureEntityMapper
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import kotlin.test.*

/** Actual Room reopen verifies selected-rule metadata without a click cache or rewriting captured outcomes. */
class TrafficProtectionSelectionPersistenceTest {
    @Test fun `saved selection survives database reopen and both actions preserve rule identity and history`()=runBlocking {
        val root=Files.createTempDirectory("knet-traffic-selection-").toFile()
        val file=root.resolve("traffic.db")
        val session=CaptureSessionId("session")
        val connection=ConnectionId("connection")
        val flowId=OpaqueFlowId("cancelled-flow")
        var savedId:ProtectedTrafficRuleId?=null
        val first=DatabaseFactory.create(file)
        val firstScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val dao=first.canonicalCaptureDao()
            dao.insertSession(CaptureSessionEntity(session.value,1L,null,"ACTIVE",0L))
            dao.insertConnection(CanonicalCaptureEntityMapper.connection(CaptureEvent.ConnectionOpened(session,connection,1,1,
                IngressContext(IngressKind.LanPairedDevice,ClientIdentity("device-a")),TrafficEndpoint("127.0.0.1",12345),
                TrafficEndpoint("127.0.0.1",8080),"tcp")))
            val started=CaptureEvent.OpaqueFlowStarted(session,connection,2,1,flowId,0,
                TrafficEndpoint("2001:db8::10",443),"play-fe.googleapis.com",OpaqueTransportProtocol.TCP,OpaqueSecurityProtocol.TLS,
                null,null,policyAction=OpaqueFlowPolicyAction.INSPECT,protectionOrigin=ProtectionDecisionOrigin.GLOBAL,
                inspectionFailureReason=InspectionFailureReason.CERTIFICATE_REJECTED)
            val entity=CanonicalCaptureEntityMapper.opaqueFlow(started).copy(state="CANCELLED",completedAtEpochMillis=2,
                terminalErrorCode=TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED.code.value)
            dao.insertOpaqueFlow(entity)
            dao.insertOpaqueFlow(entity.copy(id="other-history",captureSequence=0,connectionSequence=3,startedAtEpochMillis=3,completedAtEpochMillis=4))
            val repository=RoomProtectedTrafficRepository(first.protectedTrafficDao(),firstScope)
            withTimeout(10000){repository.configuration.first {it.configurationReady}}
            val query=DesktopTrafficQueryAdapter(dao,FileBodyStore(root.resolve("bodies")))
            val result=SetProtectedTrafficFlowActionUseCase(query,repository).execute(flowId,ProtectedTrafficAction.TUNNEL,ProtectedTrafficQuickRuleScope.DESTINATION)
            savedId=assertIs<SetProtectedTrafficFlowActionResult.Saved>(result).rule.id
            withTimeout(10000){repository.configuration.first {it.rules.any {rule -> rule.id==savedId}}}
            val selected=ObserveProtectedTrafficFlowHandlingUseCase(query,repository)(flowId).first()!!
            assertEquals(savedId,selected.destinationRule!!.id)
            assertEquals(selected.destinationRule,selected.winningRule)
            val matching=ObserveProtectedTrafficFlowHandlingUseCase(query,repository)(OpaqueFlowId("other-history")).first()!!
            assertEquals(selected.destinationRule,matching.destinationRule)
        } finally {firstScope.coroutineContext.job.cancelAndJoin();first.close()}
        val reopened=DatabaseFactory.create(file)
        val reopenedScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val repository=RoomProtectedTrafficRepository(reopened.protectedTrafficDao(),reopenedScope)
            withTimeout(10000){repository.configuration.first {it.configurationReady}}
            val query=DesktopTrafficQueryAdapter(reopened.canonicalCaptureDao(),FileBodyStore(root.resolve("bodies")))
            val observe=ObserveProtectedTrafficFlowHandlingUseCase(query,repository)
            val restored=observe(flowId).first()!!
            assertEquals(savedId,restored.destinationRule!!.id)
            assertEquals(ProtectedTrafficAction.TUNNEL,restored.decision.action)
            assertEquals(restored.destinationRule,restored.winningRule)
            val result=SetProtectedTrafficFlowActionUseCase(query,repository).execute(flowId,ProtectedTrafficAction.INSPECT,ProtectedTrafficQuickRuleScope.DESTINATION)
            assertEquals(savedId,assertIs<SetProtectedTrafficFlowActionResult.Saved>(result).rule.id)
            withTimeout(10000){repository.configuration.first {it.rules.single().action==ProtectedTrafficAction.INSPECT}}
            val inspection=observe(flowId).first()!!
            assertEquals(inspection.destinationRule,inspection.winningRule)
            assertEquals(ProtectedTrafficAction.INSPECT,inspection.decision.action)
            repository.upsertRule(inspection.destinationRule!!.copy(enabled=false))
            withTimeout(10000){repository.configuration.first {!it.rules.single().enabled}}
            assertFalse(observe(flowId).first()!!.destinationRule!!.enabled)
            assertNull(observe(flowId).first()!!.winningRule)
            repository.deleteRule(assertNotNull(savedId))
            withTimeout(10000){repository.configuration.first {it.rules.isEmpty()}}
            assertNull(observe(flowId).first()!!.destinationRule)
            val captured=assertNotNull(query.getOpaqueFlow(flowId))
            assertEquals(OpaqueFlowState.CANCELLED,captured.state)
            assertEquals(OpaqueFlowPolicyAction.INSPECT,captured.policyAction)
            assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED,captured.inspectionFailureReason)
        } finally {reopenedScope.coroutineContext.job.cancelAndJoin();reopened.close();root.deleteRecursively()}
    }
}
