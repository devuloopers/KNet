package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
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

class SwitchableProxyCaptureSinkTest {
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

    private class RecordingSink : ProxyCaptureSink {
        val connections = mutableListOf<RecordingConnection>()
        val exchangeIds = mutableListOf<String>()
        val responseExchangeIds = mutableListOf<String>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            RecordingConnection(exchangeIds, responseExchangeIds).also(connections::add)
    }

    private class RecordingConnection(
        private val exchangeIds: MutableList<String>,
        private val responseExchangeIds: MutableList<String>,
    ) : ProxyConnectionCapture {
        var closeErrorCode: String? = NOT_CLOSED

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
        ): ProxyExchangeCapture {
            exchangeIds += exchangeId.value
            return RecordingExchange(exchangeId, responseExchangeIds)
        }

        override fun close(errorCode: String?) {
            closeErrorCode = errorCode
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
            errorCode: String,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
            responseExchangeIds += exchangeId.value
        }

        override fun terminate(
            state: ExchangeState,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
            errorCode: String?,
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
