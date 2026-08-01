package com.devuloopers.knet.testingserver.payload

import com.devuloopers.knet.testingserver.common.ResponseFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

@Component
class LargePayloadHandler {

    suspend fun handlePayload(request: ServerRequest): ServerResponse {
        val sizeStr = request.pathVariable("size").lowercase()
        val byteCount = when (sizeStr) {
            "1kb" -> 1_024
            "10kb" -> 10_240
            "100kb" -> 102_400
            "1mb" -> 1_048_576
            "10mb" -> 10_485_760
            else -> 1024
        }
        val generatedString = "A".repeat(byteCount)
        return ResponseFactory.ok(request, body = mapOf("payloadSizeBytes" to byteCount, "data" to generatedString))
    }
}
