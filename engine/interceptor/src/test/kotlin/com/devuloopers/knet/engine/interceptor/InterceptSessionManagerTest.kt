package com.devuloopers.knet.engine.interceptor

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InterceptSessionManagerTest {

    @BeforeTest
    fun setUp() {
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testSuspendRequestAndResume() {
        val reqDto = TestFixtures.createHttpRequestDto()
        val event = InterceptSessionManager.suspendRequest(reqDto)

        assertNotNull(event.id)
        assertEquals(1, InterceptSessionManager.getActiveEvents().size)
        assertEquals(event.id, InterceptSessionManager.getActiveEvent(event.id)?.id)

        val resumed = InterceptSessionManager.resume(event.id, InterceptResult.Resume())
        assertTrue(resumed)
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }

    @Test
    fun testClearSuspensions() {
        val reqDto = TestFixtures.createHttpRequestDto()
        val resDto = TestFixtures.createHttpResponseDto()
        InterceptSessionManager.suspendRequest(reqDto)
        InterceptSessionManager.suspendResponse(reqDto, resDto)

        assertEquals(2, InterceptSessionManager.getActiveEvents().size)

        InterceptSessionManager.clearSuspensions()
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }

    @Test
    fun testFifoPreservation() {
        val req1 = TestFixtures.createHttpRequestDto(url = "https://api.example.com/1")
        val req2 = TestFixtures.createHttpRequestDto(url = "https://api.example.com/2")
        val req3 = TestFixtures.createHttpRequestDto(url = "https://api.example.com/3")

        val event1 = InterceptSessionManager.suspendRequest(req1)
        val event2 = InterceptSessionManager.suspendRequest(req2)
        val event3 = InterceptSessionManager.suspendRequest(req3)

        val active = InterceptSessionManager.getActiveEvents()
        assertEquals(3, active.size)
        assertEquals(event1.id, active[0].id)
        assertEquals(event2.id, active[1].id)
        assertEquals(event3.id, active[2].id)

        // Resuming the middle item preserves FIFO for remaining items
        val resumed2 = InterceptSessionManager.resume(event2.id, InterceptResult.Drop)
        assertTrue(resumed2)

        val remaining = InterceptSessionManager.getActiveEvents()
        assertEquals(2, remaining.size)
        assertEquals(event1.id, remaining[0].id)
        assertEquals(event3.id, remaining[1].id)

        // Clean up
        InterceptSessionManager.clearSuspensions()
        assertTrue(InterceptSessionManager.getActiveEvents().isEmpty())
    }

    @Test
    fun testResumeIdempotency() {
        val req = TestFixtures.createHttpRequestDto()
        val event = InterceptSessionManager.suspendRequest(req)

        val firstResume = InterceptSessionManager.resume(event.id, InterceptResult.Resume())
        val secondResume = InterceptSessionManager.resume(event.id, InterceptResult.Resume())

        assertTrue(firstResume, "First resume attempt must return true")
        assertTrue(!secondResume, "Subsequent resume attempts must return false")
    }
}

