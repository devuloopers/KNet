package com.devuloopers.knet.testingserver.authentication

import com.devuloopers.knet.testingserver.model.TestServerResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class AuthHandler {

    suspend fun handleBearerAuth(request: ServerRequest): ServerResponse {
        val authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substringAfter(" ").trim()
            val dto = TestServerResponse(
                status = 200,
                message = "Bearer authentication successful",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to true, "token" to token)
            )
            ServerResponse.ok().bodyValueAndAwait(dto)
        } else {
            val dto = TestServerResponse(
                status = 401,
                message = "Unauthorized: Missing or invalid Bearer token",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to false)
            )
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(dto)
        }
    }

    suspend fun handleBasicAuth(request: ServerRequest): ServerResponse {
        val authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Basic ", ignoreCase = true)) {
            val dto = TestServerResponse(
                status = 200,
                message = "Basic authentication successful",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to true)
            )
            ServerResponse.ok().bodyValueAndAwait(dto)
        } else {
            val dto = TestServerResponse(
                status = 401,
                message = "Unauthorized: Missing or invalid Basic credentials",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to false)
            )
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(dto)
        }
    }

    suspend fun handleApiKeyHeader(request: ServerRequest): ServerResponse {
        val keyHeader = request.headers().firstHeader("X-API-Key")
        return if (!keyHeader.isNullOrBlank()) {
            val dto = TestServerResponse(
                status = 200,
                message = "API Key Header validation successful",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to true, "apiKey" to keyHeader)
            )
            ServerResponse.ok().bodyValueAndAwait(dto)
        } else {
            val dto = TestServerResponse(
                status = 401,
                message = "Unauthorized: Missing X-API-Key header",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to false)
            )
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(dto)
        }
    }

    suspend fun handleApiKeyQuery(request: ServerRequest): ServerResponse {
        val keyParam = request.queryParam("api_key").orElse(null)
        return if (!keyParam.isNullOrBlank()) {
            val dto = TestServerResponse(
                status = 200,
                message = "API Key Query parameter validation successful",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to true, "apiKey" to keyParam)
            )
            ServerResponse.ok().bodyValueAndAwait(dto)
        } else {
            val dto = TestServerResponse(
                status = 401,
                message = "Unauthorized: Missing api_key query parameter",
                url = request.uri().toString(),
                method = "GET",
                data = mapOf("authenticated" to false)
            )
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(dto)
        }
    }
}
