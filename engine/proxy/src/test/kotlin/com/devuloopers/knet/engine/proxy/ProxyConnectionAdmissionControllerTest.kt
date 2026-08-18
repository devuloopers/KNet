package com.devuloopers.knet.engine.proxy

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests bounded, idempotent connection admission for one proxy runtime. */
class ProxyConnectionAdmissionControllerTest {

    /** Verifies total and per-client downstream limits recover when leases close. */
    @Test
    fun `downstream limits reject saturation and recover after release`() {
        val controller = ProxyConnectionAdmissionController(limitedPolicy())
        val firstClient = assertNotNull(controller.tryAcquireDownstream("client-a"))

        assertNull(controller.tryAcquireDownstream("client-a"))
        val secondClient = assertNotNull(controller.tryAcquireDownstream("client-b"))
        assertNull(controller.tryAcquireDownstream("client-c"))

        firstClient.close()
        firstClient.close()
        assertNotNull(controller.tryAcquireDownstream("client-a")).close()
        secondClient.close()
    }

    /** Verifies upstream saturation and idempotent lease release. */
    @Test
    fun `upstream limit recovers after exactly once release`() {
        val controller = ProxyConnectionAdmissionController(limitedPolicy())
        val lease = assertNotNull(controller.tryAcquireUpstream())

        assertNull(controller.tryAcquireUpstream())
        lease.close()
        lease.close()
        assertNotNull(controller.tryAcquireUpstream()).close()
    }

    /** Creates small deterministic limits used by controller scenarios. */
    private fun limitedPolicy(): KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(
        maximumDownstreamConnections = 2,
        maximumConnectionsPerClient = 1,
        maximumUpstreamConnections = 1,
    )
}
