package com.devuloopers.knet.traffic.model

import kotlin.test.*

/** Close provenance never assigns a remote peer from a generic error or invalid combination. */
class OpaqueFlowClosureTest {
    @Test fun `every reason has an explicit evidence-compatible owner`() {
        for (reason in OpaqueFlowCloseReason.entries) {
            val owner = when (reason) {
                OpaqueFlowCloseReason.PEER_EOF -> OpaqueFlowCloseInitiator.DEVICE
                OpaqueFlowCloseReason.CONCURRENT_PEER_EOF, OpaqueFlowCloseReason.IO_ERROR, OpaqueFlowCloseReason.UNKNOWN -> OpaqueFlowCloseInitiator.UNKNOWN
                else -> OpaqueFlowCloseInitiator.KNET
            }
            assertEquals(owner, OpaqueFlowClosure(owner, reason, 0).initiator)
        }
        assertEquals(OpaqueFlowCloseInitiator.SERVER,
            OpaqueFlowClosure(OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.PEER_EOF, 1).initiator)
    }

    @Test fun `unsupported owners and negative timestamps are rejected`() {
        assertFailsWith<IllegalArgumentException> { OpaqueFlowClosure(OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.IO_ERROR, 1) }
        assertFailsWith<IllegalArgumentException> { OpaqueFlowClosure(OpaqueFlowCloseInitiator.DEVICE, OpaqueFlowCloseReason.LOCAL_CLOSE, 1) }
        assertFailsWith<IllegalArgumentException> { OpaqueFlowClosure(OpaqueFlowCloseInitiator.KNET, OpaqueFlowCloseReason.PEER_EOF, 1) }
        assertFailsWith<IllegalArgumentException> { OpaqueFlowClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.UNKNOWN, -1) }
    }
}
