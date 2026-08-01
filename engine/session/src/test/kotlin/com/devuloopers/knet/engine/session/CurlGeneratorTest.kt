package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.export.CurlGenerator
import kotlin.test.Test
import kotlin.test.assertTrue

class CurlGeneratorTest {

    @Test
    fun testGenerateCurlCommand() {
        val req = TestFixtures.createHttpRequestDto(
            url = "https://api.example.com/v1/login",
            method = "POST",
            headers = listOf("Content-Type" to "application/json"),
            body = """{"user":"admin"}"""
        )
        val tx = TestFixtures.createHttpTransaction(request = req)

        val curl = CurlGenerator.generate(tx)
        assertTrue(curl.startsWith("curl -X POST \"https://api.example.com/v1/login\""))
        assertTrue(curl.contains("-H \"Content-Type: application/json\""))
        assertTrue(curl.contains("-d \"{\\\"user\\\":\\\"admin\\\"}\""))
    }
}
