package com.devuloopers.knet.testingserver.redirect

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class RedirectRouter(private val handler: RedirectHandler) {

    @Bean
    fun redirectRoutes() = coRouter {
        GET("/api/redirect/{code}", handler::handleRedirect)
    }
}
