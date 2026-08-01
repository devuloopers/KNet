package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.network.model.HttpRequest
import io.netty.util.AttributeKey

/**
 * Centralized Netty channel attribute key definitions.
 */
object ChannelAttributes {
    val REQUEST_ATTR: AttributeKey<HttpRequest> = AttributeKey.valueOf("knet.request")
    val HOST_ATTR: AttributeKey<String> = AttributeKey.valueOf("knet.host")
    val SSL_ATTR: AttributeKey<Boolean> = AttributeKey.valueOf("knet.ssl")
}
