package com.devuloopers.knet.testingserver.websocket

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Echoes text and binary WebSocket messages and answers application-visible ping frames with pong frames. */
@Component
class EchoWebSocketHandler : WebSocketHandler {
    /**
     * Keeps one full-duplex session open until either peer closes it.
     *
     * Incoming pooled payloads are copied before constructing an outgoing message so the response never
     * retains a receive buffer after WebFlux releases ownership.
     *
     * @param session Active WebSocket connection.
     * @return Completion signal for the complete connection lifetime.
     */
    override fun handle(session: WebSocketSession): Mono<Void> = session.send(
        session.receive().map { incoming -> incoming.copyFor(session) },
    )

    private fun WebSocketMessage.copyFor(session: WebSocketSession): WebSocketMessage {
        val bytes = ByteArray(payload.readableByteCount())
        payload.read(bytes)
        return when (type) {
            WebSocketMessage.Type.TEXT -> session.textMessage(bytes.decodeToString())
            WebSocketMessage.Type.BINARY -> session.binaryMessage { factory -> factory.wrap(bytes) }
            WebSocketMessage.Type.PING -> session.pongMessage { factory -> factory.wrap(bytes) }
            WebSocketMessage.Type.PONG -> session.pongMessage { factory -> factory.wrap(bytes) }
        }
    }
}

/** Registers raw WebSocket fixtures independently from GraphQL's WebSocket transport. */
@Configuration
class WebSocketConfiguration {
    /**
     * Maps the stable protocol-lab echo path at higher precedence than ordinary HTTP routes.
     *
     * @param echoHandler Stateless WebSocket echo implementation.
     * @return URL handler mapping for raw WebSocket upgrades.
     */
    @Bean
    fun protocolLabWebSocketMapping(echoHandler: EchoWebSocketHandler): HandlerMapping =
        SimpleUrlHandlerMapping(
            mapOf("/lab/v1/websocket/echo" to echoHandler),
            Ordered.HIGHEST_PRECEDENCE,
        )

    /** @return WebFlux adapter that executes mapped [WebSocketHandler] values. */
    @Bean
    fun protocolLabWebSocketHandlerAdapter(): WebSocketHandlerAdapter = WebSocketHandlerAdapter()
}
