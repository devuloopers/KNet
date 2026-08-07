package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

import com.devuloopers.knet.core.http.execution.HttpExecutor

class CancellationTest {

    @Test
    fun testCoroutineCancellationAbortsExecution() = runTest {
        val mockEngine = MockEngine { request ->
            delay(5000L) // Simulate long request
            respond(content = "Delayed", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client: HttpExecutor = KNetApiClient(customEngine = mockEngine)
        var isCancelled = false

        val job = launch {
            try {
                client.execute("https://api.knet.dev/long-running")
            } catch (_: CancellationException) {
                isCancelled = true
            }
        }

        delay(100L)
        job.cancelAndJoin()

        assertTrue(isCancelled)
    }
}
