package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.interceptor.InterceptResult
import com.devuloopers.knet.engine.interceptor.InterceptSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying [InterceptionSessionRepositoryImpl] bridging between domain UseCases
 * and Netty engine's [InterceptSessionManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterceptionSessionRepositoryImplTest {

    private val repository = InterceptionSessionRepositoryImpl(InterceptSessionManager)

    @BeforeTest
    fun setUp() {
        InterceptSessionManager.clearSuspensions()
    }

    @AfterTest
    fun tearDown() {
        InterceptSessionManager.clearSuspensions()
    }

    @Test
    fun testActiveInterceptionsStreamEmitsSuspendedTransaction() = runTest {
        val fakeRequest = HttpRequest(
            id = "tx-100",
            method = "POST",
            url = "https://api.stripe.com/v1/charges",
            protocol = "HTTP/1.1",
            headers = listOf("Authorization" to "Bearer test_key"),
            body = "{\"amount\": 1000}".encodeToByteArray(),
            timestamp = 1700000000L
        )

        val event = InterceptSessionManager.suspendRequest(fakeRequest)

        val activeList = repository.activeInterceptions.first()
        assertEquals(1, activeList.size)
        val transaction = activeList.first()

        assertEquals(event.id, transaction.id)
        assertEquals(BreakpointPhase.REQUEST, transaction.phase)
        assertEquals("POST", transaction.method)
        assertEquals("https://api.stripe.com/v1/charges", transaction.url)
        assertEquals(fakeRequest, transaction.request)
    }

    @Test
    fun testForwardRequestResumesNettyDeferredWithModifiedPayload() = runTest {
        val fakeRequest = HttpRequest(
            id = "tx-101",
            method = "POST",
            url = "https://api.example.com/v1/user",
            protocol = "HTTP/1.1",
            headers = emptyList(),
            body = null,
            timestamp = 1700000000L
        )

        val event = InterceptSessionManager.suspendRequest(fakeRequest)

        val modifiedRequest = HttpRequest(
            id = fakeRequest.id,
            method = fakeRequest.method,
            url = fakeRequest.url,
            protocol = fakeRequest.protocol,
            headers = listOf("X-Modified" to "true"),
            body = fakeRequest.body,
            timestamp = fakeRequest.timestamp
        )

        repository.forwardRequest(event.id, modifiedRequest)

        assertTrue(event.deferred.isCompleted, "Netty suspension deferred must be completed")
        val result = event.deferred.getCompleted()
        assertTrue(result is InterceptResult.Resume)
        assertEquals(modifiedRequest, result.modifiedRequest)
    }

    @Test
    fun testForwardResponseResumesNettyDeferredWithModifiedResponse() = runTest {
        val fakeRequest = HttpRequest(
            id = "tx-102",
            method = "GET",
            url = "https://api.example.com/v1/data",
            protocol = "HTTP/1.1",
            headers = emptyList(),
            body = null,
            timestamp = 1700000000L
        )
        val fakeResponse = HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = emptyList(),
            body = null,
            timestamp = 1700000000L
        )

        val event = InterceptSessionManager.suspendResponse(fakeRequest, fakeResponse)

        val modifiedResponse = HttpResponse(
            statusCode = 201,
            statusText = "Created",
            headers = fakeResponse.headers,
            body = fakeResponse.body,
            timestamp = fakeResponse.timestamp
        )
        repository.forwardResponse(event.id, modifiedResponse)

        assertTrue(event.deferred.isCompleted)
        val result = event.deferred.getCompleted()
        assertTrue(result is InterceptResult.Resume)
        assertEquals(modifiedResponse, result.modifiedResponse)
    }

    @Test
    fun testDropTransactionResumesNettyDeferredWithDrop() = runTest {
        val fakeRequest = HttpRequest(
            id = "tx-103",
            method = "GET",
            url = "https://api.example.com/v1/block",
            protocol = "HTTP/1.1",
            headers = emptyList(),
            body = null,
            timestamp = 1700000000L
        )

        val event = InterceptSessionManager.suspendRequest(fakeRequest)
        repository.dropTransaction(event.id)

        assertTrue(event.deferred.isCompleted)
        val result = event.deferred.getCompleted()
        assertTrue(result is InterceptResult.Drop)
    }

    @Test
    fun testClearAllDropsAllSuspensions() = runTest {
        val req1 = HttpRequest(id = "tx-1", method = "GET", url = "https://a.com", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1700000000L)
        val req2 = HttpRequest(id = "tx-2", method = "GET", url = "https://b.com", protocol = "HTTP/1.1", headers = emptyList(), body = null, timestamp = 1700000000L)

        val event1 = InterceptSessionManager.suspendRequest(req1)
        val event2 = InterceptSessionManager.suspendRequest(req2)

        repository.clearAll()

        assertTrue(event1.deferred.isCompleted)
        assertTrue(event2.deferred.isCompleted)
        assertTrue(event1.deferred.getCompleted() is InterceptResult.Drop)
        assertTrue(event2.deferred.getCompleted() is InterceptResult.Drop)
    }
}
