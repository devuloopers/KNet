package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import java.net.URI

/** Installs bounded aggregation only for destinations whose semantic rules require a complete request body. */
class NetworkConditionRequestAggregator(
    private val engine: NetworkConditionEngine,
    private val semanticResolver: NetworkConditionSemanticResolver,
    maximumContentBytes: Int = DEFAULT_MAXIMUM_SEMANTIC_BODY_BYTES,
) : SelectiveHttpObjectAggregator(
    maximumContentBytes = maximumContentBytes,
    shouldAggregate = { context, message ->
        if (message !is HttpRequest || message.method() == HttpMethod.CONNECT) {
            false
        } else {
            resolveNetworkConditionDestination(context, message)?.let { destination ->
                val headerInput = NetworkConditionHttpInspectionInput(
                    request = networkConditionRequestSnapshot(context, message, destination),
                    requestBody = null,
                    requestBodyComplete = false,
                )
                semanticResolver.requiresHttpBodyInspection(
                    configuration = engine.configurationSnapshot(),
                    host = destination.host,
                    port = destination.port,
                    input = headerInput,
                )
            } == true
        }
    },
) {
    companion object {
        /** Maximum request bytes retained to identify one GraphQL operation before forwarding. */
        const val DEFAULT_MAXIMUM_SEMANTIC_BODY_BYTES: Int = 1_048_576
    }
}

/** Trusted decoded destination used consistently by aggregation and shaping handlers. */
internal data class NetworkConditionDestination(
    val host: String,
    val port: Int,
    val isSsl: Boolean,
    val relativeUri: String,
)

/** Resolves one decoded proxy request without trusting an unvalidated free-form editor value. */
internal fun resolveNetworkConditionDestination(
    context: ChannelHandlerContext,
    request: HttpRequest,
): NetworkConditionDestination? {
    val absolute = request.uri()
        .takeIf { value -> value.startsWith("http://") || value.startsWith("https://") }
        ?.let { value -> runCatching { URI.create(value) }.getOrNull() }
        ?.takeIf { uri -> uri.host != null }
    val isSsl = absolute?.scheme?.equals("https", ignoreCase = true) ?:
        (context.channel().attr(ProxyChannelAttributes.IS_SSL).get() == true ||
            context.pipeline().get(PipelineHandlerNames.SSL) != null)
    if (absolute != null) {
        val port = absolute.port.takeIf { value -> value in 1..65_535 } ?: if (isSsl) 443 else 80
        return NetworkConditionDestination(
            host = absolute.host,
            port = port,
            isSsl = isSsl,
            relativeUri = relativeRequestTarget(request.uri()),
        )
    }
    val authority = if (request.method() == HttpMethod.CONNECT) request.uri() else request.headers()["Host"]
    val fallbackPort = context.channel().attr(ProxyChannelAttributes.PORT).get() ?: if (isSsl) 443 else 80
    val parsed = authority?.let { value -> runCatching { URI.create("http://$value") }.getOrNull() }
    val host = parsed?.host
        ?: context.channel().attr(ProxyChannelAttributes.TLS_SERVER_NAME).get()
        ?: context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).get()
        ?: return null
    val port = parsed?.port?.takeIf { value -> value in 1..65_535 } ?: fallbackPort
    return NetworkConditionDestination(host, port, isSsl, relativeRequestTarget(request.uri()))
}

/** Maps one decoded request to the canonical immutable input used by semantic condition extensions. */
internal fun networkConditionInspectionInput(
    context: ChannelHandlerContext,
    request: FullHttpRequest,
    destination: NetworkConditionDestination,
): NetworkConditionHttpInspectionInput {
    val mapped = HttpMapper.mapRequestContext(
        nettyReq = request,
        isSsl = destination.isSsl,
        host = destination.host,
        port = destination.port,
        relativeUri = destination.relativeUri,
        protocolOverride = context.channel().attr(ProxyChannelAttributes.APPLICATION_PROTOCOL).get(),
    )
    val bytes = ByteArray(request.content().readableBytes())
    request.content().getBytes(request.content().readerIndex(), bytes)
    return NetworkConditionHttpInspectionInput(
        request = mapped.request,
        requestBody = NetworkConditionBody(bytes),
        requestBodyComplete = true,
    )
}

/** Maps a handshake request that has no retained body to a canonical semantic input. */
internal fun networkConditionRequestSnapshot(
    context: ChannelHandlerContext,
    request: HttpRequest,
    destination: NetworkConditionDestination,
): HttpRequestSnapshot = HttpMapper.mapRequestContext(
    nettyReq = request,
    isSsl = destination.isSsl,
    host = destination.host,
    port = destination.port,
    relativeUri = destination.relativeUri,
    protocolOverride = context.channel().attr(ProxyChannelAttributes.APPLICATION_PROTOCOL).get(),
).request

private fun relativeRequestTarget(uri: String): String =
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        runCatching {
            val parsed = URI.create(uri)
            val path = parsed.rawPath?.ifBlank { "/" } ?: "/"
            parsed.rawQuery?.let { query -> "$path?$query" } ?: path
        }.getOrDefault(uri)
    } else {
        uri
    }
