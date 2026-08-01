package com.devuloopers.knet.testingserver.common

import com.devuloopers.knet.testingserver.model.TestResponse
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

/**
 * Centralized response factory for WebFlux [ServerResponse] building.
 */
object ResponseFactory {

    suspend fun ok(
        request: ServerRequest,
        body: Any? = null,
        status: HttpStatus = HttpStatus.OK
    ): ServerResponse {
        val dto = TestResponse(
            success = status.is2xxSuccessful,
            status = status.value(),
            method = request.methodName(),
            path = request.path(),
            headers = RequestUtils.extractHeaders(request),
            query = RequestUtils.extractQueryParams(request),
            cookies = RequestUtils.extractCookies(request),
            body = body
        )
        return ServerResponse.status(status).bodyValueAndAwait(dto)
    }
}
