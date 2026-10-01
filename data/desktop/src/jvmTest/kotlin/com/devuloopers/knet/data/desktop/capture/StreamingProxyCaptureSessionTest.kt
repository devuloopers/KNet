package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.CaptureIngressLimits
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCaptureMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueSecurityProtocol
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueTransportProtocol
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaquePolicyAction
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.AppliedNetworkCondition
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
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
import kotlin.test.assertIs

/** Verifies streaming reservation, truncation, and canonical terminal ownership. */
class StreamingProxyCaptureSessionTest {

    @Test
    fun `opaque flow persists metadata counters and exactly one terminal outcome`() = runTest {
        val root = Files.createTempDirectory("knet-opaque-flow-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8080, startedAtEpochMillis = 1L)
        try {
            val connection = assertNotNull(
                session.openConnection(
                    ProxyCaptureConnectionMetadata(
                        ingress = IngressContext(IngressKind.Local),
                        downstream = TrafficEndpoint("127.0.0.1", 50_000),
                        localListener = TrafficEndpoint("127.0.0.1", 8080),
                    ),
                ),
            )
            val flow = assertNotNull(
                connection.startOpaqueFlow(
                    ProxyOpaqueFlowCaptureMetadata(
                        destination = TrafficEndpoint("store.example", 443),
                        serverName = "store.example",
                        transport = ProxyOpaqueTransportProtocol.TCP,
                        security = ProxyOpaqueSecurityProtocol.TLS,
                        policyRuleId = "pinned-store",
                        policyAction = ProxyOpaquePolicyAction.TUNNEL,
                        policyGroupId = "store-compatibility",
                        sourceApplicationId = "com.example.store",
                        appliedNetworkCondition = AppliedNetworkCondition(
                            profileId = "streaming-100-kbps",
                            ruleId = "store-condition",
                            source = AppliedNetworkConditionSource.DOMAIN_RULE,
                        ),
                        occurredAtEpochMillis = 2L,
                    ),
                ),
            )
            flow.observeBytes(TrafficDirection.CLIENT_TO_SERVER, 120, 3L)
            flow.observeBytes(TrafficDirection.SERVER_TO_CLIENT, 450, 4L)
            flow.terminate(ExchangeTerminalOutcome.Completed, 5L)
            flow.terminate(
                ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_IO_FAILED),
                6L,
            )
            connection.close()
            session.flush()

            val stored = database.canonicalCaptureDao().getOpaqueFlows(session.sessionId.value, 10).single()
            assertEquals("COMPLETED", stored.state)
            assertEquals("store.example", stored.destinationHost)
            assertEquals("com.example.store", stored.sourceApplicationId)
            assertEquals("pinned-store", stored.policyRuleId)
            assertEquals("TUNNEL", stored.policyAction)
            assertEquals("store-compatibility", stored.policyGroupId)
            assertEquals("streaming-100-kbps", stored.appliedConditionProfileId)
            assertEquals("store-condition", stored.appliedConditionRuleId)
            assertEquals(120L, stored.uploadedBytes)
            assertEquals(450L, stored.downloadedBytes)
            assertNull(stored.terminalErrorCode)
            val snapshot = CanonicalCaptureEntityMapper.opaqueFlowSnapshot(stored)
            assertIs<ExchangeTerminalOutcome.Completed>(snapshot.terminalOutcome)
            assertEquals("streaming-100-kbps", snapshot.appliedNetworkCondition?.profileId)
            assertEquals("TUNNEL", snapshot.policyAction?.name)
            assertEquals("store-compatibility", snapshot.policyGroupId)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `session shutdown propagates its typed reason to unfinished exchanges`() = runTest {
        val root = Files.createTempDirectory("knet-streaming-shutdown-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8080, startedAtEpochMillis = 1L)
        try {
            val connection = assertNotNull(
                session.openConnection(
                    ProxyCaptureConnectionMetadata(
                        ingress = IngressContext(IngressKind.Local),
                        downstream = TrafficEndpoint("127.0.0.1", 50_000),
                        localListener = TrafficEndpoint("127.0.0.1", 8080),
                    ),
                ),
            )
            connection.startExchange(
                exchangeId = ExchangeId(SHUTDOWN_EXCHANGE_ID),
                request = RequestHead(
                    method = HttpMethod.fromToken("GET"),
                    target = RequestTarget.Absolute(
                        scheme = HttpScheme.fromToken("http"),
                        authority = Authority("example.test", 80),
                        pathAndQuery = "/unfinished",
                    ),
                    protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                    headers = emptyList(),
                ),
                occurredAtEpochMillis = 2L,
            )

            session.close(TrafficTerminationReason.Lifecycle.PROXY_STOPPED)

            val stored = assertNotNull(database.canonicalCaptureDao().getExchange(SHUTDOWN_EXCHANGE_ID))
            assertEquals("CANCELLED", stored.state)
            assertEquals("proxy-stopped", stored.terminalErrorCode)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }

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
                    appliedNetworkCondition = AppliedNetworkCondition(
                        profileId = "streaming-100-kbps",
                        ruleId = null,
                        source = AppliedNetworkConditionSource.GLOBAL,
                    ),
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
            exchange.terminate(ExchangeTerminalOutcome.Completed, ExchangeTimings(totalMillis = 3L), 5L)
            connection.close()
            session.close()

            val storedExchange = assertNotNull(database.canonicalCaptureDao().getExchange(EXCHANGE_ID))
            assertEquals("COMPLETED", storedExchange.state)
            assertEquals(200, storedExchange.responseStatusCode)
            assertEquals("streaming-100-kbps", storedExchange.appliedConditionProfileId)
            assertNull(storedExchange.appliedConditionRuleId)
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
        const val SHUTDOWN_EXCHANGE_ID: String = "streaming-shutdown-exchange"
    }
}
