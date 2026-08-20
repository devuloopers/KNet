package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestBodyField
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.RequestCookie
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.RequestQueryParameter
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.clientNetwork.model.RawBodyFormat
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.scripting.model.ScriptLanguage
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
            headersJson = """[{"key":"Accept","value":"application/json","enabled":true,"auto":false}]""",
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
        assertEquals(listOf(RequestHeader("Accept", "application/json")), domain.headers)

        val entityBack = RequestMapper.mapDomainToEntity(domain, "c-100")
        assertEquals("req-1", entityBack.id)
        assertEquals("GET", entityBack.method)
    }

    @Test
    fun testRequestMapperRoundTripPreservesCompleteAuthoredDocument() {
        val request = SavedApiRequest(
            id = "req-complete",
            name = "Create user",
            nameOrigin = RequestNameOrigin.GENERATED,
            method = HttpMethod.POST,
            url = "https://api.knet.dev/users",
            queryParameters = listOf(
                RequestQueryParameter("include", "profile;activity", isEnabled = false)
            ),
            headers = listOf(
                RequestHeader("X-Trace", "region:west;segment=a", isEnabled = false, isAuto = true)
            ),
            cookies = listOf(RequestCookie("session", "a=b;c=d", isEnabled = false)),
            body = ApiRequestBody(
                content = "raw-content",
                type = RequestBodyType.FORM_DATA,
                rawFormat = RawBodyFormat.XML,
                formDataFields = listOf(
                    ApiRequestBodyField("field-1", "display:name", "Ada;Lovelace", isEnabled = false)
                ),
                urlEncodedFields = listOf(
                    ApiRequestBodyField("field-2", "page", "1", isEnabled = true)
                )
            ),
            auth = ApiRequestAuth.Basic("api-user", "secret:value"),
            scripts = ApiRequestScripts("before()", "verify()", ScriptLanguage.KOTLIN),
            expectedStatus = 201
        )

        val entity = RequestMapper.mapDomainToEntity(request, "collection-1", "folder-1")
        val restored = RequestMapper.mapEntityToDomain(entity)

        assertEquals(request, restored)
        assertEquals("collection-1", entity.collectionId)
        assertEquals("folder-1", entity.folderId)
    }
}
