package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.http.HttpMethod
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

class CancellationTest {

    @Test
    fun testCoroutineCancellationAbortsExecution() = runTest {
        val mockEngine = MockEngine { request ->
            delay(5000L) // Simulate long request
            respond(content = "Delayed", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        var isCancelled = false

        val job = launch {
            try {
                client.executeDetailed(url = "https://api.knet.dev/long-running", method = HttpMethod.GET)
            } catch (_: CancellationException) {
                isCancelled = true
            }
        }

        delay(100L)
        job.cancelAndJoin()

        assertTrue(isCancelled)
    }
}
