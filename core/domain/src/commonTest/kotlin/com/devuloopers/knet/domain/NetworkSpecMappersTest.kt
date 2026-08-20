package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toNetworkRequestSpec
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toSavedApiRequest
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkSpecMappersTest {

    @Test
    fun `saved request converts through the editor spec without losing user fields`() {
        val savedRequest = SavedApiRequest(
            id = "request-1",
            name = "Update item",
            nameOrigin = RequestNameOrigin.GENERATED,
            method = HttpMethod.PUT,
            url = "https://api.knet.dev/v1/items?page=1",
            headers = listOf(RequestHeader("Authorization", "Bearer token")),
            body = ApiRequestBody(content = "{\"item\":\"test\"}", type = RequestBodyType.JSON),
        )

        val spec = savedRequest.toNetworkRequestSpec()
        val roundTripped = spec.toSavedApiRequest(
            id = savedRequest.id,
            name = savedRequest.name,
            nameOrigin = savedRequest.nameOrigin
        )

        assertEquals(HttpMethod.PUT, spec.method)
        assertEquals(RequestBodyType.JSON, spec.bodyType)
        assertEquals(listOf("page" to "1"), spec.queryParams)
        assertEquals(savedRequest.url, roundTripped.url)
        assertEquals(savedRequest.headers.first().key, roundTripped.headers.first().key)
        assertEquals(savedRequest.body.content, roundTripped.body.content)
        assertEquals(RequestNameOrigin.GENERATED, roundTripped.nameOrigin)
    }
}
