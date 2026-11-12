package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter

/** Derived GET editor state. The URL remains canonical and this projection is never persisted as a body. */
data class GraphQLParameterEditorState(
    /** Unescaped authoring fields with retained tab selection. */
    val graphQl: GraphQlState,
    /** Invalid or incompatible source explanation. */
    val diagnostic: GraphQLHttpDiagnostic? = null,
) {
    companion object {
        /**
         * Prepares GET URL fields off-thread. Initial hydration prettifies Query/Variables/Extensions
         * using the shared bounded formatters, without modifying [url] or inventing a request body.
         *
         * @param method Canonical HTTP method; non-GET sources are not parameter editors.
         * @param url Original encoded request target, retained by its owner.
         * @param prettify False for validation-only refreshes, which must not reformat active authoring buffers.
         * @return Initial editor projection and diagnostic, or null for an unrecognized/non-GET source.
         */
        fun prepare(method: String, url: String, prettify: Boolean = true): GraphQLParameterEditorState? {
            if (!method.equals("GET", true)) return null
            val document = GraphQLHttpRequestFormatter.inspect(method, url) ?: return null
            val formatted = if (prettify) GraphQLHttpRequestFormatter.format(document) else null
            return GraphQLParameterEditorState(GraphQlState(payload = StructuredPayloadState.GraphQL(
                queryText = formatted?.queryText ?: document.queryText,
                variablesText = formatted?.variablesJson ?: document.variablesJson,
                extensionsText = formatted?.extensionsJson ?: document.extensionsJson,
                operationName = document.suppliedOperationName.orEmpty(),
            )), document.diagnostic)
        }
    }
}
