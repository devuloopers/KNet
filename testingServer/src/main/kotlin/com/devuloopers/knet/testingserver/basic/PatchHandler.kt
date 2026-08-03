package com.devuloopers.knet.testingserver.basic

import com.devuloopers.knet.testingserver.common.ResponseFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBodyOrNull

@Component
class PatchHandler(
    private val objectMapper: ObjectMapper
) {
    suspend fun handlePatch(request: ServerRequest): ServerResponse {
        val bodyContent = try {
            request.awaitBodyOrNull<String>()
        } catch (_: Exception) {
            null
        }
        val parsedBody = if (!bodyContent.isNullOrBlank()) {
            try {
                objectMapper.readTree(bodyContent)
            } catch (_: Exception) {
                bodyContent
            }
        } else {
            null
        }
        return ResponseFactory.ok(request, body = parsedBody)
    }
}
