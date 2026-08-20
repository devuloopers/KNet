package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorBody
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
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
    fun describe_returnsNullForOrdinaryHttpJson() {
        assertNull(
            strategy.describe(
                request(
                    content = """{"message":"ordinary JSON"}""",
                    url = "https://api.example.com/events",
                    semanticKindHint = null,
                ),
            ),
        )
    }

    @Test
    fun describe_acceptsPersistedSemanticHintForNonstandardEndpoint() {
        val descriptor = strategy.describe(
            request(
                content = "",
                url = "https://api.example.com/gateway",
                semanticKindHint = RequestKindId.GRAPHQL,
            ),
        )

        assertEquals(RequestKindId.GRAPHQL, descriptor?.kind)
        assertEquals("GQL", descriptor?.badgeLabel)
    }

    private fun request(
        content: String,
        url: String = "https://api.example.com/graphql",
        semanticKindHint: RequestKindId? = RequestKindId.GRAPHQL,
    ): RequestDescriptorInput = RequestDescriptorInput(
        transportMethod = HttpMethod.POST,
        absoluteUrl = url,
        body = RequestDescriptorBody(content.encodeToByteArray()),
        bodyComplete = true,
        semanticKindHint = semanticKindHint,
    )
}
