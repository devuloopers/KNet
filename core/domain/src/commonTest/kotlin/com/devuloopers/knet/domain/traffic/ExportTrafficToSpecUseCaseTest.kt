package com.devuloopers.knet.domain.traffic

import com.devuloopers.knet.domain.TestFixtures
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.traffic.model.TransactionBody
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.traffic.usecase.ExportTrafficToSpecUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExportTrafficToSpecUseCaseTest {

    private class FakeLiveTrafficRepository(
        private val transactions: List<HttpTransaction>,
        private val bodyMap: Map<String, TransactionBody> = emptyMap()
    ) : LiveTrafficRepository {

        override val transactionsFlow: Flow<List<HttpTransaction>>
            get() = flowOf(transactions)

        override suspend fun getTransactionById(transactionId: String): HttpTransaction? {
            return transactions.find { it.id == transactionId }
        }

        override suspend fun loadTransactionBody(transactionId: String): TransactionBody {
            return bodyMap[transactionId] ?: TransactionBody.Empty
        }

        override suspend fun recordTransaction(transaction: HttpTransaction) {}
        override fun clearSession() {}
    }

    @Test
    fun execute_exportsCompleteTransactionWithBodyHeadersAndParamsToApiStudio() = runTest {
        val targetTxId = "tx-export-999"
        val requestJsonBody = "{\"name\":\"KNet IDE\",\"type\":\"Desktop\"}"
        val requestJsonBytes = requestJsonBody.encodeToByteArray()

        val req = TestFixtures.createHttpRequest(
            id = "req-999",
            method = "POST",
            url = "https://api.knet.dev/v1/projects?category=developer&active=true",
            headers = listOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer secret_token_xyz",
                "Cookie" to "session=abc12345",
                "Accept" to "application/json"
            )
        )
        val tx = TestFixtures.createHttpTransaction(id = targetTxId, request = req)
        val transactionBody = TransactionBody(
            requestBody = requestJsonBytes,
            requestHeaders = req.headers,
            responseBody = null,
            responseHeaders = emptyList()
        )

        val fakeRepository = FakeLiveTrafficRepository(
            transactions = listOf(tx),
            bodyMap = mapOf(targetTxId to transactionBody)
        )
        val exportUseCase = ExportTrafficToSpecUseCase(fakeRepository)
        val importUseCase = ImportRequestToStudioUseCase()

        // 1. Export from Traffic to NetworkRequestSpec
        val exportedSpec = exportUseCase.execute(targetTxId)

        assertNotNull(exportedSpec)
        assertEquals(HttpMethod.POST, exportedSpec.method)
        assertEquals("https://api.knet.dev/v1/projects?category=developer&active=true", exportedSpec.url)
        assertEquals(requestJsonBody, exportedSpec.bodyPayload)
        assertEquals(RequestBodyType.JSON, exportedSpec.bodyType)
        assertEquals(4, exportedSpec.headers.size)
        assertEquals(2, exportedSpec.queryParams.size)
        assertEquals("category" to "developer", exportedSpec.queryParams[0])
        assertEquals("active" to "true", exportedSpec.queryParams[1])
        assertEquals(1, exportedSpec.cookies.size)
        assertEquals("session" to "abc12345", exportedSpec.cookies[0])

        // 2. Import NetworkRequestSpec into API Studio
        val studioResult = importUseCase.execute(exportedSpec)

        assertNotNull(studioResult)
        assertEquals("https://api.knet.dev/v1/projects?category=developer&active=true", studioResult.spec.url)
        assertEquals("/v1/projects", studioResult.displayTitle)
        assertEquals(requestJsonBody, studioResult.spec.bodyPayload)
        assertEquals(listOf("category" to "developer", "active" to "true"), studioResult.spec.queryParams)
        assertEquals(4, studioResult.spec.headers.size)
    }

    @Test
    fun execute_usesCachedReqBodyTextWhenProvided() = runTest {
        val targetTxId = "tx-export-cached"
        val cachedBody = "{\"cached\":true}"
        val req = TestFixtures.createHttpRequest(
            id = "req-cached",
            method = "PUT",
            url = "https://api.knet.dev/v1/cache"
        )
        val tx = TestFixtures.createHttpTransaction(id = targetTxId, request = req)
        val fakeRepository = FakeLiveTrafficRepository(transactions = listOf(tx))
        val exportUseCase = ExportTrafficToSpecUseCase(fakeRepository)

        val exportedSpec = exportUseCase.execute(targetTxId, cachedReqBody = cachedBody)

        assertNotNull(exportedSpec)
        assertEquals(HttpMethod.PUT, exportedSpec.method)
        assertEquals(cachedBody, exportedSpec.bodyPayload)
    }

    @Test
    fun execute_fallsBackToTableUiStateWhenDatabaseRecordIsAbsent() = runTest {
        val targetTxId = "tx-historical-yesterday"
        val fakeRepository = FakeLiveTrafficRepository(transactions = emptyList())
        val exportUseCase = ExportTrafficToSpecUseCase(fakeRepository)

        val fallbackUiItem = com.devuloopers.knet.domain.traffic.model.TrafficItemUiState(
            id = 10,
            transactionId = targetTxId,
            method = "POST",
            host = "api.knet.dev",
            path = "/v1/historical",
            status = 200,
            statusText = "OK",
            protocol = "HTTP/2",
            timestamp = 1700000000000L,
            formattedTimestamp = "19:28:35",
            formattedTime = "120 ms",
            formattedSize = "1.2 KB",
            dateGroup = "Today",
            requestBody = "{\"historical\":true}",
            responseBody = "{}",
            queryParams = mapOf("archive" to "true"),
            requestHeaders = mapOf("Authorization" to "Bearer token123"),
            responseHeaders = emptyMap()
        )

        val spec = exportUseCase.execute(
            transactionId = targetTxId,
            cachedReqBody = "{\"historical\":true}",
            fallbackItem = fallbackUiItem
        )

        assertNotNull(spec)
        assertEquals(HttpMethod.POST, spec.method)
        assertEquals("https://api.knet.dev/v1/historical", spec.url)
        assertEquals("{\"historical\":true}", spec.bodyPayload)
        assertEquals(listOf("Authorization" to "Bearer token123"), spec.headers)
    }
}
