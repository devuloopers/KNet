package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.traffic.model.LiveTrafficUiState
import com.devuloopers.knet.domain.traffic.model.ProtocolFilter
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.domain.traffic.model.UriDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionModelTest {

    @Test
    fun testUriDetailsParsingWithQueryParameters() {
        val url = "https://api.knet.dev:8080/v1/search?q=kotlin&sort=desc&page=1"
        val parsed = UriDetails.parse(url)

        assertEquals("api.knet.dev", parsed.host)
        assertEquals("/v1/search?q=kotlin&sort=desc&page=1", parsed.path)
        assertEquals(3, parsed.queryParams.size)
        assertEquals("kotlin", parsed.queryParams["q"])
        assertEquals("desc", parsed.queryParams["sort"])
        assertEquals("1", parsed.queryParams["page"])
    }

    @Test
    fun testUriDetailsParsingWithoutQuery() {
        val url = "https://example.com/health"
        val parsed = UriDetails.parse(url)

        assertEquals("example.com", parsed.host)
        assertEquals("/health", parsed.path)
        assertTrue(parsed.queryParams.isEmpty())
    }

    @Test
    fun testProtocolFilterEnum() {
        assertEquals("ALL", ProtocolFilter.ALL.name)
        assertEquals("HTTP", ProtocolFilter.HTTP.name)
        assertEquals("HTTPS", ProtocolFilter.HTTPS.name)
        assertEquals("WEBSOCKET", ProtocolFilter.WEBSOCKET.name)
        assertEquals("GRPC", ProtocolFilter.GRPC.name)
    }

    @Test
    fun testTrafficItemUiStateFormatting() {
        val item = TrafficItemUiState(
            id = 42,
            transactionId = "tx-42",
            method = "GET",
            host = "api.knet.dev",
            path = "/users",
            status = 200,
            statusText = "OK",
            formattedTime = "120 ms",
            formattedSize = "1.25 KB",
            dateGroup = "August 01, 2026",
            requestBody = "",
            responseBody = "{}",
            queryParams = emptyMap(),
            requestHeaders = mapOf("Accept" to "application/json"),
            responseHeaders = mapOf("Content-Type" to "application/json")
        )

        assertEquals(42, item.id)
        assertEquals("tx-42", item.transactionId)
        assertEquals("GET", item.method)
        assertEquals(200, item.status)
        assertEquals("OK", item.statusText)
        assertEquals("1.25 KB", item.formattedSize)
        assertEquals(1, item.requestHeaders.size)
        assertEquals(1, item.responseHeaders.size)
    }

    @Test
    fun testLiveTrafficUiStateSealedVariants() {
        val states: List<LiveTrafficUiState> = listOf(
            LiveTrafficUiState.Loading,
            LiveTrafficUiState.Empty(activeFilter = ProtocolFilter.HTTP, searchQuery = "query")
        )

        assertTrue(states[0] is LiveTrafficUiState.Loading)
        assertTrue(states[1] is LiveTrafficUiState.Empty)

        val emptyState = states[1] as LiveTrafficUiState.Empty
        assertEquals(ProtocolFilter.HTTP, emptyState.activeFilter)
        assertEquals("query", emptyState.searchQuery)

        val item = TrafficItemUiState(
            id = 1,
            transactionId = "tx-1",
            method = "POST",
            host = "test.com",
            path = "/auth",
            status = 200,
            statusText = "OK",
            formattedTime = "12:00:00",
            formattedSize = "500 B",
            dateGroup = "Today",
            requestBody = "",
            responseBody = "",
            queryParams = emptyMap(),
            requestHeaders = emptyMap(),
            responseHeaders = emptyMap()
        )
        val successState = LiveTrafficUiState.Success(
            items = listOf(item),
            totalCount = 1,
            activeFilter = ProtocolFilter.HTTPS,
            searchQuery = "auth",
            selectedItem = item
        )

        assertEquals(1, successState.items.size)
        assertEquals(1, successState.totalCount)
        assertEquals("auth", successState.searchQuery)
        assertEquals(ProtocolFilter.HTTPS, successState.activeFilter)
    }
}
