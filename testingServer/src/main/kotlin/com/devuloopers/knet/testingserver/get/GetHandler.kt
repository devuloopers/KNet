package com.devuloopers.knet.testingserver.get

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class GetHandler {

    suspend fun handleGet(request: ServerRequest): ServerResponse {
        val headers = request.headers().asHttpHeaders().toSingleValueMap()
        val queryParams = request.queryParams().toSingleValueMap()
        val cookies = request.cookies().toSingleValueMap().mapValues { it.value.value }


        val responseDto = TestServerResponse(
            status = 200,
            message = "GET request received successfully",
            url = request.uri().toString(),
            method = "GET",
            headers = headers,
            queryParams = queryParams,
            cookies = cookies
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }

    suspend fun handleGetParams(request: ServerRequest): ServerResponse {
        val queryParams = request.queryParams().toSingleValueMap()
        val responseDto = TestServerResponse(
            status = 200,
            message = "Query parameters extracted successfully",
            url = request.uri().toString(),
            method = "GET",
            queryParams = queryParams
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }
}
