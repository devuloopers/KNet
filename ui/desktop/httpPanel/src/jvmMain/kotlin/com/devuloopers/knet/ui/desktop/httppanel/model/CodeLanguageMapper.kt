package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Maps a domain-level [BodyFormat] descriptor to the corresponding [CodeLanguage]
 * syntax highlighting token for consumption by code editors and text viewers.
 *
 * Provides a strongly-typed bridge between engine format resolution and UI syntax presentation,
 * preventing presentation modules from coupling syntax token definitions to engine parser models.
 *
 * @receiver Optional [BodyFormat] descriptor detected from payload inspection.
 * @return Matching strongly-typed [CodeLanguage] token for editor syntax highlighting.
 */
fun BodyFormat?.toCodeLanguage(): CodeLanguage = when (this) {
    is BodyFormat.Json,
    is BodyFormat.JsonStream,
    is BodyFormat.Cbor,
    is BodyFormat.GrpcWeb,
    is BodyFormat.Protobuf -> CodeLanguage.JSON
    is BodyFormat.GraphQL -> CodeLanguage.GRAPHQL
    is BodyFormat.Html -> CodeLanguage.HTML
    is BodyFormat.Xml -> CodeLanguage.XML
    is BodyFormat.Js -> CodeLanguage.JAVASCRIPT
    is BodyFormat.Css -> CodeLanguage.CSS
    is BodyFormat.FormData,
    is BodyFormat.SseStream,
    is BodyFormat.Image,
    is BodyFormat.RawText,
    null -> CodeLanguage.PLAIN
}
