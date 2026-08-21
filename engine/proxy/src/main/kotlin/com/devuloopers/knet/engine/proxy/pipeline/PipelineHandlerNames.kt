package com.devuloopers.knet.engine.proxy.pipeline

/**
 * Centralized constant identifiers for Netty [io.netty.channel.ChannelPipeline] handler registrations
 * and buffer configurations across KNet's proxy engine and interception layers.
 */
object PipelineHandlerNames {

    /** Inbound read-idle timeout handler. */
    const val READ_TIMEOUT = "readTimeout"

    /** Outbound write-idle timeout handler. */
    const val WRITE_TIMEOUT = "writeTimeout"

    /**
     * Inbound TLS decryption and outbound TLS encryption handler (`io.netty.handler.ssl.SslHandler`).
     *
     * Positioned first in the pipeline when decrypting HTTPS traffic.
     */
    const val SSL = "ssl"

    /** TLS application-protocol negotiation handler selecting HTTP/1 or HTTP/2. */
    const val ALPN = "alpn"

    /** Connection-scoped HTTP/2 frame codec and stream multiplexer. */
    const val HTTP2_CODEC = "http2Codec"

    /** Connection-scoped dispatcher that creates one child channel per HTTP/2 stream. */
    const val HTTP2_MULTIPLEX = "http2Multiplex"

    /** Stream-scoped adapter between HTTP/2 frames and canonical Netty HTTP objects. */
    const val HTTP2_STREAM_CODEC = "http2StreamCodec"

    /**
     * HTTP protocol encoder/decoder (`io.netty.handler.codec.http.HttpServerCodec` for server channels,
     * or `io.netty.handler.codec.http.HttpClientCodec` for outbound client channels).
     *
     * Decodes raw/decrypted bytes into HTTP request/response objects and encodes HTTP objects to bytes.
     */
    const val HTTP_CODEC = "httpCodec"

    /**
     * Temporary downstream HTTP chunk aggregator (`io.netty.handler.codec.http.HttpObjectAggregator`).
     *
     * Installed only when desktop composition reports an active breakpoint that still requires a
     * full request or response. Ordinary traffic never installs it and remains incremental.
     */
    const val HTTP_AGGREGATOR = "httpAggregator"

    /** Overflow-safe selective aggregation installed by an optional inspection adapter. */
    const val SELECTIVE_HTTP_AGGREGATOR = "selectiveHttpAggregator"

    /**
     * Primary proxy inbound request handler (streaming or bounded breakpoint variant).
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
     * Maximum request size accepted by the opt-in downstream breakpoint aggregator.
     */
    const val MAX_CONTENT_LENGTH_BYTES: Int = 10 * 1024 * 1024
}
