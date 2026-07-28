package com.devuloopers.knet.testingserver.authentication

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AuthRouter(private val handler: AuthHandler) {

    @Bean
    fun authRoutes() = coRouter {
        "/api/test/auth".nest {
            GET("/bearer", handler::handleBearerAuth)
            GET("/basic", handler::handleBasicAuth)
            GET("/apikey/header", handler::handleApiKeyHeader)
            GET("/apikey/query", handler::handleApiKeyQuery)
        }
    }
}
