package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * 2-Stage Priority Dispatcher for payload format resolution.
 *
 * Stage 1: Fast O(1) Header Lookup (Content-Type matching for 95% of traffic).
 * Stage 2: Structural Inspection (Priority fallback matching for vague or missing headers).
 */
object BodyFormatterRegistry {
    private val jsonFormatter = JsonBodyFormatter()
    private val webChannelFormatter = WebChannelStreamFormatter(jsonFormatter)
    private val sseFormatter = SseStreamFormatter()
    private val formDataFormatter = FormDataBodyFormatter()
    private val protobufFormatter = ProtobufBinaryFormatter()
    private val imageFormatter = ImageBodyFormatter()
    private val htmlFormatter = HtmlBodyFormatter()
    private val xmlFormatter = XmlBodyFormatter()
    private val cborFormatter = CborBodyFormatter()
    private val msgpackFormatter = MessagePackBodyFormatter()
    private val jsFormatter = JsBodyFormatter()
    private val cssFormatter = CssBodyFormatter()
    private val grpcWebFormatter = GrpcWebBodyFormatter(jsonFormatter)
    private val graphQLFormatter = GraphQLBodyFormatter()
    private val plainTextFormatter = PlainTextBodyFormatter()

    private val formatters: List<BodyFormatter> = listOf(
        protobufFormatter,
        imageFormatter,
        webChannelFormatter,
        sseFormatter,
        graphQLFormatter,
        jsonFormatter,
        formDataFormatter,
        xmlFormatter,
        cborFormatter,
        msgpackFormatter,
        jsFormatter,
        cssFormatter,
        grpcWebFormatter,
        htmlFormatter,
        plainTextFormatter
    ).sortedByDescending { it.priority }

    /**
     * Resolves the strongly-typed [BodyFormat] for a given payload using the 2-stage dispatcher.
     */
    fun resolveFormat(headers: Map<String, String>, bodyText: String): BodyFormat {
        val trimmed = bodyText.trim()

        // Stage 1: Fast Header Lookup (Header matching happens even for empty body strings)
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()

        when {
            mime.contains("graphql") -> return graphQLFormatter.format(headers, trimmed)
            mime.startsWith("image/") -> return imageFormatter.format(headers, trimmed)
            mime.contains("x-www-form-urlencoded") -> return formDataFormatter.format(headers, trimmed)
            mime.contains("event-stream") -> return sseFormatter.format(headers, trimmed)
            mime.contains("grpc-web") || mime.contains("grpc-web-text") -> return grpcWebFormatter.format(headers, trimmed)
            mime.contains("grpc") || mime.contains("channel") -> return webChannelFormatter.format(headers, trimmed)
            mime.contains("proto") -> return protobufFormatter.format(headers, trimmed)
            mime.contains("json") -> {
                if (graphQLFormatter.matches(headers, trimmed)) return graphQLFormatter.format(headers, trimmed)
                return jsonFormatter.format(headers, trimmed)
            }
            mime.contains("cbor") -> return cborFormatter.format(headers, trimmed)
            mime.contains("msgpack") || mime.contains("messagepack") -> return msgpackFormatter.format(headers, trimmed)
            mime.contains("xml") -> return xmlFormatter.format(headers, trimmed)
            mime.contains("html") -> return htmlFormatter.format(headers, trimmed)
            mime.contains("javascript") -> return jsFormatter.format(headers, trimmed)
            mime.contains("css") -> return cssFormatter.format(headers, trimmed)
        }

        if (trimmed.isEmpty()) return BodyFormat.RawText("")

        // Stage 2: Structural Inspection Fallback
        val matchedFormatter = formatters.firstOrNull { it.matches(headers, trimmed) } ?: plainTextFormatter
        return matchedFormatter.format(headers, trimmed)
    }

    /**
     * Formats a raw payload string into human-readable formatted text.
     */
    fun prettyPrintBody(headers: Map<String, String>, bodyText: String): String {
        return when (val format = resolveFormat(headers, bodyText)) {
            is BodyFormat.GraphQL -> {
                val opName = if (!format.operationName.isNullOrEmpty()) " (${format.operationName})" else ""
                val varsStr = if (format.variablesJson.isNotEmpty()) "\n\n# Variables / Arguments:\n${format.variablesJson}" else ""
                "# GraphQL ${format.operationType}$opName:\n${format.queryText}$varsStr"
            }
            is BodyFormat.Json -> format.formattedText
            is BodyFormat.JsonStream -> format.frames.joinToString("\n\n")
            is BodyFormat.FormData -> format.pairs.joinToString("\n") { "${it.first} = ${it.second}" }
            is BodyFormat.SseStream -> format.events.joinToString("\n")
            is BodyFormat.Protobuf -> format.descriptor
            is BodyFormat.Image -> format.label
            is BodyFormat.Html -> format.formattedText
            is BodyFormat.Xml -> format.formattedText
            is BodyFormat.Cbor -> format.formattedText
            is BodyFormat.Js -> format.formattedText
            is BodyFormat.Css -> format.formattedText
            is BodyFormat.GrpcWeb -> {
                format.frames.joinToString("\n\n") { frame ->
                    val type = if (frame.isTrailer) "=== gRPC-Web Trailer ===" else "=== gRPC-Web Data Frame ==="
                    "$type\n${frame.decodedJsonOrText}"
                }
            }
            is BodyFormat.RawText -> format.text
        }
    }
}
