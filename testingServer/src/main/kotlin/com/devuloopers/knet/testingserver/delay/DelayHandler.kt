package com.devuloopers.knet.testingserver.delay

import com.devuloopers.knet.testingserver.common.ResponseFactory
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import kotlin.time.Duration.Companion.milliseconds

@Component
class DelayHandler {

    suspend fun handleDelay(request: ServerRequest): ServerResponse {
        val secondsStr = request.pathVariable("seconds")
        val seconds = secondsStr.toLongOrNull()?.coerceIn(0L, 30L) ?: 1L

        // Non-blocking coroutine delay
        delay((seconds * 1000L).milliseconds)

        return ResponseFactory.ok(request, body = mapOf("delayedSeconds" to seconds))
    }
}
