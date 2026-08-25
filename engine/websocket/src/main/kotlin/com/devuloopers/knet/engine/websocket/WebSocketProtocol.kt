package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestHead
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** RFC 6455 handshake recognition shared by WebSocket engine contributions. */
object WebSocketProtocol {
    /** Returns whether a canonical request is a supported HTTP/1.1 WebSocket Upgrade handshake. */
    fun isHandshake(request: HttpRequestSnapshot): Boolean = isHandshake(request.head)

    /** Returns whether a canonical request head is a supported WebSocket Upgrade handshake. */
    @OptIn(ExperimentalEncodingApi::class)
    fun isHandshake(request: RequestHead): Boolean {
        if (request.method != HttpMethod.GET) return false
        val upgrade = header(request.headers, UPGRADE)
        val connection = request.headers
            .filter { header -> header.name.value.equals(CONNECTION, ignoreCase = true) }
            .flatMap { header -> header.value.split(',') }
            .map(String::trim)
        val key = header(request.headers, KEY)
        val validKey = key != null && runCatching { Base64.decode(key.trim()).size == KEY_BYTES }.getOrDefault(false)
        return upgrade.equals(WEBSOCKET, ignoreCase = true) &&
            connection.any { token -> token.equals(UPGRADE, ignoreCase = true) } &&
            header(request.headers, VERSION) == SUPPORTED_VERSION &&
            validKey
    }

    /** Returns the requested subprotocol values in client preference order. */
    fun requestedSubprotocols(request: RequestHead): List<String> =
        header(request.headers, SUBPROTOCOL)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    /** Returns whether per-message deflate was offered by the client. */
    fun offersPerMessageDeflate(request: RequestHead): Boolean =
        header(request.headers, EXTENSIONS)
            ?.split(',')
            ?.any { extension ->
                extension.substringBefore(';').trim().equals(PER_MESSAGE_DEFLATE, ignoreCase = true)
            } == true

    /** Looks up one case-insensitive canonical header. */
    fun header(headers: List<HeaderField>, name: String): String? = headers.firstOrNull { header ->
        header.name.value.equals(name, ignoreCase = true)
    }?.value

    private const val CONNECTION: String = "connection"
    private const val EXTENSIONS: String = "sec-websocket-extensions"
    private const val KEY: String = "sec-websocket-key"
    private const val KEY_BYTES: Int = 16
    private const val PER_MESSAGE_DEFLATE: String = "permessage-deflate"
    private const val SUBPROTOCOL: String = "sec-websocket-protocol"
    private const val SUPPORTED_VERSION: String = "13"
    private const val UPGRADE: String = "upgrade"
    private const val VERSION: String = "sec-websocket-version"
    private const val WEBSOCKET: String = "websocket"
}
