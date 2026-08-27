package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead

/**
 * Publishes one test exchange through the production streaming proxy-capture boundary.
 *
 * Tests use this fixture instead of a synthetic application recording API, preserving the
 * invariant that canonical traffic can originate only from an admitted proxy connection.
 */
internal suspend fun StreamingProxyCaptureSession.recordTestProxyExchange(
    exchangeId: ExchangeId,
    request: RequestHead,
    requestBody: ByteArray? = null,
    response: ResponseHead? = null,
    responseBody: ByteArray? = null,
    outcome: ExchangeTerminalOutcome = ExchangeTerminalOutcome.Completed,
    timings: ExchangeTimings = ExchangeTimings(),
    startedAtEpochMillis: Long = 10L,
    completedAtEpochMillis: Long = startedAtEpochMillis,
) {
    val connection = checkNotNull(
        openConnection(
            ProxyCaptureConnectionMetadata(
                ingress = IngressContext(IngressKind.Local),
                downstream = TrafficEndpoint(TEST_CLIENT_HOST, TEST_CLIENT_PORT),
                localListener = TrafficEndpoint(TEST_LISTENER_HOST, TEST_LISTENER_PORT),
                transportProtocol = TEST_TRANSPORT_PROTOCOL,
            )
        )
    ) { "Test proxy connection was not admitted." }
    try {
        val exchange = checkNotNull(
            connection.startExchange(exchangeId, request, startedAtEpochMillis)
        ) { "Test proxy exchange was not admitted." }
        exchange.publishTestBody(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            body = requestBody,
            occurredAtEpochMillis = completedAtEpochMillis,
        )
        response?.let { exchange.observeResponse(it, completedAtEpochMillis) }
        exchange.publishTestBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            body = responseBody,
            occurredAtEpochMillis = completedAtEpochMillis,
        )
        exchange.terminate(outcome, timings, completedAtEpochMillis)
    } finally {
        connection.close()
    }
    flush()
}

/** Copies bounded test bytes into the same reservation API used by proxy transport callbacks. */
private fun ProxyExchangeCapture.publishTestBody(
    direction: TrafficDirection,
    body: ByteArray?,
    occurredAtEpochMillis: Long,
) {
    if (body == null || body.isEmpty()) return
    var offset = 0
    while (offset < body.size) {
        val reservation = checkNotNull(
            tryReserveBody(
                direction = direction,
                contentEncoding = null,
                requestedBytes = body.size - offset,
            )
        ) { "Test body reservation was not admitted." }
        val length = reservation.writableBytes.size
        body.copyInto(
            destination = reservation.writableBytes,
            destinationOffset = 0,
            startIndex = offset,
            endIndex = offset + length,
        )
        check(reservation.publish(occurredAtEpochMillis)) { "Test body chunk was not admitted." }
        offset += length
    }
    completeBody(direction, body.size.toLong(), occurredAtEpochMillis)
}

private const val TEST_CLIENT_HOST = "127.0.0.2"
private const val TEST_CLIENT_PORT = 50_000
private const val TEST_LISTENER_HOST = "127.0.0.1"
private const val TEST_LISTENER_PORT = 8_080
private const val TEST_TRANSPORT_PROTOCOL = "test-proxy"
