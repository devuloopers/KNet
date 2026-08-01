package com.devuloopers.knet.core.http.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestBodyTypeTest {

    @Test
    fun testRequestBodyTypeEnumValues() {
        val types = RequestBodyType.entries.map { it.name }
        assertEquals(
            listOf("NONE", "JSON", "XML", "FORM_URLENCODED", "MULTIPART", "GRAPHQL", "RAW_TEXT"),
            types
        )
    }

    @Test
    fun testAuthTypeEnumValues() {
        val types = AuthType.entries.map { it.name }
        assertEquals(
            listOf("NONE", "BEARER_TOKEN", "BASIC_AUTH", "API_KEY"),
            types
        )
    }
}
