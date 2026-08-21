package com.devuloopers.knet.testingserver.http2

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/** Handles one independently multiplexed HTTP/2 stream and exposes bounded frame-level fixtures. */
internal class Http2LabStreamHandler(
    private val objectMapper: ObjectMapper,
) : SimpleChannelInboundHandler<Http2StreamFrame>() {
    private val requestBody = ByteArrayOutputStream()
    private var requestHeaders: Http2HeadersFrame? = null
    private var responseStarted = false

    override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
        when (frame) {
            is Http2HeadersFrame -> {
                if (requestHeaders == null) requestHeaders = frame
                if (frame.isEndStream) dispatch(context)
            }

            is Http2DataFrame -> {
                val readableBytes = frame.content().readableBytes()
                if (requestBody.size() + readableBytes > MAX_REQUEST_BODY_BYTES) {
                    writeTextResponse(context, status = 413, text = "request body exceeds lab limit")
                    return
                }
                val bytes = ByteArray(readableBytes)
                frame.content().getBytes(frame.content().readerIndex(), bytes)
                requestBody.write(bytes)
                if (frame.isEndStream) dispatch(context)
            }
        }
    }

    private fun dispatch(context: ChannelHandlerContext) {
        if (responseStarted) return
        responseStarted = true

        val headers = requestHeaders?.headers()
        val method = headers?.method()?.toString().orEmpty()
        val pathWithQuery = headers?.path()?.toString().orEmpty()
        val requestUri = runCatching { URI.create(pathWithQuery) }.getOrNull()
        val path = requestUri?.path ?: pathWithQuery.substringBefore('?')
        val query = parseQuery(requestUri?.rawQuery)

        when (path) {
            ECHO_PATH -> writeJsonResponse(
                context = context,
                value = mapOf(
                    "protocol" to "HTTP/2",
                    "method" to method,
                    "path" to path,
                    "bodyText" to requestBody.toByteArray().decodeToString(),
                ),
            )

            TRAILERS_PATH -> writeTrailersResponse(context)
            SLOW_STREAM_PATH -> writeSlowStream(
                context = context,
                label = query["label"].orEmpty().ifBlank { "stream" },
                chunkCount = query["chunks"].toBoundedInt(default = 3, range = 1..MAX_CHUNKS),
                delayMillis = query["delayMillis"].toBoundedLong(
                    default = DEFAULT_STREAM_DELAY_MILLIS,
                    range = 0L..MAX_STREAM_DELAY_MILLIS,
                ),
            )

            RESET_STREAM_PATH -> context.writeAndFlush(DefaultHttp2ResetFrame(Http2Error.CANCEL))
            GO_AWAY_PATH -> writeGoAway(context)
            LARGE_HEADERS_PATH -> writeLargeHeaders(
                context = context,
                requestedBytes = query["bytes"].toBoundedInt(
                    default = DEFAULT_LARGE_HEADER_BYTES,
                    range = 1..MAX_LARGE_HEADER_BYTES,
                ),
            )

            else -> writeTextResponse(context, status = 404, text = "unknown HTTP/2 lab fixture")
        }
    }

    private fun writeJsonResponse(context: ChannelHandlerContext, value: Any) {
        val bytes = objectMapper.writeValueAsBytes(value)
        writeResponse(context, status = 200, contentType = "application/json", bytes = bytes)
    }

    private fun writeTextResponse(context: ChannelHandlerContext, status: Int, text: String) {
        writeResponse(
            context = context,
            status = status,
            contentType = "text/plain; charset=utf-8",
            bytes = text.encodeToByteArray(),
        )
    }

    private fun writeResponse(
        context: ChannelHandlerContext,
        status: Int,
        contentType: String,
        bytes: ByteArray,
    ) {
        val headers = responseHeaders(context, status)
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
        context.write(DefaultHttp2HeadersFrame(headers, bytes.isEmpty()))
        if (bytes.isNotEmpty()) {
            context.write(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(bytes), true))
        }
        context.flush()
    }

    private fun writeTrailersResponse(context: ChannelHandlerContext) {
        val bytes = "body-before-trailers".encodeToByteArray()
        context.write(
            DefaultHttp2HeadersFrame(
                responseHeaders(context, 200).set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN),
                false,
            ),
        )
        context.write(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(bytes), false))
        context.writeAndFlush(
            DefaultHttp2HeadersFrame(
                DefaultHttp2Headers().set(TRAILER_NAME, TRAILER_VALUE),
                true,
            ),
        )
    }

    private fun writeSlowStream(
        context: ChannelHandlerContext,
        label: String,
        chunkCount: Int,
        delayMillis: Long,
    ) {
        context.writeAndFlush(
            DefaultHttp2HeadersFrame(
                responseHeaders(context, 200).set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN),
                false,
            ),
        )
        repeat(chunkCount) { index ->
            context.executor().schedule(
                {
                    if (context.channel().isActive) {
                        val bytes = "$label:${index + 1}\n".encodeToByteArray()
                        context.writeAndFlush(
                            DefaultHttp2DataFrame(
                                Unpooled.wrappedBuffer(bytes),
                                index == chunkCount - 1,
                            ),
                        )
                    }
                },
                delayMillis * index,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun writeGoAway(context: ChannelHandlerContext) {
        writeTextResponse(context, status = 200, text = "GOAWAY scheduled")
        context.channel().parent()
            .writeAndFlush(DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR).setExtraStreamIds(0))
            .addListener(ChannelFutureListener.CLOSE)
    }

    private fun writeLargeHeaders(context: ChannelHandlerContext, requestedBytes: Int) {
        val headers = responseHeaders(context, 200)
            .set(LARGE_HEADER_NAME, LARGE_HEADER_VALUE.repeat(requestedBytes))
        context.writeAndFlush(DefaultHttp2HeadersFrame(headers, true))
    }

    private fun responseHeaders(context: ChannelHandlerContext, status: Int): Http2Headers = DefaultHttp2Headers()
        .status(status.toString())
        .set("x-knet-protocol-fixture", "http2-tls")
        .set("x-knet-connection-id", context.channel().parent().id().asShortText())

    private fun parseQuery(rawQuery: String?): Map<String, String> = rawQuery
        ?.split('&')
        ?.mapNotNull { field ->
            val name = field.substringBefore('=', missingDelimiterValue = field)
            if (name.isBlank()) null else name to field.substringAfter('=', missingDelimiterValue = "")
        }
        ?.toMap()
        .orEmpty()

    private fun String?.toBoundedInt(default: Int, range: IntRange): Int =
        this?.toIntOrNull()?.coerceIn(range) ?: default

    private fun String?.toBoundedLong(default: Long, range: LongRange): Long =
        this?.toLongOrNull()?.coerceIn(range) ?: default

    private companion object {
        const val ECHO_PATH = "/lab/v1/http2/echo"
        const val TRAILERS_PATH = "/lab/v1/http2/trailers"
        const val SLOW_STREAM_PATH = "/lab/v1/http2/slow-stream"
        const val RESET_STREAM_PATH = "/lab/v1/http2/reset-stream"
        const val GO_AWAY_PATH = "/lab/v1/http2/goaway"
        const val LARGE_HEADERS_PATH = "/lab/v1/http2/large-headers"
        const val TRAILER_NAME = "x-knet-trailer"
        const val TRAILER_VALUE = "protocol-lab-trailer"
        const val LARGE_HEADER_NAME = "x-knet-large-header"
        const val LARGE_HEADER_VALUE = "x"
        const val MAX_REQUEST_BODY_BYTES = 1024 * 1024
        const val MAX_CHUNKS = 100
        const val DEFAULT_STREAM_DELAY_MILLIS = 25L
        const val MAX_STREAM_DELAY_MILLIS = 5_000L
        const val DEFAULT_LARGE_HEADER_BYTES = 4_096
        const val MAX_LARGE_HEADER_BYTES = 32_768
    }
}
