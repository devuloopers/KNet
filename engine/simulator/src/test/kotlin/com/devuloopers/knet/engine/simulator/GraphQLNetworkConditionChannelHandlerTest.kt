package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLNetworkConditionExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLNetworkConditionProtocol
import com.devuloopers.knet.engine.protocol.http.HttpNetworkConditionExtension
import com.devuloopers.knet.engine.protocol.http.HttpNetworkConditionProtocol
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.model.AppliedNetworkConditionSource
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GraphQLNetworkConditionChannelHandlerTest {
    @Test
    fun `operation rule wins over the endpoint fallback on one GraphQL URL`() {
        val extension = GraphQLNetworkConditionExtension()
        val criteria = assertNotNull(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "LoadFeed"),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query"),
        )))
        val semanticRule = NetworkConditionRule(
            id = NetworkConditionRuleId("load-feed"),
            target = NetworkConditionTarget.parse("api.example.com", 443),
            profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
            protocolCriteria = criteria,
        )
        val fallbackRule = NetworkConditionRule(
            id = NetworkConditionRuleId("graphql-endpoint"),
            target = NetworkConditionTarget.parse("api.example.com", 443),
            profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(fallbackRule, semanticRule),
        )
        val resolver = NetworkConditionSemanticResolver(NetworkConditionProtocolRegistry(listOf(extension)))

        val matching = channel(configuration, resolver)
        matching.attr(ProxyChannelAttributes.APPLICATION_PROTOCOL).set(ApplicationProtocol.fromToken("HTTP/2"))
        matching.writeInbound(graphQLRequest("LoadFeed"))
        val matchingRequest = assertNotNull(matching.readInbound<DefaultFullHttpRequest>())
        val semanticEvidence = assertNotNull(
            matching.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get(),
        )
        assertEquals(AppliedNetworkConditionSource.PROTOCOL_RULE, semanticEvidence.source)
        assertEquals(semanticRule.id.value, semanticEvidence.ruleId)
        matchingRequest.release()
        matching.finishAndReleaseAll()

        val different = channel(configuration, resolver)
        different.writeInbound(graphQLRequest("OtherFeed"))
        val differentRequest = assertNotNull(different.readInbound<DefaultFullHttpRequest>())
        val fallbackEvidence = assertNotNull(
            different.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get(),
        )
        assertEquals(AppliedNetworkConditionSource.DOMAIN_RULE, fallbackEvidence.source)
        assertEquals(fallbackRule.id.value, fallbackEvidence.ruleId)
        differentRequest.release()
        different.finishAndReleaseAll()
    }

    @Test
    fun `streamed semantic request is aggregated within the bound and falls back when it exceeds it`() {
        val extension = GraphQLNetworkConditionExtension()
        val criteria = assertNotNull(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "LoadFeed"),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query"),
        )))
        val semanticRule = NetworkConditionRule(
            id = NetworkConditionRuleId("load-feed"),
            target = NetworkConditionTarget.parse("api.example.com", 443),
            profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
            protocolCriteria = criteria,
        )
        val configuration = NetworkConditionConfiguration(enabled = true, rules = listOf(semanticRule))
        val resolver = NetworkConditionSemanticResolver(NetworkConditionProtocolRegistry(listOf(extension)))
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(
            NetworkConditionRequestAggregator(engine, resolver, maximumContentBytes = 1_024),
            NetworkConditionChannelHandler(engine, resolver),
        )

        channel.writeInbound(graphQLHead())
        assertNull(channel.readInbound<Any>())
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer(graphQLBody("LoadFeed"))))
        val aggregated = assertNotNull(channel.readInbound<DefaultFullHttpRequest>())
        assertEquals(
            AppliedNetworkConditionSource.PROTOCOL_RULE,
            channel.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get()?.source,
        )
        aggregated.release()
        channel.finishAndReleaseAll()

        val boundedEngine = NetworkConditionEngine(configuration = { configuration })
        val oversized = EmbeddedChannel(
            NetworkConditionRequestAggregator(boundedEngine, resolver, maximumContentBytes = 16),
            NetworkConditionChannelHandler(boundedEngine, resolver),
        )
        oversized.writeInbound(graphQLHead())
        oversized.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer(graphQLBody("LoadFeed"))))
        val replayedHead = assertNotNull(oversized.readInbound<DefaultHttpRequest>())
        val replayedContent = assertNotNull(oversized.readInbound<DefaultLastHttpContent>())
        assertNull(oversized.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get())
        replayedContent.release()
        oversized.finishAndReleaseAll()
    }

    @Test
    fun `header-only HTTP rules do not aggregate a streaming request body`() {
        val extension = HttpNetworkConditionExtension()
        val criteria = assertNotNull(extension.createCriteria(listOf(
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.methodFieldId, "POST"),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathFieldId, "/upload"),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathModeFieldId, "exact"),
        )))
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                NetworkConditionRule(
                    id = NetworkConditionRuleId("streaming-upload"),
                    target = NetworkConditionTarget.parse("api.example.com", 443),
                    profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
                    protocolCriteria = criteria,
                ),
                NetworkConditionRule(
                    id = NetworkConditionRuleId("graphql-on-same-host"),
                    target = NetworkConditionTarget.parse("api.example.com", 443),
                    profileId = NetworkConditionBuiltIns.NO_THROTTLING.id,
                    protocolCriteria = assertNotNull(GraphQLNetworkConditionExtension().createCriteria(listOf(
                        ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "LoadFeed"),
                        ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query"),
                    ))),
                ),
            ),
        )
        val resolver = NetworkConditionSemanticResolver(
            NetworkConditionProtocolRegistry(listOf(extension, GraphQLNetworkConditionExtension())),
        )
        val engine = NetworkConditionEngine(configuration = { configuration })
        val channel = EmbeddedChannel(
            NetworkConditionRequestAggregator(engine, resolver, maximumContentBytes = 1_024),
            NetworkConditionChannelHandler(engine, resolver),
        )
        val request = DefaultHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "https://api.example.com/upload?part=1",
        ).apply {
            headers().set(HttpHeaderNames.HOST, "api.example.com")
            headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
        }

        channel.writeInbound(request)

        assertNotNull(channel.readInbound<DefaultHttpRequest>())
        assertEquals(
            AppliedNetworkConditionSource.PROTOCOL_RULE,
            channel.attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).get()?.source,
        )
        channel.writeInbound(DefaultLastHttpContent(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3))))
        assertNotNull(channel.readInbound<DefaultLastHttpContent>()).release()
        channel.finishAndReleaseAll()
    }

    private fun channel(
        configuration: NetworkConditionConfiguration,
        resolver: NetworkConditionSemanticResolver,
    ): EmbeddedChannel = EmbeddedChannel(
        NetworkConditionChannelHandler(
            engine = NetworkConditionEngine(configuration = { configuration }),
            semanticResolver = resolver,
        ),
    )

    private fun graphQLRequest(operationName: String): DefaultFullHttpRequest {
        val body = graphQLBody(operationName)
        return DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "https://api.example.com/graphql",
            Unpooled.wrappedBuffer(body),
        ).apply {
            headers().set(HttpHeaderNames.HOST, "api.example.com")
            headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
            headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.size)
        }
    }

    private fun graphQLHead(): DefaultHttpRequest = DefaultHttpRequest(
        HttpVersion.HTTP_1_1,
        HttpMethod.POST,
        "https://api.example.com/graphql",
    ).apply {
        headers().set(HttpHeaderNames.HOST, "api.example.com")
        headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json")
    }

    private fun graphQLBody(operationName: String): ByteArray =
        """{"operationName":"$operationName","query":"query $operationName { feed { id } }"}"""
            .encodeToByteArray()
}
