package com.devuloopers.knet.testingserver.basic

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import kotlinx.coroutines.reactor.awaitSingleOrNull

@Component
class PutHandler {
    suspend fun handlePut(request: ServerRequest): ServerResponse {
        val bodyContent = request.bodyToMono(String::class.java).awaitSingleOrNull()
        return ResponseFactory.ok(request, body = bodyContent)
    }
}
