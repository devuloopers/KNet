package com.devuloopers.knet.testingserver.basic

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class BasicRouter(
    private val getHandler: GetHandler,
    private val postHandler: PostHandler,
    private val putHandler: PutHandler,
    private val patchHandler: PatchHandler,
    private val deleteHandler: DeleteHandler
) {
    @Bean
    fun basicRoutes() = coRouter {
        "/api".nest {
            GET("/get", getHandler::handleGet)
            POST("/post", postHandler::handlePost)
            PUT("/put", putHandler::handlePut)
            PATCH("/patch", patchHandler::handlePatch)
            DELETE("/delete", deleteHandler::handleDelete)
        }
    }
}
