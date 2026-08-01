package com.devuloopers.knet.testingserver.status

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class StatusRouter(private val handler: StatusHandler) {

    @Bean
    fun statusRoutes() = coRouter {
        GET("/api/status/{code}", handler::handleStatus)
    }
}
