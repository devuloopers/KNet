package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformerFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class KNetStreamingProxyHandlerTest {
    private val certificateAuthority = CertificateAuthority.generate()
    private val certificateCache = CertificateCache()
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun closeScope() {
        proxyScope.cancel()
    }

    @Test
    fun `valid CONNECT returns connection established`() {
        val channel = EmbeddedChannel(
            KNetStreamingProxyHandler(
                com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(certificateAuthority, certificateCache),
                proxyScope,
            ),
        )
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "httpbin.org:443")

        channel.writeInbound(request)

        val response = channel.readOutbound<HttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.OK, response.status())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `invalid CONNECT authority returns bad request`() {
        val channel = EmbeddedChannel(
            KNetStreamingProxyHandler(
                com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(certificateAuthority, certificateCache),
                proxyScope,
            ),
        )
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.CONNECT,
            "example.com:70000",
        )

        channel.writeInbound(request)

        val response = channel.readOutbound<HttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.BAD_REQUEST, response.status())
        channel.finishAndReleaseAll()
    }

    @Test
    fun `generated streaming failure declares and captures its diagnostic body`() {
        val capture = RecordingConnectionCapture()
        val channel = EmbeddedChannel(
            KNetStreamingProxyHandler(
                serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                    certificateAuthority,
                    certificateCache,
                ),
                proxyScope = proxyScope,
                connectionCapture = capture,
                streamTransformerFactories = listOf(failingTransformerFactory("synthetic upstream failure")),
            ),
        )
        val requestBody = "{}".toByteArray()
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "http://127.0.0.1:9/graphql",
            Unpooled.wrappedBuffer(requestBody),
        ).apply {
            headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
            headers().set(HttpHeaderNames.CONTENT_LENGTH, requestBody.size)
        }

        channel.writeInbound(request)
        channel.runPendingTasks()

        val response = channel.readOutbound<DefaultFullHttpResponse>()
        assertNotNull(response)
        assertEquals(HttpResponseStatus.BAD_GATEWAY, response.status())
        assertEquals("text/plain; charset=UTF-8", response.headers().get(HttpHeaderNames.CONTENT_TYPE))
        val diagnostic = response.content().toString(Charsets.UTF_8)
        assertTrue(diagnostic.contains("synthetic upstream failure"))

        val capturedResponse = requireNotNull(capture.exchange.response)
        assertEquals(
            "text/plain; charset=UTF-8",
            capturedResponse.headers.firstValue(HttpHeaderNames.CONTENT_TYPE.toString()),
        )
        assertEquals(diagnostic, capture.exchange.responseBody.toString(Charsets.UTF_8))
        assertEquals(diagnostic.toByteArray().size.toLong(), capture.exchange.completedResponseBytes)
        val terminalOutcome = requireNotNull(capture.exchange.outcome) as ExchangeTerminalOutcome.Failed
        assertEquals(
            TrafficTerminationReason.Interception.PROTOCOL_STREAM_TRANSFORM_FAILED,
            terminalOutcome.reason,
        )

        response.release()
        channel.finishAndReleaseAll()
    }

    private fun failingTransformerFactory(message: String): ProxyStreamTransformerFactory =
        ProxyStreamTransformerFactory { _, _, _ ->
            object : ProxyStreamTransformer {
                override fun transform(
                    direction: TrafficDirection,
                    payload: ByteArray,
                    endOfDirection: Boolean,
                    occurredAtEpochMillis: Long,
                ): CompletionStage<ProxyStreamTransformResult> = CompletableFuture<ProxyStreamTransformResult>().also {
                    it.completeExceptionally(IllegalStateException(message))
                }
            }
        }

    private class RecordingConnectionCapture : ProxyConnectionCapture {
        val exchange = RecordingExchangeCapture()

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: TrafficOrigin,
            streamId: StreamId?,
        ): ProxyExchangeCapture = exchange

        override fun close(reason: TrafficTerminationReason?) = Unit
    }

    private class RecordingExchangeCapture : ProxyExchangeCapture {
        override val exchangeId: ExchangeId = ExchangeId("streaming-failure")
        private val capturedResponseBody = ByteArrayOutputStream()
        var response: ResponseHead? = null
        var completedResponseBytes: Long? = null
        var outcome: ExchangeTerminalOutcome? = null

        val responseBody: ByteArray
            get() = capturedResponseBody.toByteArray()

        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? {
            if (direction != TrafficDirection.SERVER_TO_CLIENT) return null
            val allocation = ByteArray(requestedBytes)
            return object : ProxyBodyReservation {
                override val writableBytes: ByteArray = allocation

                override fun publish(occurredAtEpochMillis: Long): Boolean {
                    capturedResponseBody.write(allocation)
                    return true
                }

                override fun cancel() = Unit
            }
        }

        override fun completeBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
        ) {
            if (direction == TrafficDirection.SERVER_TO_CLIENT) completedResponseBytes = observedBytes
        }

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            reason: TrafficTerminationReason,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
            this.response = response
        }

        override fun observeTrailers(
            direction: TrafficDirection,
            trailers: List<HeaderField>,
            occurredAtEpochMillis: Long,
        ) = Unit

        override fun terminate(
            outcome: ExchangeTerminalOutcome,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
        ) {
            this.outcome = outcome
        }
    }

    private fun List<HeaderField>.firstValue(name: String): String? = firstOrNull {
        it.name.value.equals(name, ignoreCase = true)
    }?.value
}
