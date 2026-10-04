package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class WebSocketNetworkConditionTransformerTest {
    @Test
    fun `ordinary fragmented WebSocket message is shaped once after reassembly`() {
        val extension = WebSocketNetworkConditionExtension()
        val criteria = assertNotNull(extension.createCriteria(listOf(
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.pathFieldId, "/stream"),
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.subprotocolFieldId, "chat.v2"),
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.directionFieldId, "server"),
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.kindFieldId, "binary"),
        )))
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("ordinary-websocket-offline"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = NetworkConditionBuiltIns.OFFLINE.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            WebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(
                    NetworkConditionProtocolRegistry(listOf(extension)),
                ),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)
        val firstFrame = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.BINARY,
            payload = byteArrayOf(1, 2),
            final = false,
        )
        val finalFrame = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.CONTINUATION,
            payload = byteArrayOf(3, 4),
        )

        val first = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            firstFrame,
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        val completed = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            finalFrame,
            3L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertContentEquals(ByteArray(0), assertIs<ProxyDuplexTransformResult.Forward>(first).copyPayload())
        assertIs<ProxyDuplexTransformResult.DropConnection>(completed)
        transformer.cancel(null)
    }

    private fun request() = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.GET,
            target = RequestTarget.Absolute(
                HttpScheme.fromToken("https"),
                Authority("api.example.com", 443),
                "/stream?token=secret",
            ),
            protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
            headers = listOf(
                HeaderField(HeaderName("connection"), "Upgrade"),
                HeaderField(HeaderName("upgrade"), "websocket"),
                HeaderField(HeaderName("sec-websocket-version"), "13"),
                HeaderField(HeaderName("sec-websocket-key"), "MDEyMzQ1Njc4OWFiY2RlZg=="),
                HeaderField(HeaderName("sec-websocket-protocol"), "chat.v2"),
            ),
        ),
    )

    private fun switchingResponse() = ResponseHead(
        status = HttpStatus(101),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        headers = listOf(
            HeaderField(HeaderName("connection"), "Upgrade"),
            HeaderField(HeaderName("upgrade"), "websocket"),
            HeaderField(HeaderName("sec-websocket-protocol"), "chat.v2"),
        ),
    )
}
