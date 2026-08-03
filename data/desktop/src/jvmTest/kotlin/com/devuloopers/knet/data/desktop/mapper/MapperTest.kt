package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying [CollectionMapper], [RequestMapper], and [TransactionMapper] mapping accuracy.
 */
class MapperTest {

    @Test
    fun testCollectionMapperMapsEntityToDomain() {
        val entity = CollectionEntity(id = "c-100", name = "Test Suite")
        val folderEntity = CollectionFolderEntity(id = "f-10", collectionId = "c-100", name = "Auth")

        val domain = CollectionMapper.mapEntityToDomain(entity, listOf(folderEntity))

        assertEquals("c-100", domain.id)
        assertEquals("Test Suite", domain.name)
        assertEquals(1, domain.folders.size)
        assertEquals("Auth", domain.folders[0].name)
    }

    @Test
    fun testRequestMapperMapsEntityToDomainAndBack() {
        val entity = SavedRequestEntity(
            id = "req-1",
            collectionId = "c-100",
            folderId = "f-10",
            name = "Get User",
            method = "GET",
            url = "https://api.knet.dev/user",
            headersJson = "Accept:application/json",
            bodyContent = "",
            bodyType = "NONE",
            preRequestScript = "",
            testScript = "",
            scriptLanguage = "KOTLIN"
        )

        val domain = RequestMapper.mapEntityToDomain(entity)
        assertEquals("req-1", domain.id)
        assertEquals("Get User", domain.name)
        assertEquals(HttpMethod.GET, domain.method)
        assertEquals("https://api.knet.dev/user", domain.url)

        val entityBack = RequestMapper.mapDomainToEntity(domain, "c-100")
        assertEquals("req-1", entityBack.id)
        assertEquals("GET", entityBack.method)
    }

    @Test
    fun testTransactionMapperMapsEntityToDomain() {
        val entity = HttpTransactionEntity(
            id = "tx-500",
            method = "POST",
            url = "https://api.knet.dev/login",
            requestHeadersJson = "Content-Type:application/json",
            requestBodyPath = null,
            requestBodySize = 120L,
            responseStatusCode = 200,
            responseStatusText = "OK",
            responseHeadersJson = "Content-Type:application/json;\nCache-Control:no-cache",
            responseBodyPath = null,
            responseBodySize = 450L,
            durationMs = 120L,
            timestamp = 1000L
        )

        val domain = TransactionMapper.mapEntityToDomain(entity)

        assertEquals("tx-500", domain.id)
        assertEquals("POST", domain.request.method)
        assertEquals(200, domain.response?.statusCode)
        assertEquals(2, domain.response?.headers?.size)
        assertEquals(120L, domain.requestBodySize, "Request body size mapping should propagate correctly to Domain")
        assertEquals(450L, domain.responseBodySize, "Response body size mapping should propagate correctly to Domain")

        val mappedEntity = TransactionMapper.mapDomainToEntity(domain)
        assertEquals(120L, mappedEntity.requestBodySize, "Request body size mapping should propagate correctly to Entity")
        assertEquals(450L, mappedEntity.responseBodySize, "Response body size mapping should propagate correctly to Entity")
    }
}
