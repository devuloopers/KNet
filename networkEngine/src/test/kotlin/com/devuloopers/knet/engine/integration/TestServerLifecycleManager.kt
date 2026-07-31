package com.devuloopers.knet.engine.integration

import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress

/**
 * Thread-safe lifecycle manager for the HTTP testing server.
 *
 * Checks if a live testing server is running on [serverPort] (default: 9090).
 * If not running, it initializes an embedded JDK [HttpServer] responding to all test endpoints.
 */
object TestServerLifecycleManager {

    private const val DEFAULT_PORT = 9090
    private var embeddedServer: HttpServer? = null

    /**
     * Ensures that a test server is active on [serverPort] before running integration tests.
     *
     * @param serverPort Target HTTP port (default: 9090).
     */
    @Synchronized
    fun ensureServerRunning(serverPort: Int = DEFAULT_PORT) {
        if (isServerHealthy(serverPort)) {
            return
        }

        if (embeddedServer == null) {
            try {
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", serverPort), 0)

                // Route: GET /api/test/headers (echoes back request headers in body)
                server.createContext("/api/test/headers") { exchange ->
                    exchange.requestBody.readAllBytes()
                    val reqHeaders = exchange.requestHeaders
                    val headerEchoList = reqHeaders.entries.joinToString(", ") { "${it.key}: ${it.value.joinToString(";")}" }
                    val responseBody = """{"status":200, "headers":"$headerEchoList"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }

                // Route: GET /api/test/get
                server.createContext("/api/test/get") { exchange ->
                    exchange.requestBody.readAllBytes()
                    val responseBody = """{"status":200, "message":"GET OK"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }

                // Route: POST /api/test/post & /api/test/post/json
                val postHandler: (com.sun.net.httpserver.HttpExchange) -> Unit = { exchange ->
                    exchange.requestBody.readAllBytes()
                    val responseBody = """{"status":200, "message":"POST OK"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }
                server.createContext("/api/test/post", postHandler)
                server.createContext("/api/test/post/json", postHandler)

                // Route: PUT /api/test/put
                server.createContext("/api/test/put") { exchange ->
                    exchange.requestBody.readAllBytes()
                    val responseBody = """{"status":200, "message":"PUT OK"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }

                // Route: DELETE /api/test/delete
                server.createContext("/api/test/delete") { exchange ->
                    exchange.requestBody.readAllBytes()
                    val responseBody = """{"status":200, "message":"DELETE OK"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }

                // Route: PATCH /api/test/patch
                server.createContext("/api/test/patch") { exchange ->
                    exchange.requestBody.readAllBytes()
                    val responseBody = """{"status":200, "message":"PATCH OK"}"""
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = responseBody.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }

                server.executor = null
                server.start()
                embeddedServer = server
            } catch (exception: Exception) {
                // If port is bound elsewhere, fallback to health check
            }
        }
    }

    /**
     * Checks if the test server health endpoint responds with HTTP 200.
     *
     * @param serverPort Target HTTP port to check.
     * @return True if server is reachable and healthy.
     */
    fun isServerHealthy(serverPort: Int = DEFAULT_PORT): Boolean {
        return try {
            val url = java.net.URI.create("http://127.0.0.1:$serverPort/api/test/headers").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (exception: Exception) {
            false
        }
    }

    /**
     * Gracefully stops the embedded test server if it was started by this manager.
     */
    @Synchronized
    fun stopIfManaged() {
        embeddedServer?.stop(0)
        embeddedServer = null
    }
}
