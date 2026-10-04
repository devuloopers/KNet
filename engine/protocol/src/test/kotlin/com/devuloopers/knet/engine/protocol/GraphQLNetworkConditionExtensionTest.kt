package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLNetworkConditionExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLNetworkConditionProtocol
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
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

class GraphQLNetworkConditionExtensionTest {
    private val extension = GraphQLNetworkConditionExtension()
    private val registry = NetworkConditionProtocolRegistry(listOf(extension))

    @Test
    fun `operation name and type distinguish requests sharing one endpoint`() {
        val queryCriteria = criteria("LoadFeed", "query")
        val mutationCriteria = criteria("UpdateFeed", "mutation")
        val queryObservation = registry.inspectHttp(
            NetworkConditionProtocolId.GRAPHQL_HTTP,
            input("LoadFeed", "query LoadFeed { feed { id } }"),
        )
        val mutationObservation = registry.inspectHttp(
            NetworkConditionProtocolId.GRAPHQL_HTTP,
            input("UpdateFeed", "mutation UpdateFeed { updateFeed { id } }"),
        )

        assertTrue(registry.matches(assertNotNull(registry.compile(queryCriteria)), queryObservation))
        assertFalse(registry.matches(assertNotNull(registry.compile(queryCriteria)), mutationObservation))
        assertTrue(registry.matches(assertNotNull(registry.compile(mutationCriteria)), mutationObservation))
    }

    @Test
    fun `blank operation name with selected type matches every operation of that type`() {
        val criteria = criteria("", "subscription")
        val subscription = registry.inspectHttp(
            NetworkConditionProtocolId.GRAPHQL_HTTP,
            input("LiveFeed", "subscription LiveFeed { feedUpdated { id } }"),
        )
        val query = registry.inspectHttp(
            NetworkConditionProtocolId.GRAPHQL_HTTP,
            input("LoadFeed", "query LoadFeed { feed { id } }"),
        )

        val compiled = assertNotNull(registry.compile(criteria))
        assertTrue(registry.matches(compiled, subscription))
        assertFalse(registry.matches(compiled, query))
    }

    @Test
    fun `quick add suggests one operation and keeps a batch broad`() {
        val single = assertNotNull(registry.suggestCriteria(input("LoadFeed", "query LoadFeed { feed { id } }")))
        val batch = assertNotNull(
            registry.suggestCriteria(
                inputBody(
                    """[
                        {"operationName":"LoadFeed","query":"query LoadFeed { feed { id } }"},
                        {"operationName":"UpdateFeed","query":"mutation UpdateFeed { updateFeed { id } }"}
                    ]""".trimIndent(),
                ),
            ),
        )

        assertEquals(
            listOf("LoadFeed", "query"),
            registry.editorValues(single).map(ProtocolCriteriaValue::value),
        )
        assertEquals(listOf("", "any"), registry.editorValues(batch).map(ProtocolCriteriaValue::value))
    }

    @Test
    fun `quick add prefers the completed Traffic annotation over reparsing its body`() {
        val suggestion = assertNotNull(
            registry.suggestCriteria(
                input("BodyOperation", "query BodyOperation { feed { id } }").copy(
                    trafficAnnotations = listOf(annotation("TrafficOperation", "Mutation")),
                ),
            ),
        )

        assertEquals(
            listOf("TrafficOperation", "mutation"),
            registry.editorValues(suggestion).map(ProtocolCriteriaValue::value),
        )
    }

    @Test
    fun `batched Traffic annotation never narrows quick add to its first operation`() {
        val suggestion = assertNotNull(
            registry.suggestCriteria(
                inputBody("").copy(
                    requestBody = null,
                    requestBodyComplete = false,
                    trafficAnnotations = listOf(annotation("FirstOperation", "Query", batchSize = 2)),
                ),
            ),
        )

        assertEquals(listOf("", "any"), registry.editorValues(suggestion).map(ProtocolCriteriaValue::value))
    }

    @Test
    fun `incomplete bodies and invalid criteria fail closed`() {
        val incomplete = input("LoadFeed", "query LoadFeed { feed { id } }").copy(requestBodyComplete = false)
        val observation = registry.inspectHttp(NetworkConditionProtocolId.GRAPHQL_HTTP, incomplete)

        assertNotNull(observation, "The endpoint remains identified as GraphQL for broad rules.")
        assertFalse(registry.matches(assertNotNull(registry.compile(criteria("LoadFeed", "query"))), observation))
        assertNull(
            extension.createCriteria(
                listOf(
                    ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, "bad name"),
                    ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, "query"),
                ),
            ),
        )
    }

    private fun criteria(operationName: String, operationType: String) = assertNotNull(
        extension.createCriteria(
            listOf(
                ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, operationName),
                ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, operationType),
            ),
        ),
    )

    private fun input(operationName: String, document: String): NetworkConditionHttpInspectionInput = inputBody(
        """{"operationName":"$operationName","query":${document.jsonString()}}""",
    )

    private fun inputBody(body: String): NetworkConditionHttpInspectionInput = NetworkConditionHttpInspectionInput(
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("api.example.com", 443),
                    pathAndQuery = "/graphql",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/2"),
                headers = listOf(HeaderField(HeaderName("Content-Type"), "application/json")),
            ),
        ),
        requestBody = NetworkConditionBody(body.encodeToByteArray()),
        requestBodyComplete = true,
    )

    private fun annotation(
        operationName: String,
        operationType: String,
        batchSize: Int = 1,
    ): InspectionAnnotation = InspectionAnnotation(
        exchangeId = ExchangeId("exchange"),
        inspectorId = InspectorId("graphql"),
        schemaVersion = 1L,
        state = InspectionAnnotationState.COMPLETED,
        document = InspectionDocument(
            kind = "graphql",
            title = "GraphQL $operationType: $operationName",
            fields = buildList {
                add(InspectionField("Operation type", operationType))
                add(InspectionField("Operation name", operationName))
                if (batchSize > 1) add(InspectionField("Batch size", batchSize.toString()))
            },
        ),
        createdAtEpochMillis = 1L,
    )

    private fun String.jsonString(): String = buildString {
        append('"')
        this@jsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(character)
            }
        }
        append('"')
    }
}
