package com.devuloopers.knet.traffic.model

import kotlin.test.*

/** Transport admission, not request headers, establishes temporary-decision client isolation. */
class TlsFallbackClientScopeTest {
    @Test fun `authorized device and ingress scopes remain distinct behind a shared loopback bridge`() {
        val first = tlsFallbackClientScope(IngressContext(IngressKind.CompanionDirect, ClientIdentity("a")), "127.0.0.1")
        assertNotNull(first)
        assertNotEquals(first, tlsFallbackClientScope(IngressContext(IngressKind.CompanionDirect, ClientIdentity("b")), "127.0.0.1"))
        assertNotEquals(first, tlsFallbackClientScope(IngressContext(IngressKind.CompanionRelay, ClientIdentity("a")), "127.0.0.1"))
        assertNull(tlsFallbackClientScope(IngressContext(IngressKind.AdbDevice), "127.0.0.1"))
    }
    @Test fun `local desktop network addresses and missing admission are separated without DNS lookups`() {
        assertEquals(TlsFallbackClientScope.LocalDesktop, tlsFallbackClientScope(IngressContext(IngressKind.Local), "::1"))
        val ingress = IngressContext(IngressKind.WifiLanClient)
        assertNotEquals(tlsFallbackClientScope(ingress, "192.0.2.1"), tlsFallbackClientScope(ingress, "192.0.2.2"))
        assertNotNull(tlsFallbackClientScope(ingress, "2001:db8::10"))
        assertNull(tlsFallbackClientScope(ingress, null))
        assertNull(tlsFallbackClientScope(ingress, "unverified.example"))
    }
}
