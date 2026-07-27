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

    /**
     * Verifies that session pruning automatically trims the oldest transactions
     * when the [KNetSession.MAX_PERSISTED_TRANSACTIONS] limit is exceeded.
     * After inserting 1005 records, exactly 1000 should remain, and the 5
     * oldest request payload `.bin` files should be deleted from disk.
     */
    @Test
    fun testPruneOldestTransactionsWhenLimitExceeded() = runBlocking {
        val limit = KNetSession.MAX_PERSISTED_TRANSACTIONS
        val totalInserts = limit + 5

        // Insert totalInserts transactions (each with a unique timestamp and request body)
        for (index in 1..totalInserts) {
            val request = HttpRequest(
                id = "prune-tx-$index",
                method = "GET",
                url = "http://localhost/item/$index",
                protocol = "HTTP/1.1",
                headers = listOf("Content-Type" to "text/plain"),
                body = "body-$index".toByteArray(),
                timestamp = 1600000000000L + index
            )
            session.recordRequest(request)
        }

        // Verify that exactly MAX_PERSISTED_TRANSACTIONS remain
        val remainingTransactions = session.transactionsFlow.first()
        assertEquals(
            limit,
            remainingTransactions.size,
            "Transaction count should be pruned to $limit, but was ${remainingTransactions.size}"
        )

        // Verify the 5 oldest transactions (prune-tx-1 through prune-tx-5) were pruned
        val remainingIds = remainingTransactions.map { it.id }.toSet()
        for (prunedIndex in 1..5) {
            val prunedId = "prune-tx-$prunedIndex"
            assertTrue(
                prunedId !in remainingIds,
                "Transaction '$prunedId' should have been pruned but is still present"
            )
        }

        // Verify the newest transactions survived
        for (survivingIndex in 6..totalInserts) {
            val survivingId = "prune-tx-$survivingIndex"
            assertTrue(
                survivingId in remainingIds,
                "Transaction '$survivingId' should have survived pruning but was deleted"
            )
        }

        // Verify disk payload files for pruned transactions are deleted
        val cacheFolder = File(tempDir, "payloads")
        for (prunedIndex in 1..5) {
            val prunedFile = File(cacheFolder, "prune-tx-${prunedIndex}_req.bin")
            assertTrue(
                !prunedFile.exists(),
                "Payload file '${prunedFile.name}' should have been deleted during pruning"
            )
        }

        // Verify disk payload files for surviving transactions still exist
        for (survivingIndex in 6..totalInserts) {
            val survivingFile = File(cacheFolder, "prune-tx-${survivingIndex}_req.bin")
            assertTrue(
                survivingFile.exists(),
                "Payload file '${survivingFile.name}' should still exist after pruning"
            )
        }
    }
}
