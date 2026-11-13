package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLHttpDecoder
import kotlinx.serialization.json.*
import java.net.URLEncoder
import kotlin.test.*

/** Encoding, selection and resource-bound regressions for the shared HTTP baseline. */
class GraphQLHttpDecoderTest {
    private val decoder = GraphQLHttpDecoder()
    private val bootstrap = "query BootStrapQuery(\$device: Device!) { bootstrap(device: \$device) { site { id } } }"
    private fun get(query: String, suffix: String = "") = decoder.decode("GET", "/api?query=${encode(query)}$suffix")!!
    private fun post(query: String, name: String? = null, extra: String = "") = decoder.decode("POST", "/api", "application/json; charset=UTF-8",
        ("{\"query\":${JsonPrimitive(query)}" + (name?.let { ",\"operationName\":${JsonPrimitive(it)}" } ?: "") + extra + "}").encodeToByteArray())!!
    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

    @Test fun `bootstrap GET and POST expose equivalent selected facts without an operationName parameter`() {
        val variables = """{"device":"ANDROID","language":"en","siteId":"fixture-id"}"""
        val get = get(bootstrap, "&variables=${encode(variables)}")
        val post = post(bootstrap, extra = ",\"variables\":$variables")
        assertEquals(GraphQLHttpSource.URL_PARAMETERS, get.source)
        assertEquals(GraphQLHttpSource.JSON_BODY, post.source)
        assertEquals(post.selectedOperation, get.selectedOperation)
        assertEquals("BootStrapQuery", get.selectedOperation?.name)
        assertEquals(GraphQLOperationType.QUERY, get.selectedOperation?.type)
        assertEquals(post.variablesJson, get.variablesJson)
        assertNull(get.suppliedOperationName)
        assertNull(get.diagnostic)
    }

