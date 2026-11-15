package com.devuloopers.knet.data.desktop.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficDetailsFormatting
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter

/** Reuses bounded JSON/GraphQL formatting; copied details never mutate the persisted-query catalog. */
class DesktopTrafficDetailsFormatting : TrafficDetailsFormatting {
    /** Uses the bounded inspector JSON formatter, preserving non-JSON evidence unchanged. */
    override fun json(text: String): String = JsonBodyFormatter.prettyPrintJsonForInspection(text)
    /** Extracts a typed HTTP GraphQL document without adding queries to the shared hash catalogue. */
    override fun graphQL(method: String, url: String, contentType: String, body: ByteArray?, complete: Boolean) =
        GraphQLHttpRequestFormatter.inspect(method, url, contentType, body, complete, learnCaptured = false)
}
