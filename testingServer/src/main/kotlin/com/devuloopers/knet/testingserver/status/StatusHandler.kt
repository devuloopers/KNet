package com.devuloopers.knet.testingserver.status

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

@Component
class StatusHandler {

    suspend fun handleStatus(request: ServerRequest): ServerResponse {
        val codeStr = request.pathVariable("code")
        val code = codeStr.toIntOrNull()?.coerceIn(100, 599) ?: 200
        val httpStatus = HttpStatus.resolve(code) ?: HttpStatus.valueOf(code)

        return ResponseFactory.ok(request, body = mapOf("customStatusCode" to code), status = httpStatus)
    }
}
