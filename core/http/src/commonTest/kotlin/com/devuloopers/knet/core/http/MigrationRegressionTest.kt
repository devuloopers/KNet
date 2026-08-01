package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.execution.HttpExecutor
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationRegressionTest {

    @Test
    fun testSavedApiRequestExecutionOverload() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("GET", request.method.value)
            assertEquals("https://api.knet.dev/v1/items", request.url.toString())
            respond(content = "{\"items\":[1,2]}", status = HttpStatusCode.OK, headers = headersOf())
        }

        val executor: HttpExecutor = KNetApiClient(customEngine = mockEngine)
        val savedReq = SavedApiRequest(
            id = "req-1",
            name = "Get Items",
            method = HttpMethod.GET,
            url = "https://api.knet.dev/v1/items"
        )

        val result = executor.execute(savedReq)
        assertEquals(200, result.statusCode)
        assertTrue(result.isSuccess)
        assertEquals("{\"items\":[1,2]}", result.responseBody)

        executor.close()
    }
}
