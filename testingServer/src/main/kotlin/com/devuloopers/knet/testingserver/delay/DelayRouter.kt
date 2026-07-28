package com.devuloopers.knet.testingserver.delay

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class DelayRouter(private val handler: DelayHandler) {

    @Bean
    fun delayRoutes() = coRouter {
        GET("/api/test/delay/{seconds}", handler::handleDelay)
    }
}
