package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionMessageInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSocketNetworkConditionExtensionTest {
    private val extension = WebSocketNetworkConditionExtension()
    private val registry = NetworkConditionProtocolRegistry(listOf(extension))

    @Test
    fun `path subprotocol direction and kind match one logical message`() {
        val criteria = criteria("/stream", "chat.v2", "server", "binary")
        val compiled = assertNotNull(registry.compile(criteria))

        assertTrue(
            registry.matches(
                compiled,
                observation("/stream?token=secret", "chat.v2", TrafficDirection.SERVER_TO_CLIENT, ProtocolMessageKind.BINARY),
            ),
        )
        assertFalse(
            registry.matches(
                compiled,
                observation("/stream", "chat.v2", TrafficDirection.CLIENT_TO_SERVER, ProtocolMessageKind.BINARY),
            ),
        )
        assertFalse(
            registry.matches(
                compiled,
                observation("/other", "chat.v2", TrafficDirection.SERVER_TO_CLIENT, ProtocolMessageKind.BINARY),
            ),
        )
    }

    @Test
    fun `quick add uses path and only an unambiguous requested subprotocol`() {
        val single = assertNotNull(registry.suggestCriteria(httpInput(request("/stream?token=x", "chat.v2"))))
        val multiple = assertNotNull(registry.suggestCriteria(httpInput(request("/stream", "chat.v2, chat.v3"))))

        assertEquals(
            listOf("/stream", "chat.v2", "any", "any"),
            registry.editorValues(single).map(ProtocolCriteriaValue::value),
        )
        assertEquals(
            listOf("/stream", "", "any", "any"),
            registry.editorValues(multiple).map(ProtocolCriteriaValue::value),
        )
    }

    @Test
    fun `invalid handshake and criteria fail closed`() {
        val invalidHandshake = request("/stream", "chat.v2").let { snapshot ->
            snapshot.copy(
                head = snapshot.head.copy(
                    headers = snapshot.head.headers.filterNot { header ->
                        header.name.value.equals("sec-websocket-key", ignoreCase = true)
                    },
                ),
            )
        }

        assertNull(registry.suggestCriteria(httpInput(invalidHandshake)))
        assertNull(criteriaOrNull("stream", "chat.v2", "any", "any"))
        assertNull(criteriaOrNull("/stream", "bad protocol", "any", "any"))
        assertNull(criteriaOrNull("/stream", "chat.v2", "sideways", "any"))
    }

    private fun criteria(path: String, subprotocol: String, direction: String, kind: String) = assertNotNull(
        criteriaOrNull(path, subprotocol, direction, kind),
    )

    private fun criteriaOrNull(path: String, subprotocol: String, direction: String, kind: String) =
        extension.createCriteria(
            listOf(
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.pathFieldId, path),
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.subprotocolFieldId, subprotocol),
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.directionFieldId, direction),
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.kindFieldId, kind),
            ),
        )

    private fun observation(
        path: String,
        subprotocol: String,
        direction: TrafficDirection,
        kind: ProtocolMessageKind,
    ) = registry.inspectMessage(
        NetworkConditionProtocolId.WEBSOCKET,
        NetworkConditionMessageInspectionInput(
            exchangeId = ExchangeId("exchange"),
            request = request(path, subprotocol),
            messageId = ProtocolMessageId("message"),
            kind = kind,
            negotiatedSubprotocol = subprotocol,
            direction = direction,
            sequence = 1L,
            declaredBytes = 3L,
            compressed = false,
            compressionEncoding = null,
            body = NetworkConditionBody(byteArrayOf(1, 2, 3)),
        ),
    )

    private fun httpInput(request: HttpRequestSnapshot) = NetworkConditionHttpInspectionInput(
        request = request,
        requestBody = null,
        requestBodyComplete = false,
    )

    private fun request(path: String, subprotocol: String) = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.GET,
            target = RequestTarget.Absolute(
                HttpScheme.fromToken("https"),
                Authority("api.example.com", 443),
                path,
            ),
            protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
            headers = listOf(
                HeaderField(HeaderName("connection"), "Upgrade"),
                HeaderField(HeaderName("upgrade"), "websocket"),
                HeaderField(HeaderName("sec-websocket-version"), "13"),
                HeaderField(HeaderName("sec-websocket-key"), "MDEyMzQ1Njc4OWFiY2RlZg=="),
                HeaderField(HeaderName("sec-websocket-protocol"), subprotocol),
            ),
        ),
    )
}
