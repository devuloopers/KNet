package com.devuloopers.knet.testingserver.cookies

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class CookieHandler {

    suspend fun handleGetCookies(request: ServerRequest): ServerResponse {
        return ResponseFactory.ok(request)
    }

    suspend fun handleSetCookie(request: ServerRequest): ServerResponse {
        val cookie = ResponseCookie.from("knet_session", "session_token_xyz")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(3600)
            .build()

        val response = ResponseFactory.ok(request, body = mapOf("message" to "Set-Cookie header attached"))
        return ServerResponse.from(response)
            .cookie(cookie)
            .bodyValueAndAwait(response)
    }
}
