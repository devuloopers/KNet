package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureEntityMapper
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Historical automatic evidence remains readable after retirement; new captures never create automatic identities. */
class RetiredCompatibilityHistoryTest {
    @Test fun `historical origin reason and retired identity survive restart without becoming a live rule`() = runTest {
        val root = Files.createTempDirectory("knet-smart-evidence-").toFile()
        val file = root.resolve("traffic.db")
        val event = CaptureEvent.OpaqueFlowStarted(
            sessionId = CaptureSessionId("session"), connectionId = ConnectionId("connection"), sequence = 1,
            occurredAtEpochMillis = 1_000, flowId = OpaqueFlowId("flow"), flowVersion = 0,
            destination = TrafficEndpoint("catalog.example.com", 443), serverName = "catalog.example.com",
            transport = OpaqueTransportProtocol.TCP, security = OpaqueSecurityProtocol.TLS,
            sourceApplicationId = null, policyRuleId = null,
            policyAction = OpaqueFlowPolicyAction.TUNNEL, protectionOrigin = ProtectionDecisionOrigin.MANUAL,
            inspectionFailureReason = InspectionFailureReason.CERTIFICATE_REJECTED,
        )
        val first = DatabaseFactory.create(file)
        try {
            val current = CanonicalCaptureEntityMapper.opaqueFlow(event)
            assertNull(current.smartEntryId)
            first.canonicalCaptureDao().insertOpaqueFlow(current.copy(
                protectionOrigin = ProtectionDecisionOrigin.SMART.name, smartEntryId = "temporary-decision"))
            assertTrue(first.protectedTrafficDao().getRules().isEmpty())
        }
        finally { first.close() }
        val restarted = DatabaseFactory.create(file)
        try {
            val entity = assertNotNull(restarted.canonicalCaptureDao().getOpaqueFlow("flow"))
            val restored = CanonicalCaptureEntityMapper.opaqueFlowSnapshot(entity)
            assertEquals(ProtectionDecisionOrigin.SMART, restored.protectionOrigin)
            assertEquals(InspectionFailureReason.CERTIFICATE_REJECTED, restored.inspectionFailureReason)
            assertEquals("temporary-decision", restored.smartEntryId)
            val legacy = CanonicalCaptureEntityMapper.opaqueFlowSnapshot(entity.copy(protectionOrigin = null, inspectionFailureReason = null, smartEntryId = null))
            assertNull(legacy.protectionOrigin)
            assertNull(legacy.inspectionFailureReason)
        } finally { restarted.close(); root.deleteRecursively() }
    }
}
