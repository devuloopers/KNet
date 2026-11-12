package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Optional semantic history remains bounded and cannot compromise the selected message preview. */
class ProtocolMessagePreviewTest {
    private val exchange = HttpExchangeSnapshot(ExchangeId("exchange"), connectionId = ConnectionId("connection"),
        request = HttpRequestSnapshot(RequestHead(HttpMethod.GET,
            RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("example.test"), "/ws"),
            ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())), state = ExchangeState.COMPLETED, startedAtEpochMillis = 1)
    private fun message(id: String) = ProtocolMessageSnapshot(ProtocolMessageId(id), ConnectionId("connection"), exchange.id, null,
        MessageProtocolId.WEBSOCKET, ProtocolMessageKind.TEXT, TrafficDirection.CLIENT_TO_SERVER, 1, 1, null, 3, false, null,
        MessageBodyRef.Available(BodyRef(BodyId(id), 3, 3, outcome = BodyCaptureOutcome.Complete)), ProtocolMessageState.COMPLETE)
    private class Decoder : ProtocolMessagePayloadDecoder {
        override val decoderId = ProtocolMessagePayloadDecoderId("test")
        override val protocolId = MessageProtocolId.WEBSOCKET
        override val priority = 1
        var input: ProtocolMessagePayloadInput? = null
        override fun decode(input: ProtocolMessagePayloadInput): ProtocolMessagePresentation {
            this.input = input
            return ProtocolMessagePresentation("Selected", "text/plain", input.payload.decodeToString())
        }
        override fun contextQuery(input: ProtocolMessagePayloadInput) = ProtocolMessageContextQuery(
            input.message.exchangeId, input.message.id, protocolId, null, ProtocolMessageKind.TEXT)
    }
    private class Port(val history: List<ProtocolMessageSnapshot>) : ProtocolMessageQuery {
        val reads = mutableListOf<Pair<BodyId, BodyRange>>()
        var throwContext: Exception? = null
        var oversized = false
        var fullContext = false
        override fun observeChanges(exchangeId: ExchangeId) = emptyFlow<Long>()
        override suspend fun queryMessages(query: ProtocolMessagePageQuery) = ProtocolMessagePage(emptyList(), null, 0)
        override suspend fun queryContext(query: ProtocolMessageContextQuery): List<ProtocolMessageSnapshot> {
            throwContext?.let { throw it }; return history
        }
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
            reads += bodyId to range
            return if (fullContext && bodyId.value != "selected") BodyChunk(ByteArray(range.length), 0, true)
                else if (oversized && bodyId.value != "selected") BodyChunk(ByteArray(range.length), 0, false)
                else BodyChunk("raw".encodeToByteArray(), 0, true)
        }
    }

    @Test fun `context is lazy bounded and preserves durable provider order`() = runTest {
        val decoder = Decoder(); val port = Port(listOf(message("newer"), message("older")))
        val useCase = LoadProtocolMessageBodyUseCase(port, ProtocolMessagePresentationRegistry(listOf(decoder)))
        val result = assertIs<LoadProtocolMessageBodyResult.Available>(useCase.execute(message("selected"), exchange))
        assertEquals("raw", result.presentation!!.text)
        assertEquals(listOf("newer", "older"), decoder.input!!.context.map { it.message.id.value })
        assertEquals(listOf(1_048_576, 65_536, 65_536), port.reads.map { it.second.length })
        assertFalse(result.truncated)
    }

    @Test fun `context gap stops further reads and large body is an explicit truncated gap`() = runTest {
        val decoder = Decoder(); val port = Port(List(150) { message("history-$it") }).apply { oversized = true }
        LoadProtocolMessageBodyUseCase(port, ProtocolMessagePresentationRegistry(listOf(decoder))).execute(message("selected"), exchange)
        assertEquals(2, port.reads.size)
        assertEquals(1, decoder.input!!.context.size)
        assertTrue(decoder.input!!.context.single().truncated)
        assertEquals(65_536, decoder.input!!.context.single().payload()!!.size)
    }

    @Test fun `context unavailable and foreign parent do not destroy or mislabel raw selected bytes`() = runTest {
        val decoder = Decoder(); val port = Port(emptyList()).apply { throwContext = IllegalStateException("deleted") }
        val useCase = LoadProtocolMessageBodyUseCase(port, ProtocolMessagePresentationRegistry(listOf(decoder)))
        assertIs<LoadProtocolMessageBodyResult.Available>(useCase.execute(message("selected"), exchange))
        assertTrue(decoder.input!!.context.isEmpty())
        decoder.input = null
        val foreign = assertIs<LoadProtocolMessageBodyResult.Available>(useCase.execute(message("selected"), exchange.copy(id = ExchangeId("other"))))
        assertNull(foreign.presentation); assertNull(decoder.input)
    }

    @Test fun `foreign history stops correlation and selected capture truncation is retained`() = runTest {
        val decoder = Decoder(); val port = Port(listOf(message("foreign").copy(exchangeId = ExchangeId("other")), message("older")))
        val selected = message("selected").copy(body = MessageBodyRef.Available(BodyRef(BodyId("selected"), 10, 3,
            outcome = BodyCaptureOutcome.Truncated(3))))
        val result = assertIs<LoadProtocolMessageBodyResult.Available>(LoadProtocolMessageBodyUseCase(port,
            ProtocolMessagePresentationRegistry(listOf(decoder))).execute(selected, exchange))
        assertTrue(result.truncated); assertTrue(decoder.input!!.truncated)
        assertTrue(decoder.input!!.context.isEmpty())
        assertEquals(1, port.reads.size)
    }

    @Test fun `aggregate historical bytes stop at one MiB and excessive direct context is rejected`() = runTest {
        val decoder = Decoder()
        val port = Port(List(128) { message("history-$it") }).apply { fullContext = true }
        LoadProtocolMessageBodyUseCase(port, ProtocolMessagePresentationRegistry(listOf(decoder)))
            .execute(message("selected"), exchange)
        val context = decoder.input!!.context
        assertEquals(1_048_576, context.sumOf { it.retainedBytes })
        assertEquals(17, port.reads.size)
        assertTrue(context.last().truncated)
        val entries = List(17) { ProtocolMessageContextEntry(message("direct-$it"), ByteArray(65_536), false) }
        assertFailsWith<IllegalArgumentException> { ProtocolMessagePayloadInput(exchange, message("selected"), byteArrayOf(), entries) }
    }

    @Test fun `optional context propagates coroutine cancellation`() = runTest {
        val port = Port(emptyList()).apply { throwContext = CancellationException("selection changed") }
        assertFailsWith<CancellationException> { LoadProtocolMessageBodyUseCase(port,
            ProtocolMessagePresentationRegistry(listOf(Decoder()))).execute(message("selected"), exchange) }
    }
}
