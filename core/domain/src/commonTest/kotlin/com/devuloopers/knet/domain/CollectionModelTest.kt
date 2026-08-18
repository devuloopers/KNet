package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.domain.collection.model.defaultHeaders
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionModelTest {

    @Test
    fun testDefaultHeadersSeeding() {
        val headers = defaultHeaders()
        assertEquals(6, headers.size)
        assertTrue(headers.all { it.isEnabled })
        assertTrue(headers.all { it.isAuto })

        val keys = headers.map { it.key }
        assertTrue(keys.contains("User-Agent"))
        assertTrue(keys.contains("Accept"))
        assertTrue(keys.contains("Accept-Encoding"))
        assertTrue(keys.contains("Connection"))
        assertTrue(keys.contains("Host"))
        assertTrue(keys.contains("KNet-Token"))
    }

    @Test
    fun testSavedApiRequestMethodString() {
        val getReq = SavedApiRequest(
            id = "1",
            name = "Get Request",
            method = HttpMethod.GET,
            url = "https://example.com"
        )
        assertEquals("GET", getReq.methodString)

        val customReq = SavedApiRequest(
            id = "2",
            name = "Custom Request",
            method = HttpMethod.fromToken("purge"),
            url = "https://example.com"
        )
        assertEquals("purge", customReq.methodString)
    }

    @Test
    fun testApiRequestAuthTypes() {
        val noneAuth: ApiRequestAuth = ApiRequestAuth.None
        assertEquals("No Auth", noneAuth.type)

        val inheritAuth: ApiRequestAuth = ApiRequestAuth.Inherit
        assertEquals("Inherit Auth", inheritAuth.type)

        val bearerAuth: ApiRequestAuth = ApiRequestAuth.Bearer("token-xyz")
        assertEquals("Bearer Token", bearerAuth.type)
        assertEquals("token-xyz", (bearerAuth as ApiRequestAuth.Bearer).token)

        val basicAuth: ApiRequestAuth = ApiRequestAuth.Basic("admin", "secret")
        assertEquals("Basic Auth", basicAuth.type)
        assertEquals("admin", (basicAuth as ApiRequestAuth.Basic).username)

        val apiKeyAuth: ApiRequestAuth = ApiRequestAuth.ApiKey(name = "X-Key", value = "val", location = "Header")
        assertEquals("API Key", apiKeyAuth.type)

        val oauth2Auth: ApiRequestAuth = ApiRequestAuth.OAuth2(token = "bearer-val")
        assertEquals("OAuth 2.0", oauth2Auth.type)

        val awsAuth: ApiRequestAuth = ApiRequestAuth.AwsSignature(accessKey = "ak", secretKey = "sk")
        assertEquals("AWS Signature", awsAuth.type)
    }

    @Test
    fun testCollectionFolderAndSuiteHierarchy() {
        val req1 = SavedApiRequest(id = "r1", name = "Login", method = HttpMethod.POST, url = "https://api.com/login")
        val req2 = SavedApiRequest(id = "r2", name = "Profile", method = HttpMethod.GET, url = "https://api.com/profile")

        val folder = CollectionFolder(
            id = "f1",
            name = "Authentication",
            isExpanded = true,
            requests = listOf(req1, req2)
        )

        val collection = ApiCollection(
            id = "c1",
            name = "User Service API",
            folders = listOf(folder)
        )

        assertEquals("c1", collection.id)
        assertEquals("User Service API", collection.name)
        assertEquals(1, collection.folders.size)
        assertEquals("f1", collection.folders[0].id)
        assertEquals(2, collection.folders[0].requests.size)
        assertEquals("Login", collection.folders[0].requests[0].name)
    }

    @Test
    fun testTestFixturesCollectionCreation() {
        val collection = TestFixtures.createCollection(id = "col-fixture", name = "Test Suite")
        assertEquals("col-fixture", collection.id)
        assertEquals("Test Suite", collection.name)
        assertEquals(1, collection.folders.size)
        assertEquals("Get Users", collection.folders[0].requests[0].name)
    }

    @Test
    fun testAssertionResultModel() {
        val passResult = ScriptAssertion(id = "a1", name = "Status code is 200", passed = true)
        val failResult = ScriptAssertion(id = "a2", name = "Response body has token", passed = false)

        assertTrue(passResult.passed)
        assertFalse(failResult.passed)
    }

    @Test
    fun testApiRequestScriptsDefaults() {
        val scripts = ApiRequestScripts()
        assertEquals("", scripts.preRequest)
        assertEquals("", scripts.test)
        assertEquals(ScriptLanguage.JAVASCRIPT, scripts.language)
    }
}
