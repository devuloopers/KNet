package com.devuloopers.knet.testingserver.patch

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PatchRouter(private val handler: PatchHandler) {

    @Bean
    fun patchRoutes() = coRouter {
        PATCH("/api/test/patch", handler::handlePatch)
    }
}
