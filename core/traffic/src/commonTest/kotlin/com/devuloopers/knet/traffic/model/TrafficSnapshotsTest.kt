package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpMethod
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TrafficSnapshotsTest {

    @Test
    fun `display URL omits only the port matching the standard scheme default`() {
        fun request(scheme: StandardHttpScheme, port: Int): HttpRequestSnapshot = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.GET,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.Standard(scheme),
                    authority = Authority("api.example.test", port),
                    pathAndQuery = "/resource",
                ),
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                headers = emptyList(),
            ),
        )

        assertEquals("http://api.example.test/resource", request(StandardHttpScheme.HTTP, 80).displayUrl())
        assertEquals("https://api.example.test/resource", request(StandardHttpScheme.HTTPS, 443).displayUrl())
        assertEquals("https://api.example.test:8443/resource", request(StandardHttpScheme.HTTPS, 8443).displayUrl())
        assertEquals("http://api.example.test:443/resource", request(StandardHttpScheme.HTTP, 443).displayUrl())
    }

    @Test
    fun `breakpoint transport representations retain port separately and bracket IPv6`() {
        val request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.GET,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.Standard(StandardHttpScheme.HTTPS),
                    authority = Authority("2001:db8::1", 8443),
                    pathAndQuery = "/resource",
                ),
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
                headers = emptyList(),
            ),
        )

        assertEquals("https://[2001:db8::1]:8443/resource", request.absoluteUrl())
        assertEquals("https://[2001:db8::1]:8443/resource", request.displayUrl())
        assertEquals("https://[2001:db8::1]/resource", request.absoluteUrlWithoutPort())
        assertEquals(8443, request.destinationPort())
    }

    @Test
    fun `ordered repeated headers remain distinct in shared request snapshot`() {
        val headers = listOf(
            HeaderField(HeaderName("Cookie"), "first=1"),
            HeaderField(HeaderName("Cookie"), "second=2"),
        )
        val request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.Standard(StandardHttpMethod.GET),
                target = RequestTarget.Origin("/resource"),
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                headers = headers,
            ),
        )

        assertEquals(headers, request.head.headers)
        assertEquals(2, request.head.headers.count { it.name.value == "Cookie" })
    }

    @Test
    fun `custom HTTP method remains extension safe`() {
        val method = HttpMethod.fromToken("PURGE")

        assertIs<HttpMethod.Custom>(method)
        assertEquals("PURGE", method.token)
    }

    @Test
    fun `standard HTTP method token is normalized to canonical value`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromToken("get"))
        assertEquals("GET", HttpMethod.fromToken("get").token)
    }

    @Test
    fun `custom HTTP method preserves its case`() {
        assertEquals("customMethod", HttpMethod.fromToken("customMethod").token)
    }

    @Test
    fun `HTTP method rejects whitespace and control characters`() {
        assertFailsWith<IllegalArgumentException> { HttpMethod.fromToken("PROPFIND ") }
        assertFailsWith<IllegalArgumentException> { HttpMethod.fromToken("GET\r\nInjected") }
    }

    @Test
    fun `protocol and scheme factories preserve extension tokens`() {
        val protocol = ApplicationProtocol.fromToken("HTTP/4")
        val scheme = HttpScheme.fromToken("web+secure")

        assertIs<ApplicationProtocol.Custom>(protocol)
        assertEquals("HTTP/4", protocol.token)
        assertIs<HttpScheme.Custom>(scheme)
        assertEquals("web+secure", scheme.token)
    }

    @Test
    fun `wire aliases normalize to canonical http two and three protocols`() {
        assertEquals(
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
            ApplicationProtocol.fromToken("h2"),
        )
        assertEquals(
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
            ApplicationProtocol.fromToken("h2c"),
        )
        assertEquals(
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_3),
            ApplicationProtocol.fromToken("h3"),
        )
    }

    @Test
    fun `custom request target rejects header injection characters`() {
        assertFailsWith<IllegalArgumentException> {
            RequestTarget.Custom("/safe\r\nInjected: true")
        }
    }

    @Test
    fun `captured body is represented by reference instead of payload bytes`() {
        val bodyRef = BodyRef(
            id = BodyId("body-1"),
            observedBytes = 128L,
            storedBytes = 128L,
            outcome = BodyCaptureOutcome.Complete,
        )
        val request = HttpRequestSnapshot(
            head = RequestHead(
                method = HttpMethod.Standard(StandardHttpMethod.POST),
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.Standard(StandardHttpScheme.HTTPS),
                    authority = com.devuloopers.knet.traffic.model.http.Authority("example.test", 443),
                    pathAndQuery = "/submit",
                ),
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
                headers = emptyList(),
            ),
            body = MessageBodyRef.Available(bodyRef),
        )
        val exchange = HttpExchangeSnapshot(
            id = ExchangeId("exchange-1"),
            request = request,
            state = ExchangeState.REQUEST_COMPLETE,
            startedAtEpochMillis = 1L,
        )

        assertEquals(bodyRef, (exchange.request.body as MessageBodyRef.Available).body)
    }

    @Test
    fun `invalid authority port is rejected at the shared boundary`() {
        assertFailsWith<IllegalArgumentException> {
            com.devuloopers.knet.traffic.model.http.Authority("example.test", 70_000)
        }
    }
}
