package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.CaptureIngressLimits
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Verifies streaming reservation, truncation, and canonical terminal ownership. */
class StreamingProxyCaptureSessionTest {

    @Test
    fun `capture budget truncates storage without losing observed size or terminal metadata`() = runTest {
        val root = Files.createTempDirectory("knet-streaming-capture-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(
            database = database,
            bodyStore = bodyStore,
            bodyStoreMaintenance = bodyStore,
            limits = CaptureIngressLimits(
                metadataEventsInFlight = 64,
                bodyBytesInFlight = 16L,
                perBodyStoredBytes = 4L,
                maximumChunkBytes = 4,
            ),
        ).openStreamingProxy(localListenerPort = 8080, startedAtEpochMillis = 1L)
        try {
            val connection = assertNotNull(
                session.openConnection(
                    ProxyCaptureConnectionMetadata(
                        ingress = IngressContext(IngressKind.Local),
                        downstream = TrafficEndpoint("127.0.0.1", 50_000),
                        localListener = TrafficEndpoint("127.0.0.1", 8080),
                    )
                )
            )
            val exchange = assertNotNull(
                connection.startExchange(
                    exchangeId = ExchangeId(EXCHANGE_ID),
                    request = RequestHead(
                        method = HttpMethod.fromToken("GET"),
                        target = RequestTarget.Absolute(
                            scheme = HttpScheme.fromToken("http"),
                            authority = Authority("example.test", 80),
                            pathAndQuery = "/stream",
                        ),
                        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                        headers = emptyList(),
                    ),
                    occurredAtEpochMillis = 2L,
                )
            )
            exchange.observeResponse(
                ResponseHead(
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    status = HttpStatus(200),
                    reasonPhrase = "OK",
                    headers = emptyList(),
                ),
                occurredAtEpochMillis = 3L,
            )
            val reservation = assertNotNull(
                exchange.tryReserveBody(
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    contentEncoding = null,
                    requestedBytes = 6,
                )
            )
            byteArrayOf(1, 2, 3, 4).copyInto(reservation.writableBytes)
            assertEquals(true, reservation.publish(4L))
            assertNull(
                exchange.tryReserveBody(
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    contentEncoding = null,
                    requestedBytes = 2,
                )
            )
            exchange.completeBody(TrafficDirection.SERVER_TO_CLIENT, observedBytes = 6L, occurredAtEpochMillis = 5L)
            exchange.terminate(ExchangeState.COMPLETED, ExchangeTimings(totalMillis = 3L), 5L)
            connection.close()
            session.close()

            val storedExchange = assertNotNull(database.canonicalCaptureDao().getExchange(EXCHANGE_ID))
            assertEquals("COMPLETED", storedExchange.state)
            assertEquals(200, storedExchange.responseStatusCode)
            val body = assertNotNull(database.canonicalCaptureDao().getBody(assertNotNull(storedExchange.responseBodyId)))
            assertEquals(6L, body.observedBytes)
            assertEquals(4L, body.storedBytes)
            assertEquals("TRUNCATED:4", body.outcome)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }

    private companion object {
        const val EXCHANGE_ID: String = "streaming-truncated-exchange"
    }
}
