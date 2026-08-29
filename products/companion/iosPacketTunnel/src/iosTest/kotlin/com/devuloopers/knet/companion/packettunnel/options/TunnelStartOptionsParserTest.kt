package com.devuloopers.knet.companion.packettunnel.options

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalEncodingApi::class)
class TunnelStartOptionsParserTest {
    @Test
    fun parsesValidatedIpv4Options() {
        val parsed = TunnelStartOptionsParser.parse(validOptions())

        assertEquals("192.168.1.25", parsed.proxyHost)
        assertEquals(IpAddressFamily.IPV4, parsed.proxyAddressFamily)
        assertEquals(8080.toUShort(), parsed.proxyPort)
    }

    @Test
    fun parsesValidatedIpv6Options() {
        val parsed = TunnelStartOptionsParser.parse(validOptions().toMutableMap().apply {
            this["proxyHost"] = "fd00::25"
        })

        assertEquals(IpAddressFamily.IPV6, parsed.proxyAddressFamily)
    }

    @Test
    fun rejectsHostnameInsteadOfLiteralAddress() {
        assertInvalid(validOptions().toMutableMap().apply { this["proxyHost"] = "desktop.local" })
    }

    @Test
    fun rejectsMismatchedRootFingerprint() {
        assertInvalid(validOptions().toMutableMap().apply { this["rootSha256"] = "0".repeat(64) })
    }

    @Test
    fun rejectsUnsafeAuthorizationHeader() {
        assertInvalid(validOptions().toMutableMap().apply { this["authorization"] = "Bearer token\r\nInjected: true" })
    }

    @Test
    fun rejectsNonCanonicalDesktopId() {
        assertInvalid(validOptions().toMutableMap().apply {
            this["desktopId"] = "87961B5C-08F5-4037-8C58-CA54135D6FE7"
        })
    }

    @Test
    fun rejectsUnknownUnsupportedTrafficPolicy() {
        assertInvalid(validOptions().toMutableMap().apply { this["unsupportedPolicy"] = "BYPASS" })
    }

    private fun assertInvalid(options: Map<Any?, *>) {
        val error = assertFailsWith<TunnelException> { TunnelStartOptionsParser.parse(options) }
        assertEquals(TunnelFailure.INVALID_START_OPTIONS, error.failure)
    }

    private fun validOptions(): Map<Any?, Any> {
        val root = byteArrayOf(0x30, 0x03, 0x01, 0x02, 0x03)
        return mapOf(
            "schemaVersion" to "1",
            "desktopId" to "87961b5c-08f5-4037-8c58-ca54135d6fe7",
            "proxyHost" to "192.168.1.25",
            "proxyPort" to 8080,
            "authorization" to "Bearer test-token",
            "rootCertificate" to Base64.encode(root),
            "rootSha256" to root.sha256Hex(),
            "transportSha256" to "1".repeat(64),
            "unsupportedPolicy" to "REJECT",
        )
    }
}
