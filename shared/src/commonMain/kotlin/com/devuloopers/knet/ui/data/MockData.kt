package com.devuloopers.knet.ui.data

/**
 * Represents a simulated HTTP transaction captured by KNet.
 *
 * Fully documented according to the repository's KDoc standards.
 *
 * @property id The unique ID of the transaction.
 * @property method The HTTP method (GET, POST, etc.).
 * @property host The target host domain (e.g. api.example.com).
 * @property path The request URI path (e.g. /v1/login).
 * @property status The HTTP status code (200, 101, 304, etc.).
 * @property statusText The text representation of the status (e.g. OK).
 * @property time The timestamp string.
 * @property size The display size of the payload (e.g. 1.2 KB).
 * @property dateGroup The group classification (e.g. "Today - May 23, 2025").
 * @property requestBody The pretty-formatted request body content.
 * @property responseBody The pretty-formatted response body content.
 * @property queryParams The parsed map of query parameters (supports nested structures).
 * @property requestHeaders Map of HTTP request headers.
 * @property responseHeaders Map of HTTP response headers.
 * @property timingDnsMs DNS lookup time in milliseconds.
 * @property timingTcpMs TCP connection time in milliseconds.
 * @property timingTlsMs TLS handshake time in milliseconds.
 * @property timingTtfbMs Time To First Byte in milliseconds.
 * @property timingDownloadMs Content download time in milliseconds.
 */
data class MockTransaction(
    val id: Int,
    val method: String,
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val time: String,
    val size: String,
    val dateGroup: String,
    val requestBody: String,
    val responseBody: String,
    val queryParams: Map<String, Any>,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val timingDnsMs: Long,
    val timingTcpMs: Long,
    val timingTlsMs: Long,
    val timingTtfbMs: Long,
    val timingDownloadMs: Long
) {
    /** The total execution duration of the transaction in milliseconds. */
    val totalTimeMs: Long
        get() = timingDnsMs + timingTcpMs + timingTlsMs + timingTtfbMs + timingDownloadMs
}

/**
 * Represents an active interceptor or modifier rule.
 *
 * @property name The display name of the rule.
 * @property type The target context type (Request/Response).
 * @property condition Description of the triggering matching criteria.
 * @property action The action execution type.
 * @property enabled Whether this rule is currently active.
 * @property hitCount How many times this rule has matched.
 * @property lastHit Timestamp of the most recent match.
 */
data class MockRule(
    val name: String,
    val type: String,
    val condition: String,
    val action: String,
    val enabled: Boolean,
    val hitCount: Int,
    val lastHit: String
)

/**
 * Registry offering prepared dummy datasets matching KNet's user interface reference screenshot.
 */
object MockData {

