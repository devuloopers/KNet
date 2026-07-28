package com.devuloopers.knet.testingserver.headers

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class HeaderHandler {

    suspend fun handleCustomHeaders(request: ServerRequest): ServerResponse {
        val requestHeaders = request.headers().asHttpHeaders().toSingleValueMap()

        val dto = TestServerResponse(
            status = 200,
            message = "Custom response headers injected",
            url = request.uri().toString(),
            method = "GET",
            headers = requestHeaders
        )

        return ServerResponse.ok()
            .header("X-KNet-Server", "Spring-Boot-WebFlux")
            .header("X-RateLimit-Limit", "1000")
            .header("Cache-Control", "no-cache, no-store")
            .bodyValueAndAwait(dto)
    }
}
