package com.devuloopers.knet.testingserver.status

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class StatusHandler {

    suspend fun handleStatusCode(request: ServerRequest): ServerResponse {
        val codeStr = request.pathVariable("code")
        val code = codeStr.toIntOrNull() ?: 200
        val httpStatus = HttpStatus.resolve(code) ?: HttpStatus.OK

        val dto = TestServerResponse(
            status = code,
            message = "Returned HTTP Status Code $code (${httpStatus.reasonPhrase})",
            url = request.uri().toString(),
            method = "GET"
        )
        return ServerResponse.status(httpStatus).bodyValueAndAwait(dto)
    }
}
