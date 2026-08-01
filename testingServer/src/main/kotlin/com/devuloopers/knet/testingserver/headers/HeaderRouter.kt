package com.devuloopers.knet.testingserver.headers

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class HeaderRouter(private val handler: HeaderHandler) {

    @Bean
    fun headerRoutes() = coRouter {
        GET("/api/headers", handler::handleHeaders)
    }
}
