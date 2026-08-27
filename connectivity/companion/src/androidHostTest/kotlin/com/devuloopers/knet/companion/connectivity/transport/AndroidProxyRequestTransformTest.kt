package com.devuloopers.knet.companion.connectivity.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidProxyRequestTransformTest {
    @Test
    fun `origin-form HTTP request becomes standard absolute proxy form`() {
        val transformed = normalizeHttpProxyRequest(
            "GET /v1/items?q=one HTTP/1.1\r\nHost: api.example.test\r\n\r\n".encodeToByteArray(),
            destinationHost = "api.example.test",
            destinationPort = 80,
        )

        assertTrue(requireNotNull(transformed).decodeToString().startsWith(
            "GET http://api.example.test/v1/items?q=one HTTP/1.1\r\n",
        ))
    }

    @Test
    fun `non-default port and IPv6 authority remain unambiguous`() {
        val transformed = normalizeHttpProxyRequest(
            "GET /health HTTP/1.1\r\nHost: [2001:db8::1]:8080\r\n\r\n".encodeToByteArray(),
            destinationHost = "2001:db8::1",
            destinationPort = 8080,
        )

        assertTrue(requireNotNull(transformed).decodeToString().startsWith(
            "GET http://[2001:db8::1]:8080/health HTTP/1.1\r\n",
        ))
    }

    @Test
    fun `carrier replaces an injected proxy authorization header exactly once`() {
        val transformed = addAuthorization(
            (
                "GET http://example.test/ HTTP/1.1\r\n" +
                    "Proxy-Authorization: Bearer attacker:value\r\n" +
                    "Host: example.test\r\n\r\n"
            ).encodeToByteArray(),
            "Proxy-Authorization: Bearer device:credential\r\n",
        )?.decodeToString().orEmpty()

        assertEquals(1, Regex("Proxy-Authorization", RegexOption.IGNORE_CASE).findAll(transformed).count())
        assertTrue(transformed.contains("Bearer device:credential"))
    }

    @Test
    fun `folded or incomplete request headers fail closed`() {
        assertNull(
            addAuthorization(
                "GET / HTTP/1.1\r\n continuation\r\n\r\n".encodeToByteArray(),
                "Proxy-Authorization: Bearer device:credential\r\n",
            ),
        )
        assertNull(
            normalizeHttpProxyRequest(
                "GET / HTTP/1.1\r\nHost: example.test\r\n".encodeToByteArray(),
                destinationHost = "example.test",
                destinationPort = 80,
            ),
        )
    }
}
