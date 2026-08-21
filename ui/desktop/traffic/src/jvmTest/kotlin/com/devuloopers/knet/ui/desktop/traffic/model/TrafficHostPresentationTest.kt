package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.traffic.model.http.HttpScheme
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficHostPresentationTest {
    @Test
    fun `http default port is omitted`() {
        assertEquals(
            "play.googleapis.com",
            "play.googleapis.com:80".toTrafficHostLabel(HttpScheme.fromToken("http")),
        )
    }

    @Test
    fun `https default port is omitted`() {
        assertEquals(
            "api.example.com",
            "api.example.com:443".toTrafficHostLabel(HttpScheme.fromToken("https")),
        )
    }

    @Test
    fun `non-default ports remain visible`() {
        assertEquals(
            "localhost:8080",
            "localhost:8080".toTrafficHostLabel(HttpScheme.fromToken("http")),
        )
        assertEquals(
            "api.example.com:8443",
            "api.example.com:8443".toTrafficHostLabel(HttpScheme.fromToken("https")),
        )
    }

    @Test
    fun `default port is only omitted for its matching scheme`() {
        assertEquals(
            "api.example.com:443",
            "api.example.com:443".toTrafficHostLabel(HttpScheme.fromToken("http")),
        )
        assertEquals(
            "api.example.com:80",
            "api.example.com:80".toTrafficHostLabel(HttpScheme.fromToken("https")),
        )
    }

    @Test
    fun `bracketed IPv6 authority omits only its default port`() {
        assertEquals(
            "[2001:db8::1]",
            "[2001:db8::1]:443".toTrafficHostLabel(HttpScheme.fromToken("https")),
        )
        assertEquals(
            "[2001:db8::1]:8443",
            "[2001:db8::1]:8443".toTrafficHostLabel(HttpScheme.fromToken("https")),
        )
    }
}
