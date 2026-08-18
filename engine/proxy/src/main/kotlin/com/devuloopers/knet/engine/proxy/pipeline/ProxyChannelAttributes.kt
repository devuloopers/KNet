package com.devuloopers.knet.engine.proxy.pipeline

import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import io.netty.util.AttributeKey

/** Cross-handler channel attributes owned by the proxy transport module. */
object ProxyChannelAttributes {
    val REQUEST_CONTEXT: AttributeKey<ProxyRequestContext> = AttributeKey.valueOf("knet.request")
}
