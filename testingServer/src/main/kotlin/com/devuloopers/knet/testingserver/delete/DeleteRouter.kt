package com.devuloopers.knet.testingserver.delete

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class DeleteRouter(private val handler: DeleteHandler) {

    @Bean
    fun deleteRoutes() = coRouter {
        DELETE("/api/test/delete", handler::handleDelete)
    }
}
