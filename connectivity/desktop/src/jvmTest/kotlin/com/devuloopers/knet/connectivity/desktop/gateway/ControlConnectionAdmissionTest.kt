package com.devuloopers.knet.connectivity.desktop.gateway

import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.Semaphore
import kotlin.test.*

/** Pending TLS and idle keep-alive sockets cannot bypass the physical connection budget. */
class ControlConnectionAdmissionTest {
    @Test fun `connection capacity rejects overflow and becomes reusable only after actual close`() {
        val permits = Semaphore(1)
        val admitted = EmbeddedChannel(ControlConnectionAdmission(permits))
        val rejected = EmbeddedChannel(ControlConnectionAdmission(permits))
        try {
            assertTrue(admitted.isActive)
            assertFalse(rejected.isActive)
            assertEquals(0, permits.availablePermits())
            admitted.close(); admitted.close()
            assertEquals(1, permits.availablePermits())
            EmbeddedChannel(ControlConnectionAdmission(permits)).also { replacement ->
                try { assertTrue(replacement.isActive); assertEquals(0, permits.availablePermits()) }
                finally { replacement.finishAndReleaseAll() }
            }
            assertEquals(1, permits.availablePermits())
        } finally { admitted.finishAndReleaseAll(); rejected.finishAndReleaseAll() }
    }
    @Test fun `protocol pipeline replacement cannot prematurely release an open transport`() {
        val permits = Semaphore(1)
        val guard = ControlConnectionAdmission(permits)
        val channel = EmbeddedChannel(guard)
        try {
            channel.pipeline().remove(guard)
            assertEquals(0, permits.availablePermits())
            channel.close()
            assertEquals(1, permits.availablePermits())
        } finally { channel.finishAndReleaseAll() }
    }
}
