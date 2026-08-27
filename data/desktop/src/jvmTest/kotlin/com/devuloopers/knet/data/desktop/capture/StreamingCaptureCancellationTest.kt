package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.CaptureIngressLimits
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Verifies transport cancellations remain typed in durable body metadata. */
class StreamingCaptureCancellationTest {

    @Test
    fun `cancelled request body is not persisted as complete`() = runTest {
        val root = Files.createTempDirectory("knet-streaming-cancel-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(
            database = database,
            bodyStore = bodyStore,
            bodyStoreMaintenance = bodyStore,
            limits = CaptureIngressLimits(64, 1_024L, 1_024L, 1_024),
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
                        method = HttpMethod.fromToken("POST"),
                        target = RequestTarget.Absolute(
                            HttpScheme.fromToken("http"),
                            Authority("example.test", 80),
                            "/upload",
                        ),
                        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                        headers = emptyList(),
                    ),
                    occurredAtEpochMillis = 2L,
                )
            )
            val reservation = assertNotNull(
                exchange.tryReserveBody(TrafficDirection.CLIENT_TO_SERVER, null, 3)
            )
            byteArrayOf(1, 2, 3).copyInto(reservation.writableBytes)
            assertEquals(true, reservation.publish(3L))
            exchange.cancelBody(
                direction = TrafficDirection.CLIENT_TO_SERVER,
                observedBytes = 3L,
                occurredAtEpochMillis = 4L,
                reason = TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED,
            )
            exchange.terminate(
                outcome = ExchangeTerminalOutcome.Cancelled(
                    TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED,
                ),
                timings = ExchangeTimings(totalMillis = 2L),
                occurredAtEpochMillis = 4L,
            )
            connection.close(TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED)
            session.close()

            val storedExchange = assertNotNull(database.canonicalCaptureDao().getExchange(EXCHANGE_ID))
            assertEquals("CANCELLED", storedExchange.state)
            val body = assertNotNull(
                database.canonicalCaptureDao().getBody(assertNotNull(storedExchange.requestBodyId))
            )
            assertEquals("FAILED:downstream_cancelled", body.outcome)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }

    private companion object {
        const val EXCHANGE_ID: String = "streaming-cancelled-exchange"
    }
}
