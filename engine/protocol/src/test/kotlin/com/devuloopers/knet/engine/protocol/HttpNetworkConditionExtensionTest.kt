package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.engine.protocol.http.HttpNetworkConditionExtension
import com.devuloopers.knet.engine.protocol.http.HttpNetworkConditionProtocol
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
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

class HttpNetworkConditionExtensionTest {
    private val extension = HttpNetworkConditionExtension()
    private val registry = NetworkConditionProtocolRegistry(listOf(extension))

    @Test
    fun `exact method and path ignore the request query`() {
        val criteria = criteria("GET", "/api/videos", "exact")
        val compiled = assertNotNull(registry.compile(criteria))

        assertTrue(registry.matches(compiled, observation("GET", "/api/videos?page=2")))
        assertFalse(registry.matches(compiled, observation("POST", "/api/videos?page=2")))
        assertFalse(registry.matches(compiled, observation("GET", "/api/video")))
    }

    @Test
    fun `path prefix is segment aware`() {
        val compiled = assertNotNull(registry.compile(criteria("", "/api/videos", "prefix")))

        assertTrue(registry.matches(compiled, observation("GET", "/api/videos")))
        assertTrue(registry.matches(compiled, observation("GET", "/api/videos/live")))
        assertFalse(registry.matches(compiled, observation("GET", "/api/videos-old")))
    }

    @Test
    fun `quick add suggests the captured method and queryless path`() {
        val suggested = assertNotNull(registry.suggestCriteria(input("PATCH", "/api/videos/one?token=secret")))

        assertEquals(NetworkConditionProtocolId.HTTP, suggested.protocolId)
        assertEquals(
            listOf("PATCH", "/api/videos/one", "exact"),
            registry.editorValues(suggested).map(ProtocolCriteriaValue::value),
        )
    }

    @Test
    fun `invalid method path and payload fail closed`() {
        assertNull(criteriaOrNull("bad method", "/api", "exact"))
        assertNull(criteriaOrNull("GET", "api", "exact"))
        assertNull(criteriaOrNull("GET", "/api", "contains"))
        assertNull(
            registry.compile(
                NetworkConditionProtocolCriteria(NetworkConditionProtocolId.HTTP, "{\"version\":99}"),
            ),
        )
    }

    private fun criteria(method: String, path: String, mode: String): NetworkConditionProtocolCriteria =
        assertNotNull(criteriaOrNull(method, path, mode))

    private fun criteriaOrNull(
        method: String,
        path: String,
        mode: String,
    ): NetworkConditionProtocolCriteria? = extension.createCriteria(
        listOf(
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.methodFieldId, method),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathFieldId, path),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathModeFieldId, mode),
        ),
    )

    private fun observation(method: String, path: String) = registry.inspectHttp(
        NetworkConditionProtocolId.HTTP,
        input(method, path),
    )

    private fun input(method: String, path: String) = NetworkConditionHttpInspectionInput(
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.fromToken(method),
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.com", 443),
                    pathAndQuery = path,
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/2"),
                headers = emptyList(),
            ),
        ),
        requestBody = null,
        requestBodyComplete = false,
    )
}
