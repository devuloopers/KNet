package com.devuloopers.knet.engine.proxy.tls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TlsServerNameTest {
    @Test
    fun `missing SNI falls back to CONNECT host`() {
        assertEquals("192.0.2.10", TlsServerName.select(null, "192.0.2.10"))
    }

    @Test
    fun `valid SNI is normalized and selected independently from CONNECT host`() {
        assertEquals(
            "mobile-api.example.com",
            TlsServerName.select("Mobile-API.Example.COM", "192.0.2.10"),
        )
    }

    @Test
    fun `malformed presented SNI is rejected`() {
        listOf(
            "",
            "contains space.example",
            "-starts-with-hyphen.example",
            "ends-with-hyphen-.example",
            "double..dot.example",
            "label_${"x".repeat(10)}.example",
            "x".repeat(64) + ".example",
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) {
                TlsServerName.select(invalid, "192.0.2.10")
            }
        }
    }
}
