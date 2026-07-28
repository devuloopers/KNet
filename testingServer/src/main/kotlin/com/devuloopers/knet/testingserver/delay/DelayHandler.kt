package com.devuloopers.knet.testingserver.delay

import com.devuloopers.knet.testingserver.model.TestServerResponse
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import kotlin.time.Duration.Companion.milliseconds

@Component
class DelayHandler {

    suspend fun handleDelay(request: ServerRequest): ServerResponse {
        val secondsStr = request.pathVariable("seconds")
        val seconds = secondsStr.toLongOrNull()?.coerceIn(1L, 10L) ?: 1L

        // Asynchronous non-blocking coroutine delay
        delay((seconds * 1000L).milliseconds)

        val dto = TestServerResponse(
            status = 200,
            message = "Delayed response for $seconds seconds",
            url = request.uri().toString(),
            method = "GET",
            data = mapOf("delayedSeconds" to seconds)
        )
        return ServerResponse.ok().bodyValueAndAwait(dto)
    }
}
