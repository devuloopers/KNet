package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.traffic.id.BodyId
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PrepareTrafficRequestUseCaseTest {
    @Test
    fun `whole request body is loaded through bounded one mebibyte ranges`() = runTest {
        val body = ByteArray(1_048_576 + 17) { (it % 251).toByte() }
        val port = FakePort(body)

        val found = assertIs<PrepareTrafficRequestResult.Found>(
            PrepareTrafficRequestUseCase(port).execute(ExchangeId("exchange"), body.size),
        )

        assertEquals(listOf(1_048_576, 17), port.ranges.map { it.length })
        val rebuilt = found.value.bodyChunks.flatMap { it.copyBytes().asList() }.toByteArray()
        assertContentEquals(body, rebuilt)
    }

    @Test
    fun `request larger than explicit export budget is rejected without a body read`() = runTest {
        val port = FakePort(ByteArray(32))

        val result = PrepareTrafficRequestUseCase(port).execute(ExchangeId("exchange"), 16)

        assertIs<PrepareTrafficRequestResult.BodyTooLarge>(result)
        assertEquals(emptyList(), port.ranges)
    }

    private class FakePort(private val body: ByteArray) : TrafficQueryPort {
        private val bodyId = BodyId("body")
        val ranges = mutableListOf<BodyRange>()
        override val generations: Flow<TrafficGeneration> = emptyFlow()

        override suspend fun query(query: TrafficPageQuery): TrafficPage =
            TrafficPage(items = emptyList(), nextCursor = null, totalCount = 0L, generation = 0L)

        override suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot? =
            HttpExchangeSnapshot(
                id = ExchangeId("exchange"),
                request = HttpRequestSnapshot(
                    head = RequestHead(
                        method = HttpMethod.fromToken("POST"),
                        target = RequestTarget.Origin("/"),
                        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                        headers = emptyList(),
                    ),
                    body = MessageBodyRef.Available(
                        BodyRef(
                            id = bodyId,
                            observedBytes = body.size.toLong(),
                            storedBytes = body.size.toLong(),
                            outcome = BodyCaptureOutcome.Complete,
                        ),
                    ),
                ),
                state = ExchangeState.COMPLETED,
                startedAtEpochMillis = 1L,
            ).takeIf { it.id == exchangeId }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk {
            ranges += range
            val start = range.offset.toInt()
            val end = minOf(body.size, start + range.length)
            return BodyChunk(body.copyOfRange(start, end), range.offset, end == body.size)
        }
    }
}
