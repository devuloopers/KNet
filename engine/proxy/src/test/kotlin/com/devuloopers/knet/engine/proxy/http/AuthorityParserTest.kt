package com.devuloopers.knet.engine.proxy.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Tests strict parsing of proxy CONNECT targets and Host authorities. */
class AuthorityParserTest {

    /** Verifies ordinary host authorities use the supplied default port. */
    @Test
    fun `host without port uses default`() {
        val result = assertIs<AuthorityParseResult.Valid>(AuthorityParser.parse("example.com", 443))

        assertEquals(ParsedAuthority("example.com", 443), result.authority)
    }

    /** Verifies explicit ports override the default. */
    @Test
    fun `host with explicit port is preserved`() {
        val result = assertIs<AuthorityParseResult.Valid>(AuthorityParser.parse("example.com:8443", 443))

        assertEquals(ParsedAuthority("example.com", 8443), result.authority)
    }

    /** Verifies bracketed IPv6 targets preserve the address and parse their port. */
    @Test
    fun `bracketed ipv6 authority is parsed`() {
        val result = assertIs<AuthorityParseResult.Valid>(AuthorityParser.parse("[2001:db8::1]:9443", 443))

        assertEquals(ParsedAuthority("2001:db8::1", 9443), result.authority)
    }

    /** Verifies invalid or injectable authorities are rejected without an exception. */
    @Test
    fun `invalid authorities are rejected`() {
        assertIs<AuthorityParseResult.Invalid>(AuthorityParser.parse("example.com:70000", 443))
        assertIs<AuthorityParseResult.Invalid>(AuthorityParser.parse("example.com\r\nHost: attacker", 443))
        assertIs<AuthorityParseResult.Invalid>(AuthorityParser.parse("user@example.com", 443))
        assertIs<AuthorityParseResult.Invalid>(AuthorityParser.parse("[]:443", 443))
    }
}
