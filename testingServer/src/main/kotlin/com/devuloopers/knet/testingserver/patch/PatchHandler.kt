package com.devuloopers.knet.testingserver.patch

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class PatchHandler {

    suspend fun handlePatch(request: ServerRequest): ServerResponse {
        val bodyText = request.awaitBody<String>()
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "PATCH resource updated partially",
            url = request.uri().toString(),
            method = "PATCH",
            headers = headers,
            body = bodyText
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }
}
