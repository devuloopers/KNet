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
}
