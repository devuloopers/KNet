package com.devuloopers.knet.testingserver.put

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PutRouter(private val handler: PutHandler) {

    @Bean
    fun putRoutes() = coRouter {
        PUT("/api/test/put", handler::handlePut)
    }
}
