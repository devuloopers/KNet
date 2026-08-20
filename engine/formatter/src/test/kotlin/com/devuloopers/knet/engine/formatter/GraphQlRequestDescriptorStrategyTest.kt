package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.domain.apistudio.descriptor.RequestKindId
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.engine.formatter.descriptor.GraphQlRequestDescriptorStrategy
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphQlRequestDescriptorStrategyTest {

    private val strategy = GraphQlRequestDescriptorStrategy()

    @Test
    fun describe_prefersExplicitEnvelopeOperationName() {
        val descriptor = strategy.describe(
            request("""{"operationName":"GetUser","query":"query DifferentName { user { id } }"}""")
        )

        assertEquals("GetUser", descriptor?.suggestedName)
        assertEquals(RequestKindId.GRAPHQL, descriptor?.kind)
        assertEquals("GQL", descriptor?.badgeLabel)
    }

    @Test
    fun describe_usesNamedAstOperationWhenEnvelopeNameIsAbsent() {
        val descriptor = strategy.describe(request("mutation UpdateProfile { updateProfile { id } }"))

        assertEquals("UpdateProfile", descriptor?.suggestedName)
        assertEquals(RequestKindId.GRAPHQL, descriptor?.kind)
        assertEquals("GQL", descriptor?.badgeLabel)
    }

    @Test
    fun describe_retainsGraphQlIdentityForAnonymousOperation() {
        val descriptor = strategy.describe(request("{ viewer { id } }"))

        assertEquals(RequestKindId.GRAPHQL, descriptor?.kind)
        assertEquals("GQL", descriptor?.badgeLabel)
        assertNull(descriptor?.suggestedName)
    }

    @Test
    fun describe_returnsNullForOtherBodyTypes() {
        val request = request("query GetUser { user { id } }").copy(
            body = ApiRequestBody(
                content = "query GetUser { user { id } }",
                type = RequestBodyType.RAW_TEXT
            )
        )

        assertNull(strategy.describe(request))
    }

    private fun request(content: String): SavedApiRequest = SavedApiRequest(
        id = "graphql-request",
        name = "Untitled Request",
        method = HttpMethod.POST,
        url = "https://api.example.com/graphql",
        body = ApiRequestBody(content = content, type = RequestBodyType.GRAPHQL)
    )
}
