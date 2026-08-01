package com.devuloopers.knet.engine.interceptor

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterceptorConcurrencyTest {

    @Test
    fun testParallelSuspensionsAndResumptions() {
        val executor = Executors.newFixedThreadPool(10)

        repeat(100) { i ->
            executor.submit {
                val req = TestFixtures.createHttpRequestDto(url = "https://api.example.com/$i")
                val event = InterceptSessionManager.suspendRequest(req)
                InterceptSessionManager.resume(event.id, InterceptResult.Resume())
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue(finished, "Parallel suspensions and resumptions must finish within timeout")
        assertEquals(0, InterceptSessionManager.getActiveEvents().size)
    }
}
