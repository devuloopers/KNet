package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalCaptureOriginMapperTest {

    @Test
    fun `API Studio origin survives durable entity round trip`() {
        val entity = CanonicalCaptureEntityMapper.exchange(
            CaptureEvent.ExchangeStarted(
                sessionId = CaptureSessionId("session"),
                connectionId = ConnectionId("connection"),
                sequence = 1L,
                occurredAtEpochMillis = 2L,
                exchangeId = ExchangeId("exchange"),
                exchangeVersion = 1L,
                request = RequestHead(
                    method = HttpMethod.POST,
                    target = RequestTarget.Origin("/graphql"),
                    protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                    headers = emptyList(),
                ),
                origin = TrafficOrigin.ApiStudio,
            ),
        )

        val snapshot = CanonicalCaptureEntityMapper.snapshot(entity, emptyMap())

        assertEquals(TrafficOrigin.ApiStudio.token, entity.origin)
        assertEquals(TrafficOrigin.ApiStudio, snapshot.origin)
    }
}
