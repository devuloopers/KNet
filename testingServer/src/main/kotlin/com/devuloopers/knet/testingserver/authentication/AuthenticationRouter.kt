package com.devuloopers.knet.testingserver.authentication

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AuthenticationRouter(private val handler: AuthenticationHandler) {

    @Bean
    fun authRoutes() = coRouter {
        "/api/auth".nest {
            GET("/bearer", handler::handleBearer)
            GET("/basic", handler::handleBasic)
            GET("/apikey/header", handler::handleApiKeyHeader)
            GET("/apikey/query", handler::handleApiKeyQuery)
        }
    }
}
