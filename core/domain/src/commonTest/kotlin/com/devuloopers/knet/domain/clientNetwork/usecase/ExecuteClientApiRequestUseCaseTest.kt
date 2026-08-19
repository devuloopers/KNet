package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeHttpExecutor : HttpExecutor {
    var lastExecutedUrl: String = ""
    var lastExecutedMethod: HttpMethod = HttpMethod.GET
    var lastHeaders: Map<String, String> = emptyMap()
    var lastProxyPort: Int? = null
    var shouldFail: Boolean = false

    override suspend fun execute(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        proxyPort: Int?
    ): ExecutionResult {
        lastExecutedUrl = url
        lastExecutedMethod = method
        lastHeaders = headers
        lastProxyPort = proxyPort

        if (shouldFail) {
            throw RuntimeException("Network connection failed")
        }

        return ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("Content-Type" to "application/json"),
            cookies = mapOf("session" to "abc123xyz"),
            responseBody = "{\"status\": \"success\"}",
            timings = ExchangeTimings(totalMillis = 45L),
            responseSizeBytes = 24L,
            isSuccess = true
        )
    }

    override fun close() { }
}

class ExecuteClientApiRequestUseCaseTest {

    @Test
    fun testExecuteClientApiRequestSuccess() = runTest {
        val fakeExecutor = FakeHttpExecutor()
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        val result = useCase(
            url = "https://api.example.com/v1/users",
            method = HttpMethod.POST,
            headers = mapOf("Accept" to "application/json"),
            queryParams = listOf("page" to "1", "sort" to "desc"),
            cookies = mapOf("theme" to "dark"),
            body = OutboundRequestBody.Json("{\"name\": \"KNet\"}"),
            auth = ApiRequestAuth.None,
            proxyPort = 8080
        )

        assertTrue(result.isSuccess)
        assertEquals(200, result.statusCode)
        assertEquals("OK", result.statusText)
        assertEquals("https://api.example.com/v1/users?page=1&sort=desc", fakeExecutor.lastExecutedUrl)
        assertEquals(HttpMethod.POST, fakeExecutor.lastExecutedMethod)
        assertEquals("theme=dark", fakeExecutor.lastHeaders["Cookie"])
        assertEquals("abc123xyz", result.cookies["session"])
        assertEquals(8080, fakeExecutor.lastProxyPort)
    }

    @Test
    fun `complete URL query is rebuilt once and repeated keys are preserved`() = runTest {
        val fakeExecutor = FakeHttpExecutor()
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        useCase(
            url = "https://api.example.com/items?tag=stale",
            queryParams = listOf("tag" to "one", "tag" to "two"),
        )

        assertEquals(
            "https://api.example.com/items?tag=one&tag=two",
            fakeExecutor.lastExecutedUrl,
        )
    }

    @Test
    fun testExecuteClientApiRequestDirectPipelineWhenProxyPortNull() = runTest {
        val fakeExecutor = FakeHttpExecutor()
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        val result = useCase(
            url = "https://api.example.com/v1/users",
            method = HttpMethod.GET,
            proxyPort = null
        )

        assertTrue(result.isSuccess)
        assertEquals(null, fakeExecutor.lastProxyPort)
    }

    @Test
    fun testExecuteClientApiRequestValidationError() = runTest {
        val fakeExecutor = FakeHttpExecutor()
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        val result = useCase(
            url = "",
            method = HttpMethod.GET
        )

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertEquals("Validation Error", result.statusText)
        assertEquals("URL cannot be empty", result.errorMessage)
    }

    @Test
    fun testExecuteClientApiRequestCustomMethod() = runTest {
        val fakeExecutor = FakeHttpExecutor()
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        val result = useCase(
            url = "https://api.example.com/v1/resource",
            method = HttpMethod.fromToken("PROPFIND")
        )

        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.fromToken("PROPFIND"), fakeExecutor.lastExecutedMethod)
    }

    @Test
    fun testExecuteClientApiRequestNetworkErrorHandling() = runTest {
        val fakeExecutor = FakeHttpExecutor().apply { shouldFail = true }
        val useCase = ExecuteClientApiRequestUseCase(fakeExecutor)

        val result = useCase(
            url = "https://api.example.com/fail",
            method = HttpMethod.GET
        )

        assertFalse(result.isSuccess)
        assertEquals(0, result.statusCode)
        assertEquals("Execution Error", result.statusText)
        assertEquals("Network connection failed", result.errorMessage)
    }

    @Test
    fun `execution cancellation is propagated instead of converted to a result`() = runTest {
        val cancellingExecutor = object : HttpExecutor {
            override suspend fun execute(
                url: String,
                method: HttpMethod,
                headers: Map<String, String>,
                body: OutboundRequestBody,
                auth: ApiRequestAuth,
                proxyPort: Int?,
            ): ExecutionResult = throw CancellationException("cancelled")

            override fun close() = Unit
        }

        assertFailsWith<CancellationException> {
            ExecuteClientApiRequestUseCase(cancellingExecutor)(url = "https://api.example.com/cancel")
        }
    }

    @Test
    fun testFormatResponseBodyUseCaseJsonFormatting() {
        val formatUseCase = FormatResponseBodyUseCase()
        val rawJson = "{\"status\":\"ok\",\"code\":200}"

        val formatted = formatUseCase.execute(rawJson)

        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("\"status\": \"ok\""))
    }
}
