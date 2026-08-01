package com.devuloopers.knet.core.http.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiExecutionResultTest {

    @Test
    fun testSuccessfulExecutionResultDefaults() {
        val result = ApiExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to "application/json"),
            responseBody = "{\"message\":\"hello\"}",
            latencyMs = 42L,
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
        val result = ApiExecutionResult(
            statusCode = 500,
            statusText = "Internal Server Error",
            headers = emptyMap(),
            responseBody = "",
            latencyMs = 120L,
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
        val res201 = ApiExecutionResult(201, "Created", emptyMap(), "", 10L, 0L)
        val res204 = ApiExecutionResult(204, "No Content", emptyMap(), "", 10L, 0L)
        val res404 = ApiExecutionResult(404, "Not Found", emptyMap(), "", 10L, 0L)

        assertTrue(res201.isSuccess)
        assertTrue(res204.isSuccess)
        assertFalse(res404.isSuccess)
    }
}