    @Test fun `form decoder handles literal plus delimiters percent unicode and encoded keys exactly once`() {
        val query = "query Unicode { field(value: \"a+b & c=d %25 café 日本語\") }"
        val decoded = decoder.decode("GET", "/api?%71uery=${encode(query)}&extensions=${encode("""{"token":"a+b&c=d%25"}""")}")!!
        assertEquals(query, decoded.queryText)
        assertTrue(decoded.extensionsJson.contains("a+b&c=d%25"))
        assertNull(decoded.diagnostic)
        assertEquals("query X { x }", decoder.decode("GET", "/api?query=query+X+%7B+x+%7D")!!.queryText)
        assertEquals("%7B x %7D", decoder.decode("GET", "/graphql?query=%257B+x+%257D")!!.queryText)
    }

    @Test fun `invalid escapes and invalid UTF8 never produce an operation`() {
        for (value in listOf("%", "%2", "%GG", "%C3%28", "%FF", "%ED%A0%80")) {
            val decoded = decoder.decode("GET", "/api?query=$value")!!
            assertEquals(GraphQLHttpDiagnostic.MALFORMED_ENCODING, decoded.diagnostic, value)
            assertTrue(decoded.matchableOperations.isEmpty())
        }
        assertNull(decoder.decode("GET", "/ordinary?search=%GG"))
        assertNull(decoder.decode("GET", "/search?query=dogs"))
        assertNull(decoder.decode("POST", "/search", body = "{\"query\":\"SELECT * FROM users\"}".encodeToByteArray()))
    }

    @Test fun `duplicate recognized parameters including encoded aliases are ambiguous`() {
        for (field in GraphQLHttpParameter.entries) {
            val decoded = decoder.decode("GET", "/graphql?${field.wireName}=one&${encode(field.wireName.first().toString()).replace(field.wireName.first().toString(), "%" + field.wireName.first().code.toString(16))}${field.wireName.drop(1)}=two")!!
            assertEquals(GraphQLHttpDiagnostic.AMBIGUOUS_PARAMETERS, decoded.diagnostic, field.name)
        }
        assertNull(get("{ x }", "&tracking=1&tracking=2").diagnostic)
    }

    @Test fun `multiple operations honor selector with fragments comments and BOM`() {
        val source = "\uFEFF# comment\nfragment F on User { id }\nquery Read { user { ...F } } mutation Write { save { id } }"
        assertEquals(GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION, post(source).diagnostic)
        assertEquals(GraphQLOperationType.MUTATION, post(source, "Write").selectedOperation?.type)
        assertEquals(GraphQLHttpDiagnostic.UNKNOWN_OPERATION, post(source, "Unknown").diagnostic)
        assertEquals(source, post(source, "Unknown").queryText)
        assertEquals("Read", get(source, "&operationName=Read").selectedOperation?.name)
        assertEquals(GraphQLHttpDiagnostic.GET_OPERATION_NOT_QUERY, get(source, "&operationName=Write").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION, post("query X { a } query X { b }", "X").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION, post("{ a } query X { b }", "X").diagnostic)
    }

    @Test fun `anonymous shorthand and explicit null optional fields remain valid`() {
        assertNull(post("{ viewer { id } }").selectedOperation?.name)
        assertEquals(GraphQLOperationType.QUERY, post("{ viewer { id } }").selectedOperation?.type)
        assertNull(post("query X { x }", extra = ",\"operationName\":null,\"variables\":null,\"extensions\":null").diagnostic)
        assertNull(get("query X { x }", "&operationName=&variables=&extensions=").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.UNKNOWN_OPERATION, post("query X { x }", "").diagnostic)
        assertEquals("null", get("query null { x }", "&operationName=null").selectedOperation?.name)
    }

    @Test fun `wrong envelope types and bad optional JSON are explicit failures`() {
        for (suffix in listOf("&variables=[]", "&variables=123", "&variables=%7B", "&extensions=true", "&operationName=bad+name")) {
            assertEquals(GraphQLHttpDiagnostic.MALFORMED_ENVELOPE, get("{ x }", suffix).diagnostic, suffix)
        }
        val incomplete = get("query Read { x }", "&variables=%7Boops&extensions=%7Bbroken")
        assertEquals("query Read { x }", incomplete.queryText)
        assertEquals("{oops", incomplete.variablesJson)
        assertEquals("{broken", incomplete.extensionsJson)
        assertTrue(incomplete.matchableOperations.isEmpty())
        for (extra in listOf(",\"variables\":[]", ",\"extensions\":true", ",\"operationName\":123")) {
            assertEquals(GraphQLHttpDiagnostic.MALFORMED_ENVELOPE, post("{ x }", extra = extra).diagnostic)
        }
        assertEquals(GraphQLHttpDiagnostic.MALFORMED_ENVELOPE,
            decoder.decode("POST", "/graphql", body = "{\"query\":123}".encodeToByteArray())!!.diagnostic)
    }

    @Test fun `GET and POST never merge conflicting sources`() {
        val post = decoder.decode("POST", "/graphql?query=${encode("query Wrong { x }")}&operationName=Wrong",
            body = "{\"query\":\"query Right { x }\"}".encodeToByteArray())!!
        assertEquals("Right", post.selectedOperation?.name)
        val get = decoder.decode("GET", "/api?query=${encode("query Right { x }")}",
            body = "{\"query\":\"query Wrong { x }\"}".encodeToByteArray(), bodyComplete = false)!!
        assertEquals("Right", get.selectedOperation?.name)
    }

    @Test fun `GET mutation and subscription are viewable but never operation matched`() {
        for (kind in listOf("mutation", "subscription")) {
            val result = get("$kind Change { changed }")
            assertEquals(GraphQLHttpDiagnostic.GET_OPERATION_NOT_QUERY, result.diagnostic)
            assertEquals("Change", result.selectedOperation?.name)
            assertTrue(result.matchableOperations.isEmpty())
        }
    }

    @Test fun `missing hash only invalid and truncated documents never invent a query`() {
        assertEquals(GraphQLHttpDiagnostic.INVALID_PERSISTED_QUERY, decoder.decode("GET", "/graphql?extensions=${encode("""{"persistedQuery":{"sha256Hash":"fixture"}}""")}")!!.diagnostic)
        assertEquals(GraphQLHttpDiagnostic.MISSING_QUERY, decoder.decode("POST", "/graphql", body = "{\"operationName\":\"Missing\"}".encodeToByteArray())!!.diagnostic)
        assertEquals(GraphQLHttpDiagnostic.INVALID_DOCUMENT, post("query Incomplete {").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.TRUNCATED, decoder.decode("POST", "/graphql", body = "{\"query\":\"{ x }\"}".encodeToByteArray(), bodyComplete = false)!!.diagnostic)
        assertNull(decoder.decode("POST", "/users", body = "{\"name\":\"Ada\"}".encodeToByteArray()))
        assertNull(decoder.decode("GET", "/api?variables=%7B%22x%22%3A1%7D"))
        assertNull(decoder.decode("POST", "/api", body = "{\"variables\":{\"x\":1}}".encodeToByteArray()))
    }

    @Test fun `raw and JSON batch compatibility use the same valid selection semantics`() {
        assertEquals(GraphQLHttpSource.RAW_DOCUMENT, decoder.decode("POST", "/graphql", "application/graphql", "query Raw { x }".encodeToByteArray())!!.source)
        val batch = decoder.decode("POST", "/graphql", body = """[{"query":"query Read { x }"},{"query":"mutation Write { x }"}]""".encodeToByteArray())!!
        assertEquals(listOf("Read", "Write"), batch.matchableOperations.map { it.name })
        val invalid = decoder.decode("POST", "/graphql", body = """[{"query":"query Read { x }"},{"query":"query A { a } query B { b }"}]""".encodeToByteArray())!!
        assertTrue(invalid.matchableOperations.isEmpty())
    }

    @Test fun `input depth token operation and parameter budgets fail closed`() {
        val huge = "x".repeat(GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS + 1)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post(huge).diagnostic)
        val deep = "{ x ".repeat(150) + "}".repeat(150)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post(deep).diagnostic)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post("{ " + "x ".repeat(33_000) + "}").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post("query " + "X".repeat(257) + " { x }").diagnostic)
        val many = (1..257).joinToString(" ") { "query X$it { x }" }
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post(many, "X1").diagnostic)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, decoder.decode("GET", "/graphql?" + (1..4100).joinToString("&") { "x$it=y" })!!.diagnostic)
        assertEquals(GraphQLHttpDiagnostic.LIMIT_EXCEEDED, post("{ x }", extra = ",\"variables\":" + "[".repeat(150) + "0" + "]".repeat(150)).diagnostic)
    }

    @Test fun `URL editing retains untouched pairs fragment and original encoding without double encoding`() {
        val original = "/api?flag&query=query%20X%20%7B%20x%20%7D&tracking=a%2bb&tracking=2#fragment"
        val changed = GraphQLHttpDecoder.updateUrlParameters(original, mapOf(GraphQLHttpParameter.VARIABLES to """{"text":"a+b & café"}""")).getOrThrow()
        assertTrue(changed.startsWith(original.substringBefore('#')))
        assertTrue(changed.endsWith("#fragment"))
        assertEquals("query X { x }", decoder.decode("GET", changed)!!.queryText)
        assertEquals(original, GraphQLHttpDecoder.updateUrlParameters(original, emptyMap()).getOrThrow())
        assertEquals("/api#fragment", GraphQLHttpDecoder.updateUrlParameters("/api?query=x#fragment", mapOf(GraphQLHttpParameter.QUERY to null)).getOrThrow())
        assertTrue(GraphQLHttpDecoder.updateUrlParameters("/api?query=x&%71uery=y", mapOf(GraphQLHttpParameter.QUERY to "{ x }")).isFailure)
    }
}
