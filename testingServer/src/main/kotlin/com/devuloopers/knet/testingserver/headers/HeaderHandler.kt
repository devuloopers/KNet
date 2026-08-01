package com.devuloopers.knet.testingserver.headers

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

@Component
class HeaderHandler {
    suspend fun handleHeaders(request: ServerRequest): ServerResponse {
        return ResponseFactory.ok(request)
    }
}
