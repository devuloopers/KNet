package com.devuloopers.knet.testingserver.payload

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PayloadRouter(private val handler: LargePayloadHandler) {

    @Bean
    fun payloadRoutes() = coRouter {
        GET("/api/payload/{size}", handler::handlePayload)
    }
}
