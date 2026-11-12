package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat

/** Body-only compatibility adapter to the shared bounded HTTP envelope/AST decoder. */
class GraphQLBodyFormatter : BodyFormatter {
    override val priority: Int = 85

    /** Formats standalone source without keeping a second parser implementation. */
    fun formatQuery(queryText: String): String = Companion.formatQuery(queryText)

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean =
        !contentType(headers).substringBefore(';').trim().equals("application/graphql-response+json", true) &&
        GraphQLHttpRequestFormatter.inspect("POST", "", contentType(headers), bodyText.encodeToByteArray())
            ?.let { it.source != GraphQLHttpSource.JSON_BATCH } == true

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat =
        GraphQLHttpRequestFormatter.inspect("POST", "", contentType(headers), bodyText.encodeToByteArray())
            ?.let(GraphQLHttpRequestFormatter::format) ?: BodyFormat.RawText(bodyText)

    private fun contentType(headers: Map<String, String>): String = headers.entries.firstOrNull {
        it.key.equals("Content-Type", true)
    }?.value.orEmpty()

    companion object {
        /** Bounded AST formatting; invalid source remains readable and unchanged. */
        fun formatQuery(queryText: String): String = GraphQLHttpRequestFormatter.formatQuery(queryText)
    }
}
