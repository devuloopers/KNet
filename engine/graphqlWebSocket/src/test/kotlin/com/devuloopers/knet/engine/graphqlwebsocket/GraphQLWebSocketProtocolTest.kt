package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketMessageType
import com.devuloopers.knet.engine.graphqlwebsocket.session.GraphQLWebSocketProtocolException
import com.devuloopers.knet.engine.graphqlwebsocket.session.GraphQLWebSocketSessionStateMachine
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperationType
import com.devuloopers.knet.traffic.model.TrafficDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphQLWebSocketProtocolTest {
    private val parser = GraphQLWebSocketEnvelopeParser()

    @Test
    fun `strict parser accepts every modern envelope shape and rejects legacy or malformed input`() {
        val messages = listOf(
            """{"type":"connection_init","payload":{"token":"hidden"}}""",
            """{"type":"connection_ack"}""",
            subscribe("one", "LivePrices"),
            """{"id":"one","type":"next","payload":{"data":{"price":42}}}""",
            """{"id":"one","type":"error","payload":[{"message":"failed"}]}""",
            """{"id":"one","type":"complete"}""",
            """{"type":"ping","payload":{"at":1}}""",
            """{"type":"pong"}""",
        )

        assertEquals(GraphQLWebSocketMessageType.entries, messages.map { message ->
            assertNotNull(parser.parse(message.encodeToByteArray())).type
        })
        assertNull(parser.parse("""{"type":"start","id":"one"}""".encodeToByteArray()))
        assertNull(parser.parse("""{"type":"complete"}""".encodeToByteArray()))
        assertNull(parser.parse("""{"type":"connection_ack","id":"one"}""".encodeToByteArray()))
        assertNull(parser.parse("""{"type":"subscribe","id":"one","payload":{}}""".encodeToByteArray()))
        assertNull(parser.parse("not-json".encodeToByteArray()))
    }

    @Test
    fun `state machine correlates concurrent operations and permits id reuse only after completion`() {
        val machine = GraphQLWebSocketSessionStateMachine(parser, maximumActiveOperations = 2)
        machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope("""{"type":"connection_init"}"""))
        machine.accept(TrafficDirection.SERVER_TO_CLIENT, envelope("""{"type":"connection_ack"}"""))
        val first = machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope(subscribe("one", "LivePrices")))
        val second = machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope(subscribe("two", "LiveNews")))

        assertEquals("LivePrices", first.operation?.name)
        assertEquals(GraphQLOperationType.SUBSCRIPTION, first.operation?.type)
        assertEquals(listOf("one", "two"), machine.activeOperations.map { operation -> operation.id })
        assertEquals("LiveNews", second.operation?.name)
        assertFailsWith<GraphQLWebSocketProtocolException> {
            machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope(subscribe("one", "Duplicate")))
        }

        val next = machine.accept(
            TrafficDirection.SERVER_TO_CLIENT,
            envelope("""{"id":"one","type":"next","payload":{"data":{"price":42}}}"""),
        )
        assertEquals("LivePrices", next.operation?.name)
        machine.accept(TrafficDirection.SERVER_TO_CLIENT, envelope("""{"id":"one","type":"complete"}"""))
        machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope(subscribe("one", "Reused")))
        assertTrue(machine.activeOperations.any { operation -> operation.name == "Reused" })

        machine.close()
        assertTrue(machine.activeOperations.isEmpty())
        assertFailsWith<GraphQLWebSocketProtocolException> {
            machine.accept(TrafficDirection.SERVER_TO_CLIENT, envelope("""{"type":"ping"}"""))
        }
    }

    @Test
    fun `state machine rejects wrong direction and subscribe before acknowledgement`() {
        val machine = GraphQLWebSocketSessionStateMachine(parser)
        assertFailsWith<GraphQLWebSocketProtocolException> {
            machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope(subscribe("one", "TooEarly")))
        }
        assertFailsWith<GraphQLWebSocketProtocolException> {
            machine.accept(TrafficDirection.CLIENT_TO_SERVER, envelope("""{"type":"connection_ack"}"""))
        }
    }

    private fun envelope(value: String) = assertNotNull(parser.parse(value.encodeToByteArray()))

    private fun subscribe(id: String, operationName: String): String =
        """{"id":"$id","type":"subscribe","payload":{"query":"subscription $operationName { ticker }","operationName":"$operationName"}}"""
}
