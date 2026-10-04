package com.devuloopers.knet.application.usecase.networkconditions

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolExtension
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.EffectiveNetworkCondition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkConditionSemanticResolverTest {
    private val protocolId = NetworkConditionProtocolId("test-semantic")

    @Test
    fun `registry rejects duplicate protocol ownership`() {
        assertFailsWith<IllegalArgumentException> {
            NetworkConditionProtocolRegistry(listOf(FakeExtension(protocolId), FakeExtension(protocolId)))
        }
    }

    @Test
    fun `resolver inspects one protocol once and selects its most specific matching rule`() {
        val extension = FakeExtension(protocolId)
        val resolver = NetworkConditionSemanticResolver(NetworkConditionProtocolRegistry(listOf(extension)))
        val criteria = NetworkConditionProtocolCriteria(protocolId, "LoadFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(
                rule("wild", "*.example.com", criteria),
                rule("exact", "api.example.com", criteria),
            ),
        )

        val effective = resolver.resolveHttp(configuration, "api.example.com", 443, input("LoadFeed"))

        assertEquals("exact", effective?.ruleId?.value)
        assertEquals(EffectiveNetworkCondition.Source.PROTOCOL_RULE, effective?.source)
        assertEquals(1, extension.inspections)
    }

    @Test
    fun `invalid semantic payload fails closed to destination and global policy`() {
        val resolver = NetworkConditionSemanticResolver(
            NetworkConditionProtocolRegistry(listOf(FakeExtension(protocolId))),
        )
        val semantic = rule(
            "semantic",
            "api.example.com",
            NetworkConditionProtocolCriteria(protocolId, "invalid"),
        )
        val domain = rule(
            "domain",
            "api.example.com",
            NetworkConditionProtocolCriteria.TransportDefault,
        ).copy(profileId = NetworkConditionBuiltIns.NO_THROTTLING.id)
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            globalProfileId = NetworkConditionBuiltIns.HIGH_LATENCY.id,
            rules = listOf(semantic, domain),
        )

        val destinationFallback = resolver.resolveHttp(configuration, "api.example.com", 443, input("LoadFeed"))
        val globalFallback = resolver.resolveHttp(
            configuration.copy(rules = listOf(semantic)),
            "api.example.com",
            443,
            input("LoadFeed"),
        )

        assertEquals("domain", destinationFallback?.ruleId?.value)
        assertEquals(EffectiveNetworkCondition.Source.GLOBAL, globalFallback?.source)
    }

    @Test
    fun `inspection requirement is bounded by enabled target and interception unit`() {
        val resolver = NetworkConditionSemanticResolver(
            NetworkConditionProtocolRegistry(listOf(FakeExtension(protocolId))),
        )
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(rule("semantic", "api.example.com", NetworkConditionProtocolCriteria(protocolId, "x"))),
        )

        assertTrue(resolver.requiresHttpInspection(configuration, "api.example.com", 443))
        assertFalse(resolver.requiresHttpInspection(configuration, "other.example.com", 443))
        assertFalse(resolver.requiresMessageInspection(configuration, "api.example.com", 443))
        assertFalse(resolver.requiresHttpInspection(configuration.copy(enabled = false), "api.example.com", 443))
    }

    @Test
    fun `only body-dependent HTTP rules request aggregation`() {
        val headerId = NetworkConditionProtocolId("header-semantic")
        val bodyId = NetworkConditionProtocolId("body-semantic")
        val registry = NetworkConditionProtocolRegistry(
            listOf(
                FakeExtension(headerId, displayName = "Headers", editorOrder = 20),
                FakeExtension(bodyId, displayName = "Body", requiresRequestBody = true, editorOrder = 10),
            ),
        )
        val resolver = NetworkConditionSemanticResolver(registry)
        val headerOnly = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(rule("headers", "api.example.com", NetworkConditionProtocolCriteria(headerId, "x"))),
        )
        val withBody = headerOnly.copy(
            rules = headerOnly.rules +
                rule("body", "api.example.com", NetworkConditionProtocolCriteria(bodyId, "x")),
        )

        assertFalse(resolver.requiresHttpBodyInspection(headerOnly, "api.example.com", 443))
        assertTrue(resolver.requiresHttpBodyInspection(withBody, "api.example.com", 443))
        assertEquals(listOf("All traffic", "Body", "Headers"), registry.definitions.map { it.displayName })
    }

    private fun rule(
        id: String,
        host: String,
        criteria: NetworkConditionProtocolCriteria,
    ): NetworkConditionRule = NetworkConditionRule(
        id = NetworkConditionRuleId(id),
        target = NetworkConditionTarget.parse(host),
        profileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id,
        protocolCriteria = criteria,
    )

    private fun input(operation: String) = NetworkConditionHttpInspectionInput(
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.com", 443),
                    pathAndQuery = "/graphql",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = emptyList(),
            ),
        ),
        requestBody = null,
        requestBodyComplete = true,
    ).also { currentOperation = operation }

    private class FakeExtension(
        private val id: NetworkConditionProtocolId,
        private val displayName: String = "Test semantic",
        private val requiresRequestBody: Boolean = false,
        override val editorOrder: Int = 0,
    ) : NetworkConditionProtocolExtension {
        var inspections: Int = 0

        override val definition = NetworkConditionProtocolDefinition(
            protocolId = id,
            displayName = displayName,
            criteriaVersion = 1,
            interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
            requiresRequestBody = requiresRequestBody,
            fields = emptyList(),
        )

        override fun compile(criteria: NetworkConditionProtocolCriteria): CompiledNetworkConditionProtocolCriteria? =
            criteria.takeIf { value -> value.protocolId == id && value.encodedPayload != "invalid" }
                ?.let { value ->
                    object : CompiledNetworkConditionProtocolCriteria {
                        override val protocolId: NetworkConditionProtocolId = id

                        override fun matches(observation: NetworkConditionProtocolObservation?): Boolean =
                            (observation as? Observation)?.operation == value.encodedPayload
                    }
                }

        override fun inspectHttp(input: NetworkConditionHttpInspectionInput): NetworkConditionProtocolObservation {
            inspections += 1
            return Observation(currentOperation, id)
        }

        override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> = emptyList()

        override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? = null

        private data class Observation(
            val operation: String,
            override val protocolId: NetworkConditionProtocolId,
        ) : NetworkConditionProtocolObservation {
        }
    }

    private companion object {
        var currentOperation: String = ""
    }
}
