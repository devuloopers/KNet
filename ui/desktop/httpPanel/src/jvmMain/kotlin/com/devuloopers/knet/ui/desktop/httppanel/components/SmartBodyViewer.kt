package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.core.components.placeholder.KNetBodyLoadingPlaceholder
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec

/**
 * Polymorphic, strongly-typed body viewer composable that resolves payload format
 * via [BodyFormatterRegistry] and renders the appropriate dedicated viewer.
 *
 * Supported formats:
 * - GraphQL: [GraphQLBodyViewer] with Query, Variables, Extensions, and Raw JSON sub-tabs.
 * - Form Data (URL-encoded / multipart): [FormDataViewer] structured key-value parameters.
 * - Structured Text (JSON, XML, HTML, JS, CSS, CBOR, Protobuf): [KNetCodeEditor] with accurate language syntax highlighting.
 * - Raw/Plain Text: [KNetCodeEditor] in plain text mode.
 *
 * @param spec Strongly-typed [PayloadInspectionSpec] holding headers, raw payload, and preparation state.
 * @param emptyTitle Title for empty state placeholder.
 * @param emptySubtitle Subtitle for empty state placeholder.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun SmartBodyViewer(
    spec: PayloadInspectionSpec,
    emptyTitle: String = "No Body Payload",
    emptySubtitle: String = "This transaction contained no request or response body payload.",
    modifier: Modifier = Modifier
) {
    if (spec.isPreparing) {
        KNetBodyLoadingPlaceholder(modifier = modifier.fillMaxSize())
        return
    }

    if (spec.isEmpty) {
        KNetEmptyStatePlaceholder(
            title = emptyTitle,
            subtitle = emptySubtitle,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val headersMap = remember(spec.headers) { spec.headers.toMap() }
    val format = spec.resolvedFormat ?: remember(spec.headers, spec.rawBody) {
        BodyFormatterRegistry.resolveFormat(headersMap, spec.rawBody)
    }

    when (format) {
        is BodyFormat.GraphQL -> {
            val formattedJson = remember(spec.rawBody) {
                JsonBodyFormatter().prettyPrintJson(spec.rawBody)
            }
            GraphQLBodyViewer(
                format = format,
                rawJsonText = formattedJson,
                modifier = modifier.fillMaxSize()
            )
        }

        is BodyFormat.FormData -> {
            FormDataViewer(
                pairs = format.pairs,
                modifier = modifier.fillMaxSize()
            )
        }

        else -> {
            val (codeLanguage, displayText) = when (format) {
                is BodyFormat.Json -> CodeLanguage.JSON to format.formattedText
                is BodyFormat.Xml -> CodeLanguage.XML to format.formattedText
                is BodyFormat.Html -> CodeLanguage.HTML to format.formattedText
                is BodyFormat.Js -> CodeLanguage.JAVASCRIPT to format.formattedText
                is BodyFormat.Css -> CodeLanguage.CSS to format.formattedText
                is BodyFormat.Cbor,
                is BodyFormat.Protobuf,
                is BodyFormat.GrpcWeb -> {
                    val prettyText = remember(spec.headers, spec.rawBody) {
                        BodyFormatterRegistry.prettyPrintBody(headersMap, spec.rawBody)
                    }
                    CodeLanguage.JSON to prettyText
                }
                else -> CodeLanguage.PLAIN to spec.rawBody
            }

            KNetCodeEditor(
                code = displayText,
                language = codeLanguage,
                mode = EditorMode.ReadOnly,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}
