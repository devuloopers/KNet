package com.devuloopers.knet.testingserver.delete

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class DeleteHandler {

    suspend fun handleDelete(request: ServerRequest): ServerResponse {
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "DELETE resource deleted successfully",
            url = request.uri().toString(),
            method = "DELETE",
            headers = headers
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }
}
