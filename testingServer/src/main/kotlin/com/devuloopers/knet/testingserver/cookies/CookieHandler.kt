package com.devuloopers.knet.testingserver.cookies

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class CookieHandler {

    suspend fun handleCookies(request: ServerRequest): ServerResponse {
        val clientCookies = request.cookies().toSingleValueMap().mapValues { it.value.value }

        val dto = TestServerResponse(
            status = 200,
            message = "Cookies processed successfully",
            url = request.uri().toString(),
            method = "GET",
            cookies = clientCookies
        )

        val setCookie = ResponseCookie.from("KNet-Session-Id", "session_abc123_xyz")
            .path("/")
            .httpOnly(true)
            .build()

        return ServerResponse.ok()
            .cookie(setCookie)
            .bodyValueAndAwait(dto)
    }
}
