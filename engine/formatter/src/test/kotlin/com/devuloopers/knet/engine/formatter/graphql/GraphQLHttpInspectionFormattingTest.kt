package com.devuloopers.knet.engine.formatter.graphql

import com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic
import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument
import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inspection pretty-printing is separate from transport facts and mutable authoring buffers. */
class GraphQLHttpInspectionFormattingTest {
    private val variables = """{"device":"ANDROID","language":"en","siteId":"fixture-id"}"""
    private val extensions = """{"client":{"name":"fixture","flags":["news","sport"]}}"""

    @Test fun `GET and POST variables and extensions use shared indentation without rewriting source`() {
        val query = "query BootStrapQuery { bootstrap { id } }"
        val url = "https://fixture.test/graphql?query=${encode(query)}&variables=${encode(variables)}&extensions=${encode(extensions)}"
        val body = """{"query":"$query","variables":$variables,"extensions":$extensions}"""
        val documents = listOf(
            GraphQLHttpRequestFormatter.inspect("GET", url)!!,
            GraphQLHttpRequestFormatter.inspect("POST", "/graphql", "application/json", body.encodeToByteArray())!!,
        )
        for (document in documents) {
            val view = GraphQLHttpRequestFormatter.format(document)
            assertEquals("""
                {
                  "device": "ANDROID",
                  "language": "en",
                  "siteId": "fixture-id"
                }
            """.trimIndent(), view.variablesJson)
            assertTrue(view.extensionsJson.contains("\n    \"flags\": [\n      \"news\",\n      \"sport\"\n    ]"))
            assertEquals(Json.parseToJsonElement(variables), Json.parseToJsonElement(view.variablesJson))
            assertEquals(Json.parseToJsonElement(extensions), Json.parseToJsonElement(view.extensionsJson))
            assertEquals(variables, document.variablesJson, "Source facts must stay compact and unmodified")
            assertEquals(extensions, document.extensionsJson)
            assertEquals(document.source, view.source)
            assertNull(view.diagnostic)
        }
        assertEquals(url, GraphQLHttpRequestFormatter.updateUrl(url, emptyMap()).getOrThrow())
    }

    @Test fun `invalid JSON is retained exactly rather than salvaged or reformatted`() {
        for (raw in listOf(" {\"id\": ", "prefix{\"id\":1}", "{\"id\":1} trailing", "{\"a\":1} {\"b\":2}", "[broken]")) {
            val document = GraphQLHttpDocument(GraphQLHttpSource.URL_PARAMETERS,
                variablesJson = raw, extensionsJson = raw, diagnostic = GraphQLHttpDiagnostic.MALFORMED_ENVELOPE)
            val view = GraphQLHttpRequestFormatter.format(document)
            assertEquals(raw, view.variablesJson)
            assertEquals(raw, view.extensionsJson)
            assertEquals(document.diagnostic, view.diagnostic)
        }
    }

    @Test fun `deep oversized and indentation expanding JSON fall back within display budgets`() {
        val deep = "{\"level\":".repeat(129) + "0" + "}".repeat(129)
        val oversized = "{}" + " ".repeat(1_048_576)
        val expanding = "{\"level\":".repeat(100) + (1..12_000).joinToString(",", "{", "}") { "\"field$it\":0" } + "}".repeat(100)
        for (raw in listOf(deep, oversized, expanding)) {
            assertEquals(raw, JsonBodyFormatter.prettyPrintJsonForInspection(raw))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
