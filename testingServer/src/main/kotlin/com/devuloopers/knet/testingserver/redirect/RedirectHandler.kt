package com.devuloopers.knet.testingserver.redirect

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import java.net.URI

@Component
class RedirectHandler {

    suspend fun handleRedirect(request: ServerRequest): ServerResponse {
        val codeStr = request.pathVariable("code")
        val status = when (codeStr) {
            "301" -> HttpStatus.MOVED_PERMANENTLY
            "302" -> HttpStatus.FOUND
            "307" -> HttpStatus.TEMPORARY_REDIRECT
            "308" -> HttpStatus.PERMANENT_REDIRECT
            else -> HttpStatus.FOUND
        }
        val targetUri = URI.create("/api/get")
        return ServerResponse.status(status).location(targetUri).buildAndAwait()
    }
}
