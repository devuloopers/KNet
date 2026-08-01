package com.devuloopers.knet.testingserver.cookies

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class CookieRouter(private val handler: CookieHandler) {

    @Bean
    fun cookieRoutes() = coRouter {
        "/api/cookies".nest {
            GET("", handler::handleGetCookies)
            GET("/set", handler::handleSetCookie)
        }
    }
}
