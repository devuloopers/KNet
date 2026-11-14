package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwitchableProxyCaptureSinkTest {
    @Test fun `source pause cuts only its handles and keep-alive resume retains the same writer`() {
        val first = com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(com.devuloopers.knet.identity.RegisteredDeviceId("first"))
        val second = com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(com.devuloopers.knet.identity.RegisteredDeviceId("second"))
        val writer = RecordingSink(com.devuloopers.knet.traffic.id.CaptureSessionId("one-writer"))
        val policy = com.devuloopers.knet.application.contract.traffic.CaptureAdmissionPolicy(false, mapOf(first to true, second to true))
        val sink = SwitchableProxyCaptureSink(writer, policy)
        fun metadata(identity: String) = connectionMetadata().copy(ingress = IngressContext(IngressKind.LanPairedDevice,
            clientIdentity = com.devuloopers.knet.traffic.model.ClientIdentity(identity)))
        val firstConnection = sink.openConnection(metadata("first"))
        val secondConnection = sink.openConnection(metadata("second"))
        // A local caller copying a device's name/identity string never becomes that authenticated device.
        val unverified = sink.openConnection(metadata("first").copy(ingress = IngressContext(IngressKind.Local,
            clientIdentity = com.devuloopers.knet.traffic.model.ClientIdentity("first"))))
        assertNull(unverified.startExchange(ExchangeId("spoofed"), request(), 1))
        assertNotNull(firstConnection.startExchange(ExchangeId("first-before"), request(), 1))
        assertNotNull(secondConnection.startExchange(ExchangeId("second-before"), request(), 1))

        sink.setAdmission(policy.copy(overrides = policy.overrides + (first to false)))
        assertEquals("capture_paused", writer.connections[0].closeErrorCode)
        assertEquals("not-closed", writer.connections[1].closeErrorCode)
        assertNull(firstConnection.sessionId)
        assertNull(firstConnection.startExchange(ExchangeId("first-paused"), request(), 2))
        assertNotNull(secondConnection.startExchange(ExchangeId("second-after"), request(), 2))
        assertEquals(2, writer.connections.size)

        sink.setAdmission(policy)
        assertNotNull(firstConnection.startExchange(ExchangeId("first-resumed"), request(), 3))
        assertEquals(com.devuloopers.knet.traffic.id.CaptureSessionId("one-writer"), firstConnection.sessionId)
        assertEquals(listOf("first-before", "second-before", "second-after", "first-resumed"), writer.exchangeIds)
        assertEquals(3, writer.connections.size)
        firstConnection.close(); secondConnection.close(); unverified.close()
    }

    @Test fun `writer rotation does not re-enable a paused source or newly connected peer`() {
        val first = com.devuloopers.knet.domain.policy.PolicyWorkspace.Device(com.devuloopers.knet.identity.RegisteredDeviceId("first"))
        val initial = RecordingSink()
        val replacement = RecordingSink()
        val sink = SwitchableProxyCaptureSink(initial,
            com.devuloopers.knet.application.contract.traffic.CaptureAdmissionPolicy(false, mapOf(first to true)))
        val metadata = connectionMetadata().copy(ingress = IngressContext(IngressKind.LanPairedDevice,
            clientIdentity = com.devuloopers.knet.traffic.model.ClientIdentity("first")))
        val connection = sink.openConnection(metadata)
        assertNotNull(connection.startExchange(ExchangeId("before"), request(), 1))
        sink.setAdmission(com.devuloopers.knet.application.contract.traffic.CaptureAdmissionPolicy(false))
        sink.replaceTarget(replacement)
        assertNull(connection.startExchange(ExchangeId("after"), request(), 2))
        val newcomer = sink.openConnection(metadata.copy(ingress = metadata.ingress.copy(
            clientIdentity = com.devuloopers.knet.traffic.model.ClientIdentity("new-device"))))
        assertNull(newcomer.startExchange(ExchangeId("new-device"), request(), 3))
        assertTrue(replacement.connections.isEmpty())
        connection.close(); newcomer.close()
    }

    @Test fun `TLS learning context follows replacement capture and disappears while paused or closed`() {
        val firstId = com.devuloopers.knet.traffic.id.CaptureSessionId("first")
        val secondId = com.devuloopers.knet.traffic.id.CaptureSessionId("second")
        val switchable = SwitchableProxyCaptureSink(RecordingSink(firstId))
        val connection = assertNotNull(switchable.openConnection(connectionMetadata()))
        assertEquals(firstId, connection.sessionId)
        switchable.pause(); assertNull(connection.sessionId)
        switchable.replaceTarget(RecordingSink(secondId)); assertEquals(secondId, connection.sessionId)
        connection.close(); assertNull(connection.sessionId)
    }

    @Test
    fun `existing transport connection uses replacement capture target after clear`() {
        val first = RecordingSink()
        val second = RecordingSink()
        val switchable = SwitchableProxyCaptureSink(first)
        val connection = assertNotNull(switchable.openConnection(connectionMetadata()))

        assertNotNull(connection.startExchange(ExchangeId("before-clear"), request(), 1_000L))
        switchable.replaceTarget(second)
        assertNotNull(connection.startExchange(ExchangeId("after-clear"), request(), 2_000L))

        assertEquals(listOf("before-clear"), first.exchangeIds)
        assertEquals("capture_target_rotated", first.connections.single().closeErrorCode)
        assertEquals(listOf("after-clear"), second.exchangeIds)

        connection.close()
        assertEquals(null, second.connections.single().closeErrorCode)
    }

    @Test
    fun `connection accepted while paused captures its next exchange after resume`() {
        val first = RecordingSink()
        val resumed = RecordingSink()
        val switchable = SwitchableProxyCaptureSink(first)
        switchable.pause()
        val connection = assertNotNull(switchable.openConnection(connectionMetadata()))

        assertNull(connection.startExchange(ExchangeId("while-paused"), request(), 1_000L))
        switchable.replaceTarget(resumed)
        assertNotNull(connection.startExchange(ExchangeId("after-resume"), request(), 2_000L))

        assertEquals(emptyList(), first.exchangeIds)
        assertEquals(listOf("after-resume"), resumed.exchangeIds)
    }

    @Test
    fun `exchange begun before cutover remains owned by its original generation`() {
        val first = RecordingSink()
        val second = RecordingSink()
        val switchable = SwitchableProxyCaptureSink(first)
        val connection = assertNotNull(switchable.openConnection(connectionMetadata()))
        val originalExchange = assertNotNull(
            connection.startExchange(ExchangeId("original"), request(), 1_000L),
        )

        switchable.replaceTarget(second)
        originalExchange.observeResponse(response(), 1_100L)
        assertNotNull(connection.startExchange(ExchangeId("next"), request(), 2_000L))

        assertEquals(listOf("original"), first.exchangeIds)
        assertEquals(listOf("original"), first.responseExchangeIds)
        assertEquals(listOf("next"), second.exchangeIds)
    }

    private class RecordingSink(private val sessionId: com.devuloopers.knet.traffic.id.CaptureSessionId? = null) : ProxyCaptureSink {
        val connections = mutableListOf<RecordingConnection>()
        val exchangeIds = mutableListOf<String>()
        val responseExchangeIds = mutableListOf<String>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            RecordingConnection(sessionId, exchangeIds, responseExchangeIds).also(connections::add)
    }

    private class RecordingConnection(
        override val sessionId: com.devuloopers.knet.traffic.id.CaptureSessionId?,
        private val exchangeIds: MutableList<String>,
        private val responseExchangeIds: MutableList<String>,
    ) : ProxyConnectionCapture {
        var closeErrorCode: String? = NOT_CLOSED

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
            streamId: com.devuloopers.knet.traffic.id.StreamId?,
        ): ProxyExchangeCapture {
            exchangeIds += exchangeId.value
            return RecordingExchange(exchangeId, responseExchangeIds)
        }

        override fun close(reason: TrafficTerminationReason?) {
            closeErrorCode = reason?.code?.value
        }

        private companion object {
            const val NOT_CLOSED: String = "not-closed"
        }
    }

    private class RecordingExchange(
        override val exchangeId: ExchangeId,
        private val responseExchangeIds: MutableList<String>,
    ) : ProxyExchangeCapture {
        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            reason: TrafficTerminationReason,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
            responseExchangeIds += exchangeId.value
        }

        override fun terminate(
            outcome: ExchangeTerminalOutcome,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
        ) = Unit
    }

    private fun connectionMetadata(): ProxyCaptureConnectionMetadata = ProxyCaptureConnectionMetadata(
        ingress = IngressContext(IngressKind.Local),
        downstream = TrafficEndpoint("127.0.0.1", 50_000),
        localListener = TrafficEndpoint("127.0.0.1", 8_080),
    )

    private fun request(): RequestHead = RequestHead(
        method = HttpMethod.fromToken("GET"),
        target = RequestTarget.Absolute(
            scheme = HttpScheme.fromToken("http"),
            authority = Authority("example.test", 80),
            pathAndQuery = "/",
        ),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        headers = emptyList(),
    )

    private fun response(): ResponseHead = ResponseHead(
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        status = com.devuloopers.knet.traffic.model.http.HttpStatus(200),
        reasonPhrase = "OK",
        headers = emptyList(),
    )
}
