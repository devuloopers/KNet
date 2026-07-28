package com.devuloopers.knet.testingserver.post

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class PostHandler {

    suspend fun handlePostJson(request: ServerRequest): ServerResponse {
        val bodyText = request.awaitBody<String>()
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "JSON payload received successfully",
            url = request.uri().toString(),
            method = "POST",
            headers = headers,
            body = bodyText
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }

    suspend fun handlePostXml(request: ServerRequest): ServerResponse {
        val bodyText = request.awaitBody<String>()
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "XML payload received successfully",
            url = request.uri().toString(),
            method = "POST",
            headers = headers,
            body = bodyText
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }

    suspend fun handlePostForm(request: ServerRequest): ServerResponse {
        val bodyText = request.awaitBody<String>()
        val headers = request.headers().asHttpHeaders().toSingleValueMap()

        val responseDto = TestServerResponse(
            status = 200,
            message = "Form payload received successfully",
            url = request.uri().toString(),
            method = "POST",
            headers = headers,
            body = bodyText
        )
        return ServerResponse.ok().bodyValueAndAwait(responseDto)
    }
}
