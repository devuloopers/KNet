package com.devuloopers.knet.testingserver.authentication

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

@Component
class AuthenticationHandler {

    suspend fun handleBearer(request: ServerRequest): ServerResponse {
        val authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substringAfter(" ").trim()
            ResponseFactory.ok(request, body = mapOf("authenticated" to true, "type" to "Bearer", "token" to token))
        } else {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to false, "error" to "Missing or invalid Bearer token"),
                status = HttpStatus.UNAUTHORIZED
            )
        }
    }

    suspend fun handleBasic(request: ServerRequest): ServerResponse {
        val authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Basic ", ignoreCase = true)) {
            ResponseFactory.ok(request, body = mapOf("authenticated" to true, "type" to "Basic"))
        } else {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to false, "error" to "Missing or invalid Basic credentials"),
                status = HttpStatus.UNAUTHORIZED
            )
        }
    }

    suspend fun handleApiKeyHeader(request: ServerRequest): ServerResponse {
        val keyHeader = request.headers().firstHeader("X-API-Key")
        return if (!keyHeader.isNullOrBlank()) {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to true, "type" to "ApiKeyHeader", "key" to keyHeader)
            )
        } else {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to false, "error" to "Missing X-API-Key header"),
                status = HttpStatus.UNAUTHORIZED
            )
        }
    }

    suspend fun handleApiKeyQuery(request: ServerRequest): ServerResponse {
        val keyParam = request.queryParam("api_key").orElse(null)
        return if (!keyParam.isNullOrBlank()) {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to true, "type" to "ApiKeyQuery", "key" to keyParam)
            )
        } else {
            ResponseFactory.ok(
                request,
                body = mapOf("authenticated" to false, "error" to "Missing api_key query parameter"),
                status = HttpStatus.UNAUTHORIZED
            )
        }
    }
}
