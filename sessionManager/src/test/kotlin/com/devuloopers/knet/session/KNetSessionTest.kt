package com.devuloopers.knet.session

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import com.devuloopers.knet.session.httparchive.HTTPArchiveExporter
import com.devuloopers.knet.session.util.CurlGenerator
import com.devuloopers.knet.storage.KNetDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests validating Room database operations, file caching,
 * HAR JSON exports, and cURL command generation.
 */
class KNetSessionTest {

    private lateinit var tempDir: File
    private lateinit var database: KNetDatabase
    private lateinit var payloadStore: FilePayloadStore
    private lateinit var session: KNetSession

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("knet_test_session").toFile()
        
        val dbFile = File(tempDir, "knet_test.db")
        database = KNetDatabase.create(dbFile)
        
        val cacheFolder = File(tempDir, "payloads")
        payloadStore = FilePayloadStore(cacheFolder)
        
        session = KNetSession(database, payloadStore)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun testRecordRequestAndResponseFlow() = runBlocking {
        // 1. Prepare and record request
        val requestBody = "{\"query\":\"KNet\"}"
        val request = HttpRequest(
            id = "tx-123",
            method = "POST",
            url = "http://localhost:8080/search",
            protocol = "HTTP/1.1",
            headers = listOf("Content-Type" to "application/json", "User-Agent" to "KNet-Test"),
            body = requestBody.toByteArray(),
            timestamp = 1600000000000L
        )

        val transaction = session.recordRequest(request)
        assertEquals("tx-123", transaction.id)
        assertEquals("POST", transaction.request.method)
        assertEquals("http://localhost:8080/search", transaction.request.url)
        assertNotNull(transaction.requestBodyPath)
        assertTrue(File(transaction.requestBodyPath!!).exists())

        // Verify databases flow lists the request transaction
        var currentTransactions = session.transactionsFlow.first()
        assertEquals(1, currentTransactions.size)
        assertEquals("tx-123", currentTransactions.first().id)

        // 2. Prepare and record response
        val responseBody = "{\"status\":\"success\",\"hits\":[1,2,3]}"
        val response = HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = listOf("Content-Type" to "application/json", "Server" to "Mock"),
            body = responseBody.toByteArray(),
            timestamp = 1600000000250L
        )

        val updated = session.recordResponse("tx-123", response, durationMs = 250L)
        assertTrue(updated)

        // Verify mapped data from database
        currentTransactions = session.transactionsFlow.first()
        assertEquals(1, currentTransactions.size)
        
        val updatedTx = currentTransactions.first()
        assertEquals(250L, updatedTx.durationMs)
        assertNotNull(updatedTx.response)
        assertEquals(200, updatedTx.response?.statusCode)
        assertEquals("OK", updatedTx.response?.statusText)
        assertEquals(responseBody, updatedTx.response?.body?.let { String(it) })
        assertNotNull(updatedTx.responseBodyPath)
        assertTrue(File(updatedTx.responseBodyPath!!).exists())

        // 3. Verify cURL command generation
        val curlCmd = CurlGenerator.generate(updatedTx)
        assertTrue(curlCmd.contains("curl -X POST \"http://localhost:8080/search\""))
        assertTrue(curlCmd.contains("-H \"Content-Type: application/json\""))
        assertTrue(curlCmd.contains("-d \"{\\\"query\\\":\\\"KNet\\\"}\""))

        // 4. Verify HTTP Archive (HAR) 1.2 Export
        val harJson = HTTPArchiveExporter.export(currentTransactions)
        assertTrue(harJson.contains("\"version\":\"1.2\""))
        assertTrue(harJson.contains("\"name\":\"KNet Proxy\""))
        assertTrue(harJson.contains("\"url\":\"http://localhost:8080/search\""))
        assertTrue(harJson.contains("\"status\":200"))
        assertTrue(harJson.contains("KNet"))
        assertTrue(harJson.contains("success"))

        // 5. Verify clear operations
        session.clearSession()
        assertTrue(session.transactionsFlow.first().isEmpty())
        
        val cacheFolder = File(tempDir, "payloads")
        val cacheFiles = cacheFolder.listFiles() ?: emptyArray()
        assertTrue(cacheFiles.isEmpty(), "Cache directory should be empty after clearSession")
    }
}
