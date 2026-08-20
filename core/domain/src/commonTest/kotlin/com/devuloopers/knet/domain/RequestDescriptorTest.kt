package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDescriptorTest {

    private val httpStrategy = HttpRequestDescriptorStrategy()

    @Test
    fun httpStrategy_usesPathAndActualMethodBadge() {
        val descriptor = DescribeRequestUseCase(listOf(httpStrategy)).execute(
            request("https://api.example.com/account/user/?expand=true#profile", HttpMethod.POST)
        )

        assertEquals("/account/user", descriptor.suggestedName)
        assertEquals(RequestKindId.HTTP, descriptor.kind)
        assertEquals("POST", descriptor.badgeLabel)
        assertEquals(HttpMethod.POST, descriptor.transportMethod)
    }

    @Test
    fun httpStrategy_usesHostForRootRequest() {
        val descriptor = DescribeRequestUseCase(listOf(httpStrategy)).execute(
            request("https://api.example.com:8443/")
        )

        assertEquals("api.example.com", descriptor.suggestedName)
    }

    @Test
    fun useCase_runsSemanticDescriptorBeforeTerminalHttpRegardlessOfRegistrationOrder() {
        val useCase = DescribeRequestUseCase(
            listOf(
                httpStrategy,
                RequestDescriptorStrategy {
                    RequestDescriptorContribution(RequestKindId.GRAPHQL, "GQL", "GetProfile")
                },
            )
        )

        val descriptor = useCase.execute(request("https://api.example.com/graphql", HttpMethod.POST))

        assertEquals("GetProfile", descriptor.suggestedName)
        assertEquals(RequestKindId.GRAPHQL, descriptor.kind)
        assertEquals("GQL", descriptor.badgeLabel)
        assertEquals(HttpMethod.POST, descriptor.transportMethod)
    }

    @Test
    fun anonymousProtocol_retainsProtocolBadgeAndUsesHttpNameFallback() {
        val useCase = DescribeRequestUseCase(
            listOf(
                RequestDescriptorStrategy {
                    RequestDescriptorContribution(RequestKindId.GRAPHQL, "GQL")
                },
                httpStrategy
            )
        )

        val descriptor = useCase.execute(request("https://api.example.com/graphql", HttpMethod.POST))

        assertEquals("/graphql", descriptor.suggestedName)
        assertEquals(RequestKindId.GRAPHQL, descriptor.kind)
        assertEquals("GQL", descriptor.badgeLabel)
    }

    @Test
    fun useCase_isolatesContributionFailureAndReturnsStableFallback() {
        val useCase = DescribeRequestUseCase(
            listOf(
                RequestDescriptorStrategy { error("Optional strategy failure") },
                httpStrategy
            )
        )

        assertEquals(
            DescribeRequestUseCase.UNTITLED_REQUEST,
            useCase.execute(request("")).suggestedName
        )
    }

    private fun request(
        url: String,
        method: HttpMethod = HttpMethod.GET
    ): SavedApiRequest = SavedApiRequest(
        id = "request-1",
        name = DescribeRequestUseCase.UNTITLED_REQUEST,
        method = method,
        url = url
    )
}
