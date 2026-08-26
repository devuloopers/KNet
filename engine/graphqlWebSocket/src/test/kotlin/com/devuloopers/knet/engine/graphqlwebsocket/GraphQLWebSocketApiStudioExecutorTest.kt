package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.engine.graphqlwebsocket.apistudio.GraphQLWebSocketApiStudioExecutor
import com.devuloopers.knet.engine.graphqlwebsocket.apistudio.GraphQLWebSocketRequestDraft
import com.devuloopers.knet.engine.graphqlwebsocket.apistudio.GraphQLWebSocketRequestDraftCodec
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.websocket.WebSocketApiStudioClientFactory
import com.devuloopers.knet.engine.websocket.WebSocketApiStudioExecutor
import com.devuloopers.knet.engine.websocket.WebSocketDecodeResult
import com.devuloopers.knet.engine.websocket.WebSocketFrame
import com.devuloopers.knet.engine.websocket.WebSocketFrameDecoder
import com.devuloopers.knet.engine.websocket.WebSocketOpcode
import com.devuloopers.knet.engine.websocket.WebSocketRequestDraftCodec
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GraphQLWebSocketApiStudioExecutorTest {
    @Test
    fun `executor negotiates modern subprotocol and completes one streamed subscription`() = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress("127.0.0.1", 0))
            val origin = startGraphQLOrigin(server)
            val parser = GraphQLWebSocketEnvelopeParser()
            val transportCodec = WebSocketRequestDraftCodec()
            val graphQLCodec = GraphQLWebSocketRequestDraftCodec(envelopeParser = parser)
            val executor = GraphQLWebSocketApiStudioExecutor(
                draftCodec = graphQLCodec,
                webSocketDraftCodec = transportCodec,
                webSocketExecutor = WebSocketApiStudioExecutor(
                    transportCodec,
                    WebSocketApiStudioClientFactory(byteArrayOf(1)),
                ),
                envelopeParser = parser,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
            val command = ApiStudioProtocolExecutionCommand(graphQLCodec.encode(
                id = "graphql-websocket-execution",
                name = "LivePrices",
                draft = GraphQLWebSocketRequestDraft(
                    url = "ws://127.0.0.1:${server.localPort}/graphql",
                    query = "subscription LivePrices { ticker }",
                    operationName = "LivePrices",
                    operationId = "prices-one",
                ),
            ))

            val events = withTimeout(5_000L) { executor.execute(command).take(7).toList() }

            assertEquals(GRAPHQL_TRANSPORT_WS_SUBPROTOCOL,
                assertIs<ApiStudioProtocolExecutionEvent.Started>(events.first()).negotiatedApplicationProtocol)
            val messages = events.filterIsInstance<ApiStudioProtocolExecutionEvent.Message>()
            assertEquals(
                listOf(
                    ApiStudioProtocolMessageDirection.OUTBOUND,
                    ApiStudioProtocolMessageDirection.INBOUND,
                    ApiStudioProtocolMessageDirection.OUTBOUND,
                    ApiStudioProtocolMessageDirection.INBOUND,
                    ApiStudioProtocolMessageDirection.INBOUND,
                ),
                messages.map { event -> event.message.direction },
            )
            assertTrue(messages.any { event -> event.message.displayText.contains("connection_ack") })
            assertTrue(messages.any { event -> event.message.displayText.contains("LivePrices") })
            origin.join(1_000L)
        }
    }

    @Test
    fun `executor rejects missing or wrong negotiated subprotocol before initialization`() = runBlocking {
        listOf<String?>(null, "chat").forEach { selectedProtocol ->
            ServerSocket().use { server ->
                server.bind(InetSocketAddress("127.0.0.1", 0))
                val origin = startHandshakeOnlyOrigin(server, selectedProtocol)
                val parser = GraphQLWebSocketEnvelopeParser()
                val transportCodec = WebSocketRequestDraftCodec()
                val graphQLCodec = GraphQLWebSocketRequestDraftCodec(envelopeParser = parser)
                val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val executor = GraphQLWebSocketApiStudioExecutor(
                    draftCodec = graphQLCodec,
                    webSocketDraftCodec = transportCodec,
                    webSocketExecutor = WebSocketApiStudioExecutor(
                        transportCodec,
                        WebSocketApiStudioClientFactory(byteArrayOf(1)),
                    ),
                    envelopeParser = parser,
                    scope = executionScope,
                )
                val command = ApiStudioProtocolExecutionCommand(graphQLCodec.encode(
                    id = "graphql-websocket-negotiation-failure",
                    name = "LivePrices",
                    draft = GraphQLWebSocketRequestDraft(
                        url = "ws://127.0.0.1:${server.localPort}/graphql",
                        query = "subscription LivePrices { ticker }",
                        operationName = "LivePrices",
                        operationId = "prices-one",
                    ),
                ))

                val event = withTimeout(5_000L) { executor.execute(command).take(1).toList().single() }

                assertIs<ApiStudioProtocolExecutionEvent.Failed>(event)
                executionScope.cancel()
                origin.join(1_000L)
            }
        }
    }

    private fun startGraphQLOrigin(server: ServerSocket) =
        thread(name = "knet-graphql-websocket-api-studio-test", isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val headers = readHandshakeHeaders(input)
                val accept = websocketAccept(checkNotNull(headers["sec-websocket-key"]))
                check(headers["sec-websocket-protocol"] == GRAPHQL_TRANSPORT_WS_SUBPROTOCOL)
                socket.getOutputStream().apply {
                    write((
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n" +
                            "Sec-WebSocket-Protocol: $GRAPHQL_TRANSPORT_WS_SUBPROTOCOL\r\n\r\n"
                        ).encodeToByteArray())
                    flush()
                }
                val init = decodeSingleFrame(readFrameWire(input), expectsMask = true)
                check(init.payload.decodeToString().contains("connection_init"))
                writeText(socket.getOutputStream(), """{"type":"connection_ack"}""")
                val subscribe = decodeSingleFrame(readFrameWire(input), expectsMask = true)
                check(subscribe.payload.decodeToString().contains("LivePrices"))
                writeText(
                    socket.getOutputStream(),
                    """{"id":"prices-one","type":"next","payload":{"data":{"ticker":"KNET"}}}""",
                )
                writeText(socket.getOutputStream(), """{"id":"prices-one","type":"complete"}""")
                val close = decodeSingleFrame(readFrameWire(input), expectsMask = true)
                socket.getOutputStream().apply {
                    write(WebSocketFrameDecoder.encode(WebSocketOpcode.CLOSE, close.payload))
                    flush()
                }
            }
        }

    private fun startHandshakeOnlyOrigin(server: ServerSocket, selectedProtocol: String?) =
        thread(name = "knet-graphql-websocket-negotiation-test", isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val headers = readHandshakeHeaders(input)
                val accept = websocketAccept(checkNotNull(headers["sec-websocket-key"]))
                check(headers["sec-websocket-protocol"] == GRAPHQL_TRANSPORT_WS_SUBPROTOCOL)
                val protocolHeader = selectedProtocol?.let { protocol ->
                    "Sec-WebSocket-Protocol: $protocol\r\n"
                }.orEmpty()
                socket.getOutputStream().apply {
                    write((
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Sec-WebSocket-Accept: $accept\r\n" +
                            protocolHeader +
                            "\r\n"
                        ).encodeToByteArray())
                    flush()
                }
            }
        }

    private fun writeText(output: java.io.OutputStream, text: String) {
        output.write(WebSocketFrameDecoder.encode(WebSocketOpcode.TEXT, text.encodeToByteArray()))
        output.flush()
    }

    private fun readHandshakeHeaders(input: InputStream): Map<String, String> {
        readAsciiLine(input)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) return headers
            val separator = line.indexOf(':')
            require(separator > 0) { "Malformed WebSocket handshake header." }
            headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
    }

    private fun readFrameWire(input: InputStream): ByteArray {
        val first = input.read()
        val second = input.read()
        require(first >= 0 && second >= 0) { "WebSocket frame ended before its base header." }
        val shortLength = second and 0x7f
        val lengthBytes = when (shortLength) {
            126 -> 2
            127 -> 8
            else -> 0
        }
        val extension = input.readExact(lengthBytes)
        val length = when (shortLength) {
            126 -> ((extension[0].toInt() and 0xff) shl 8) or (extension[1].toInt() and 0xff)
            127 -> extension.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xffL) }
                .also { require(it <= Int.MAX_VALUE) { "Test frame is too large." } }
                .toInt()
            else -> shortLength
        }
        val maskBytes = if (second and 0x80 != 0) 4 else 0
        return byteArrayOf(first.toByte(), second.toByte()) + extension + input.readExact(maskBytes + length)
    }

    private fun decodeSingleFrame(wire: ByteArray, expectsMask: Boolean): WebSocketFrame =
        assertIs<WebSocketDecodeResult.Frames>(
            WebSocketFrameDecoder(expectsMask, permitsCompression = false).accept(wire),
        ).values.single()

    private fun InputStream.readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            require(read >= 0) { "Connection ended before the requested WebSocket bytes arrived." }
            offset += read
        }
        return bytes
    }

    private fun readAsciiLine(input: InputStream): String {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val value = input.read()
            require(value >= 0) { "Connection ended before a complete HTTP line." }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().decodeToString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun websocketAccept(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).encodeToByteArray())
        return Base64.encode(digest)
    }

    private companion object {
        const val WEBSOCKET_GUID: String = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