    /**
     * Lists of captured transactions grouped by date segments.
     */
    val transactions: List<MockTransaction> = listOf(
        MockTransaction(
            id = 1,
            method = "POST",
            host = "api.example.com",
            path = "/v1/login",
            status = 200,
            statusText = "OK",
            time = "10:15:30",
            size = "1.2 KB",
            dateGroup = "Today - May 23, 2025",
            requestBody = """
                {
                  "email": "john.doe@example.com",
                  "password": "********",
                  "deviceId": "a1b2c3d4e5f6g7h8",
                  "remember": true
                }
            """.trimIndent(),
            responseBody = """
                {
                  "success": true,
                  "message": "Login successful",
                  "data": {
                    "user": {
                      "id": "123456",
                      "name": "John Doe",
                      "email": "john.doe@example.com",
                      "role": "user"
                    },
                    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                    "refreshToken": "def50280c1b7e4e4087d1a5e1d2e3f4",
                    "expiresIn": 3600
                  }
                }
            """.trimIndent(),
            queryParams = mapOf(
                "filter" to mapOf(
                    "user" to mapOf(
                        "name" to "john",
                        "age" to 25
                    ),
                    "address" to mapOf(
                        "city" to "Delhi",
                        "country" to "India"
                    )
                ),
                "page" to 1,
                "limit" to 10
            ),
            requestHeaders = mapOf(
                "Host" to "api.example.com",
                "User-Agent" to "KNet/1.0.0 (Desktop)",
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Content-Length" to "72",
                "Connection" to "keep-alive"
            ),
            responseHeaders = mapOf(
                "Content-Type" to "application/json",
                "Content-Length" to "1146",
                "Server" to "Nginx/1.24.0",
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-store",
                "Date" to "Thu, 23 May 2025 10:15:30 GMT"
            ),
            timingDnsMs = 2,
            timingTcpMs = 15,
            timingTlsMs = 23,
            timingTtfbMs = 21,
            timingDownloadMs = 22
        ),
        MockTransaction(
            id = 2,
            method = "GET",
            host = "api.example.com",
            path = "/v1/user/profile",
            status = 200,
            statusText = "OK",
            time = "10:15:24",
            size = "2.3 KB",
            dateGroup = "Today - May 23, 2025",
            requestBody = "",
            responseBody = """
                {
                  "name": "John Doe",
                  "email": "john.doe@example.com",
                  "role": "user",
                  "membership": "premium"
                }
            """.trimIndent(),
            queryParams = emptyMap(),
            requestHeaders = mapOf("Host" to "api.example.com", "Authorization" to "Bearer eyJ..."),
            responseHeaders = mapOf("Content-Type" to "application/json"),
            timingDnsMs = 1,
            timingTcpMs = 10,
            timingTlsMs = 20,
            timingTtfbMs = 15,
            timingDownloadMs = 12
        ),
        MockTransaction(
            id = 3,
            method = "GET",
            host = "api.example.com",
            path = "/v1/notifications",
            status = 200,
            statusText = "OK",
            time = "10:15:25",
            size = "3.1 KB",
            dateGroup = "Today - May 23, 2025",
            requestBody = "",
            responseBody = "[]",
            queryParams = emptyMap(),
            requestHeaders = mapOf("Host" to "api.example.com"),
            responseHeaders = mapOf("Content-Type" to "application/json"),
            timingDnsMs = 1,
            timingTcpMs = 12,
            timingTlsMs = 22,
            timingTtfbMs = 18,
            timingDownloadMs = 15
        ),
        MockTransaction(
            id = 4,
            method = "WS",
            host = "ws.example.com",
            path = "/chat",
            status = 101,
            statusText = "Switching Protocols",
            time = "10:15:26",
            size = "0 B",
            dateGroup = "Today - May 23, 2025",
            requestBody = "",
            responseBody = "",
            queryParams = emptyMap(),
            requestHeaders = mapOf("Upgrade" to "websocket", "Connection" to "Upgrade"),
            responseHeaders = mapOf("Upgrade" to "websocket", "Connection" to "Upgrade"),
            timingDnsMs = 3,
            timingTcpMs = 18,
            timingTlsMs = 25,
            timingTtfbMs = 10,
            timingDownloadMs = 0
        ),
        MockTransaction(
            id = 5,
            method = "POST",
            host = "api.example.com",
            path = "/v1/upload",
            status = 200,
            statusText = "OK",
            time = "10:15:27",
            size = "1.8 KB",
            dateGroup = "Today - May 23, 2025",
            requestBody = "",
            responseBody = "{\"success\":true}",
            queryParams = emptyMap(),
            requestHeaders = mapOf("Content-Type" to "multipart/form-data"),
            responseHeaders = mapOf("Content-Type" to "application/json"),
            timingDnsMs = 1,
            timingTcpMs = 11,
            timingTlsMs = 21,
            timingTtfbMs = 30,
            timingDownloadMs = 40
        ),
        MockTransaction(
            id = 6,
            method = "GET",
            host = "cdn.example.com",
            path = "/assets/app.js",
            status = 304,
            statusText = "Not Modified",
            time = "10:15:28",
            size = "0 B",
            dateGroup = "Today - May 23, 2025",
            requestBody = "",
            responseBody = "",
            queryParams = emptyMap(),
            requestHeaders = mapOf("If-None-Match" to "W/\"5d25-1823\""),
            responseHeaders = mapOf("ETag" to "W/\"5d25-1823\""),
            timingDnsMs = 2,
            timingTcpMs = 8,
            timingTlsMs = 15,
            timingTtfbMs = 12,
            timingDownloadMs = 1
        ),
        MockTransaction(
            id = 7,
            method = "POST",
            host = "api.example.com",
            path = "/v1/payment",
            status = 400,
            statusText = "Bad Request",
            time = "10:15:29",
            size = "887 B",
            dateGroup = "Today - May 23, 2025",
            requestBody = "{\"amount\":-10}",
            responseBody = "{\"error\":\"Amount must be positive\"}",
            queryParams = emptyMap(),
            requestHeaders = mapOf("Content-Type" to "application/json"),
            responseHeaders = mapOf("Content-Type" to "application/json"),
            timingDnsMs = 1,
            timingTcpMs = 9,
            timingTlsMs = 19,
            timingTtfbMs = 14,
            timingDownloadMs = 5
        )
    )

    /**
     * Active configuration rules list.
     */
    val rules: List<MockRule> = listOf(
        MockRule(
            name = "Pause on /login",
            type = "Request",
            condition = "Path contains \"/login\"",
            action = "Breakpoint",
            enabled = true,
            hitCount = 12,
            lastHit = "10:15:30"
        ),
        MockRule(
            name = "Modify User-Agent",
            type = "Request",
            condition = "Host contains \"api.example.com\"",
            action = "Rewrite Header",
            enabled = true,
            hitCount = 34,
            lastHit = "10:14:58"
        ),
        MockRule(
            name = "Block Ads",
            type = "Request",
            condition = "Host contains \"doubleclick.net\"",
            action = "Drop",
            enabled = true,
            hitCount = 66,
            lastHit = "10:15:29"
        )
    )
}
