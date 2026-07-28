package com.devuloopers.knet.testingserver.put

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class PutHandler {

    suspend fun handlePut(request: ServerRequest): ServerResponse {
        val bodyText = request.awaitBody<String>()
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "PUT resource updated successfully",
            url = request.uri().toString(),
            method = "PUT",
            headers = headers,
            body = bodyText
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }
}
