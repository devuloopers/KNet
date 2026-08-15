package com.devuloopers.knet.engine.proxy.pipeline

/**
 * Centralized constant identifiers for Netty [io.netty.channel.ChannelPipeline] handler registrations
 * and buffer configurations across KNet's proxy engine and interception layers.
 */
object PipelineHandlerNames {

    /**
     * Inbound TLS decryption and outbound TLS encryption handler (`io.netty.handler.ssl.SslHandler`).
     *
     * Positioned first in the pipeline when decrypting HTTPS traffic.
     */
    const val SSL = "ssl"

    /**
     * HTTP protocol encoder/decoder (`io.netty.handler.codec.http.HttpServerCodec` for server channels,
     * or `io.netty.handler.codec.http.HttpClientCodec` for outbound client channels).
     *
     * Decodes raw/decrypted bytes into HTTP request/response objects and encodes HTTP objects to bytes.
     */
    const val HTTP_CODEC = "httpCodec"

    /**
     * HTTP chunk aggregator (`io.netty.handler.codec.http.HttpObjectAggregator`).
     *
     * Buffers streaming HTTP chunks into cohesive `FullHttpRequest` / `FullHttpResponse` instances.
     */
    const val HTTP_AGGREGATOR = "httpAggregator"

    /**
     * Primary proxy inbound request handler (`com.devuloopers.knet.engine.proxy.handler.KNetProxyHandler`).
     *
     * Handles CONNECT handshakes, target host resolution, loop prevention, and outbound forwarding.
     */
    const val PROXY_HANDLER = "proxyHandler"

    /**
     * Outbound remote server client response handler (`com.devuloopers.knet.engine.proxy.handler.KNetOutboundHandler`).
     *
     * Streams responses from destination servers back to client channels while capturing timing and body bytes.
     */
    const val OUTBOUND_HANDLER = "outboundHandler"

    /**
     * Maximum allowed aggregated HTTP request/response payload size in bytes (10 MB).
     */
    const val MAX_CONTENT_LENGTH_BYTES: Int = 10 * 1024 * 1024
}
