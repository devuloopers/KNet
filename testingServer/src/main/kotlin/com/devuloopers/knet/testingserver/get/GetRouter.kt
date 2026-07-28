package com.devuloopers.knet.testingserver.get

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class GetRouter(private val handler: GetHandler) {

    @Bean
    fun getRoutes() = coRouter {
        "/api/test/get".nest {
            GET("", handler::handleGet)
            GET("/params", handler::handleGetParams)
        }
    }
}
