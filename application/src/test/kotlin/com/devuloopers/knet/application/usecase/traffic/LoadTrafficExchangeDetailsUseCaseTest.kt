package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class LoadTrafficExchangeDetailsUseCaseTest {
    @Test
    fun `loads only the configured bounded preview`() = runTest {
        val bodyId = BodyId("body-1")
        val exchange = exchangeWithBody(bodyId)
        val port = FakeTrafficQueryPort(exchange, byteArrayOf(1, 2, 3, 4))
        val useCase = LoadTrafficExchangeDetailsUseCase(port)

        val result = useCase.execute(exchange.id, BodyPreviewLimit(3))

        val found = assertIs<LoadTrafficExchangeDetailsResult.Found>(result)
        val preview = assertIs<TrafficBodyPreview.Available>(found.details.requestBody)
        assertEquals(listOf<Byte>(1, 2, 3), preview.chunk.copyBytes().toList())
        assertFalse(preview.chunk.endOfBody)
        assertEquals(3, port.requestedRange?.length)
    }

    private fun exchangeWithBody(bodyId: BodyId): HttpExchangeSnapshot = HttpExchangeSnapshot(
        id = ExchangeId("exchange-1"),
        request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.Standard(StandardHttpMethod.GET),
                target = RequestTarget.Origin("/"),
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                headers = emptyList(),
            ),
            body = MessageBodyRef.Available(
                BodyRef(
                    id = bodyId,
                    observedBytes = 4L,
                    storedBytes = 4L,
                    outcome = BodyCaptureOutcome.Complete,
                ),
            ),
        ),
        state = ExchangeState.COMPLETED,
        startedAtEpochMillis = 1L,
    )

    private class FakeTrafficQueryPort(
        private val exchange: HttpExchangeSnapshot,
        private val body: ByteArray,
    ) : TrafficQueryPort {
        override val generations: Flow<TrafficGeneration> = emptyFlow()
        var requestedRange: BodyRange? = null

        override suspend fun query(query: TrafficPageQuery): TrafficPage = TrafficPage(
            items = emptyList(),
            nextCursor = null,
            generation = 0L,
        )

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            exchange.takeIf { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
            requestedRange = range
            val from = range.offset.toInt()
            val to = minOf(body.size, from + range.length)
            return BodyChunk(
                bytes = body.copyOfRange(from, to),
                offset = range.offset,
                endOfBody = to == body.size,
            )
        }
    }
}
