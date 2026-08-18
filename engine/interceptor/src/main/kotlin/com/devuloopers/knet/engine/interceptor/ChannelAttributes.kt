package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import io.netty.util.AttributeKey

/**
 * Centralized Netty channel attribute key definitions.
 */
object ChannelAttributes {
    val REQUEST_CONTEXT = ProxyChannelAttributes.REQUEST_CONTEXT
    val HOST_ATTR: AttributeKey<String> = AttributeKey.valueOf("knet.host")
    val SSL_ATTR: AttributeKey<Boolean> = AttributeKey.valueOf("knet.ssl")
}
