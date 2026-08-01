package com.devuloopers.knet.testingserver.error

import com.devuloopers.knet.testingserver.model.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class ErrorSimulationHandler {

    suspend fun handleTimeout(request: ServerRequest): ServerResponse {
        val error = ErrorResponse(
            success = false,
            status = 504,
            error = "Gateway Timeout Simulation",
            path = request.path()
        )
        return ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT).bodyValueAndAwait(error)
    }

    suspend fun handleMalformedJson(request: ServerRequest): ServerResponse {
        val rawMalformedJson = "{\"status\": 400, \"message\": \"Malformed JSON simulation\", \"unclosedString: }"
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .bodyValueAndAwait(rawMalformedJson)
    }
}
