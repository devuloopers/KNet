package com.devuloopers.knet.core.http.model

import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.traffic.model.ExchangeTimings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionResultTest {

    @Test
    fun testSuccessfulExecutionResultDefaults() {
        val result = ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to "application/json"),
            responseBody = "{\"message\":\"hello\"}",
            timings = ExchangeTimings(totalMillis = 42L),
            responseSizeBytes = 19L
        )

        assertEquals(200, result.statusCode)
        assertEquals("OK", result.statusText)
        assertEquals("application/json", result.headers["Content-Type"])
        assertEquals("{\"message\":\"hello\"}", result.responseBody)
        assertEquals(42L, result.latencyMs)
        assertEquals(19L, result.responseSizeBytes)
        assertTrue(result.isSuccess)
        assertNull(result.errorMessage)
    }

    @Test
    fun testErrorExecutionResultDefaults() {
        val result = ExecutionResult(
            statusCode = 500,
            statusText = "Internal Server Error",
            headers = emptyMap(),
            responseBody = "",
            timings = ExchangeTimings(totalMillis = 120L),
            responseSizeBytes = 0L,
            errorMessage = "Server error occurred"
        )

        assertEquals(500, result.statusCode)
        assertEquals("Internal Server Error", result.statusText)
        assertFalse(result.isSuccess)
        assertEquals("Server error occurred", result.errorMessage)
    }

    @Test
    fun testIsSuccessPropertyForRanges() {
        val timings = ExchangeTimings(totalMillis = 10L)
        val res201 = ExecutionResult(statusCode = 201, statusText = "Created", timings = timings)
        val res204 = ExecutionResult(statusCode = 204, statusText = "No Content", timings = timings)
        val res404 = ExecutionResult(statusCode = 404, statusText = "Not Found", timings = timings)

        assertTrue(res201.isSuccess)
        assertTrue(res204.isSuccess)
        assertFalse(res404.isSuccess)
    }
}
