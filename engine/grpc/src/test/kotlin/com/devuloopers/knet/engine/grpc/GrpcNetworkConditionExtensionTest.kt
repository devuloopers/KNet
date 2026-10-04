package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrpcNetworkConditionExtensionTest {
    private val extension = GrpcNetworkConditionExtension()
    private val registry = NetworkConditionProtocolRegistry(listOf(extension))

    @Test
    fun `service and method distinguish multiplexed gRPC streams`() {
        val criteria = criteria("knet.testing.v1.ProtocolLab", "StreamVideo")
        val compiled = assertNotNull(registry.compile(criteria))

        assertTrue(registry.matches(compiled, observation("/knet.testing.v1.ProtocolLab/StreamVideo")))
        assertFalse(registry.matches(compiled, observation("/knet.testing.v1.ProtocolLab/UnaryEcho")))
        assertFalse(registry.matches(compiled, observation("/other.v1.ProtocolLab/StreamVideo")))
    }

    @Test
    fun `native gRPC recognition uses content type and canonical path`() {
        assertNotNull(observation("/knet.testing.v1.ProtocolLab/UnaryEcho", "application/grpc+proto"))
        assertNull(observation("/knet.testing.v1.ProtocolLab/UnaryEcho", "application/json"))
        assertNull(observation("/too/many/path/segments", "application/grpc"))
    }

    @Test
    fun `quick add suggests exact service and method without a body`() {
        val suggestion = assertNotNull(
            registry.suggestCriteria(input("/knet.testing.v1.ProtocolLab/StreamVideo", "application/grpc")),
        )

        assertEquals(NetworkConditionProtocolId.GRPC, suggestion.protocolId)
        assertEquals(
            listOf("knet.testing.v1.ProtocolLab", "StreamVideo"),
            registry.editorValues(suggestion).map(ProtocolCriteriaValue::value),
        )
    }

    @Test
    fun `invalid names and versions fail closed`() {
        assertNull(criteriaOrNull("bad service!", "Method"))
        assertNull(criteriaOrNull("valid.Service", "bad-method"))
        assertNull(
            registry.compile(
                NetworkConditionProtocolCriteria(NetworkConditionProtocolId.GRPC, "{\"version\":2}"),
            ),
        )
    }

    private fun criteria(service: String, method: String): NetworkConditionProtocolCriteria =
        assertNotNull(criteriaOrNull(service, method))

    private fun criteriaOrNull(service: String, method: String): NetworkConditionProtocolCriteria? =
        extension.createCriteria(
            listOf(
                ProtocolCriteriaValue(GrpcNetworkConditionProtocol.serviceFieldId, service),
                ProtocolCriteriaValue(GrpcNetworkConditionProtocol.methodFieldId, method),
            ),
        )

    private fun observation(path: String, contentType: String = "application/grpc") = registry.inspectHttp(
        NetworkConditionProtocolId.GRPC,
        input(path, contentType),
    )

    private fun input(path: String, contentType: String) = NetworkConditionHttpInspectionInput(
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.com", 443),
                    pathAndQuery = path,
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/2"),
                headers = listOf(HeaderField(HeaderName("content-type"), contentType)),
            ),
        ),
        requestBody = null,
        requestBodyComplete = false,
    )
}
