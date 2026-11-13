package com.devuloopers.knet.companion.model

import kotlin.test.*

/** Real codec round trips and malformed/version/direction/size boundaries used by both platforms. */
class CompanionSessionProtocolTest {
    @Test fun `duplicate top level escaped aliases and nested configuration keys are rejected`() {
        val start = CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.START, "run"))
        assertFails { CompanionSessionProtocol.decode(start.replace("\"kind\":", "\"kind\":\"STOP\",\"kind\":")) }
        val configuration = CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION,
            "run", revision = 1, configuration = PacketConditionConfiguration.Disabled))
        assertFails { CompanionSessionProtocol.decode(configuration.replace("\"enabled\":", "\"enabled\":true,\"enabled\":")) }
    }

    @Test fun `every control message round trips with its closed payload shape`() {
        for (kind in CompanionSessionMessageKind.entries) {
            val message = CompanionSessionMessage(kind, "run_1", 1, if (kind in listOf(
                CompanionSessionMessageKind.CONFIGURATION, CompanionSessionMessageKind.POLICY_RENEWAL,
                CompanionSessionMessageKind.POLICY_ACK)) 1 else 0,
                configuration = if (kind == CompanionSessionMessageKind.CONFIGURATION) PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000) else null,
                acknowledgement = if (kind == CompanionSessionMessageKind.POLICY_ACK) CompanionPolicyAcknowledgement.APPLIED else null)
            assertEquals(message, CompanionSessionProtocol.decode(CompanionSessionProtocol.encode(message)))
        }
    }
    @Test fun `unknown versions operations fields and invalid shape cannot enter a session`() {
        val valid = CompanionSessionProtocol.encode(CompanionSessionMessage(CompanionSessionMessageKind.START, "run"))
        for (invalid in listOf(valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("START", "UNKNOWN"), valid.dropLast(1) + ",\"deviceId\":\"spoofed\"}",
            valid.replace("\"sequence\":0", "\"sequence\":-1"), "", "[]", "null", "x".repeat(8193))) {
            assertFails { CompanionSessionProtocol.decode(invalid) }
        }
        assertFailsWith<IllegalArgumentException> { CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION, "run", revision = 1) }
        assertFailsWith<IllegalArgumentException> { CompanionSessionMessage(CompanionSessionMessageKind.POLICY_ACK, "run", revision = 1) }
        assertFailsWith<IllegalArgumentException> { CompanionSessionMessage(CompanionSessionMessageKind.HEARTBEAT, "run", configuration = PacketConditionConfiguration()) }
        assertFailsWith<IllegalArgumentException> { CompanionSessionMessage(CompanionSessionMessageKind.POLICY_RENEWAL, "run", revision = 0) }
    }
    @Test fun `direction and identity safety are explicit`() {
        val clientKinds = setOf(CompanionSessionMessageKind.START, CompanionSessionMessageKind.HEARTBEAT,
            CompanionSessionMessageKind.STOP, CompanionSessionMessageKind.POLICY_ACK)
        for (kind in CompanionSessionMessageKind.entries) assertEquals(kind in clientKinds, CompanionSessionProtocol.allowsClient(kind))
        for (run in listOf("", "x/y", "x\n", "x".repeat(129))) {
            assertFailsWith<IllegalArgumentException> { CompanionSessionMessage(CompanionSessionMessageKind.START, run) }
        }
    }
}
