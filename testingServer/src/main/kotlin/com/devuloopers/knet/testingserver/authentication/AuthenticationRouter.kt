package com.devuloopers.knet.testingserver.authentication

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

/**
 * Spring WebFlux Router configuration for authentication endpoints.
 *
 * @property handler Handler processing authentication requests.
 */
@Configuration
class AuthenticationRouter(private val handler: AuthenticationHandler) {

    /**
     * Defines functional reactive routes under `/api/auth`.
     */
    @Bean
    fun authenticationRoutes() = coRouter {
        "/api/auth".nest {
            GET("/bearer", handler::handleBearer)
            GET("/basic", handler::handleBasic)
            GET("/apikey/header", handler::handleApiKeyHeader)
            GET("/apikey/query", handler::handleApiKeyQuery)
        }
    }
}

