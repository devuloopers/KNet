package com.devuloopers.knet.testingserver.post

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PostRouter(private val handler: PostHandler) {

    @Bean
    fun postRoutes() = coRouter {
        "/api/test/post".nest {
            POST("/json", handler::handlePostJson)
            POST("/xml", handler::handlePostXml)
            POST("/form", handler::handlePostForm)
        }
    }
}
