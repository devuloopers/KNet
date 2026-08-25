package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOutboundMessage
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WebSocketApiStudioExecutorTest {
    @Test
    fun `request draft codec round trips websocket specific fields`() {
        val codec = WebSocketRequestDraftCodec()
        val draft = WebSocketRequestDraft(
            url = "wss://example.test/chat",
            subprotocols = listOf("chat"),
            headers = listOf(WebSocketHandshakeHeader("authorization", "Bearer token")),
            connectTimeoutMillis = 2_500L,
            outboundMessages = listOf(WebSocketAuthoredMessage(WebSocketAuthoredMessageKind.TEXT, "hello")),
        )

        val restored = codec.decode(codec.encode("websocket-draft", "Chat", draft)).getOrThrow()

        assertEquals(draft, restored)
    }

    @Test
    fun `request draft rejects generated handshake headers and invalid subprotocol tokens`() {
        assertFailsWith<IllegalArgumentException> {
            WebSocketHandshakeHeader("Sec-WebSocket-Key", "overridden")
        }
        assertFailsWith<IllegalArgumentException> {
            WebSocketRequestDraft("ws://example.test/socket", subprotocols = listOf("not valid"))
        }
    }

    @Test
    fun `one shot executor sends authored messages and closes normally`() = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress("127.0.0.1", 0))
            val origin = startEchoOrigin(server)
            val codec = WebSocketRequestDraftCodec()
            val executor = WebSocketApiStudioExecutor(
                codec,
                WebSocketApiStudioClientFactory(byteArrayOf(1)),
            )
            val command = ApiStudioProtocolExecutionCommand(
                codec.encode(
                    id = "websocket-one-shot",
                    name = "Echo",
                    draft = WebSocketRequestDraft(
                        url = "ws://127.0.0.1:${server.localPort}/echo",
                        outboundMessages = listOf(
                            WebSocketAuthoredMessage(WebSocketAuthoredMessageKind.TEXT, "hello"),
                        ),
                    ),
                ),
            )

            val events = withTimeout(5_000L) { executor.execute(command).take(4).toList() }

            assertIs<ApiStudioProtocolExecutionEvent.Started>(events[0])
            assertEquals(
                ApiStudioProtocolMessageDirection.OUTBOUND,
                assertIs<ApiStudioProtocolExecutionEvent.Message>(events[1]).message.direction,
            )
            assertEquals(
                ApiStudioProtocolMessageDirection.INBOUND,
                assertIs<ApiStudioProtocolExecutionEvent.Message>(events[2]).message.direction,
            )
            assertIs<ApiStudioProtocolExecutionEvent.Completed>(events[3])
            origin.join(1_000L)
        }
    }

    @Test
    fun `interactive executor connects sends receives and closes with typed events`() = runBlocking {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress("127.0.0.1", 0))
            val origin = startEchoOrigin(server)
            val codec = WebSocketRequestDraftCodec()
            val executor = WebSocketApiStudioExecutor(
                codec,
                WebSocketApiStudioClientFactory(byteArrayOf(1)),
            )
            val document = codec.encode(
                id = "websocket-live",
                name = "Echo",
                draft = WebSocketRequestDraft("ws://127.0.0.1:${server.localPort}/echo"),
            )
            val session = executor.open(ApiStudioProtocolExecutionCommand(document)).getOrThrow()

            val events = coroutineScope {
                val collected = async {
                    withTimeout(5_000L) { session.events.take(4).toList() }
                }
                session.send(ApiStudioProtocolOutboundMessage("hello", "text/plain")).getOrThrow()
                session.halfClose().getOrThrow()
                collected.await()
            }

            assertIs<ApiStudioProtocolExecutionEvent.Started>(events[0])
            val outbound = assertIs<ApiStudioProtocolExecutionEvent.Message>(events[1]).message
            assertEquals(ApiStudioProtocolMessageDirection.OUTBOUND, outbound.direction)
            assertEquals("hello", outbound.displayText)
            val inbound = assertIs<ApiStudioProtocolExecutionEvent.Message>(events[2]).message
            assertEquals(ApiStudioProtocolMessageDirection.INBOUND, inbound.direction)
            assertEquals("hello", inbound.displayText)
            val completed = assertIs<ApiStudioProtocolExecutionEvent.Completed>(events[3])
            assertEquals("1000", completed.statusCode)
            assertEquals("WebSocket", completed.actualProtocol)
            origin.join(1_000L)
        }
    }

    private fun startEchoOrigin(server: ServerSocket) =
        thread(name = "knet-websocket-api-studio-test", isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val headers = readHandshakeHeaders(input)
                val accept = websocketAccept(checkNotNull(headers["sec-websocket-key"]))
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 101 Switching Protocols\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Upgrade: websocket\r\n" +
                                "Sec-WebSocket-Accept: $accept\r\n\r\n"
                            ).encodeToByteArray(),
                    )
                    flush()
                }
                val outbound = decodeSingleFrame(readFrameWire(input), expectsMask = true)
                socket.getOutputStream().apply {
                    write(WebSocketFrameDecoder.encode(WebSocketOpcode.TEXT, outbound.payload))
                    flush()
                }
                val close = decodeSingleFrame(readFrameWire(input), expectsMask = true)
                socket.getOutputStream().apply {
                    write(WebSocketFrameDecoder.encode(WebSocketOpcode.CLOSE, close.payload))
                    flush()
                }
            }
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

    private fun decodeSingleFrame(wire: ByteArray, expectsMask: Boolean): WebSocketFrame {
        val frames = assertIs<WebSocketDecodeResult.Frames>(
            WebSocketFrameDecoder(expectsMask, permitsCompression = false).accept(wire),
        ).values
        return frames.single()
    }

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
        return bytes.toByteArray().toString(Charsets.US_ASCII)
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
