package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying collection and saved-request persistence mapping accuracy.
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
}
