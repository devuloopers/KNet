package com.devuloopers.knet.testingserver.error

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ErrorRouter(private val handler: ErrorSimulationHandler) {

    @Bean
    fun errorRoutes() = coRouter {
        "/api/error".nest {
            GET("/timeout", handler::handleTimeout)
            GET("/malformed-json", handler::handleMalformedJson)
        }
    }
}
