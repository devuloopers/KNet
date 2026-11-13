package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.contract.graphql.*
import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.engine.protocol.inspector.graphql.*
import kotlinx.serialization.json.*
import java.net.URLEncoder
import kotlin.test.*

/** Exact APQ semantics, source isolation, atomic import and strict memory/lifetime bounds. */
class GraphQLPersistedQueriesTest {
    private val catalog = BoundedGraphQLPersistedQueryCatalog()
    private val resolver = GraphQLPersistedQueryResolver(catalog)
    private val url = "https://fixture.test/graphql"
    private val query = "# exact source\nquery GetUser { user { id } }\n"
    private val hash = GraphQLQueryHashing.sha256(query)
    private val endpoint = catalog.endpoint(url)!!
    private fun metadata(id: String = hash.value, version: JsonElement = JsonPrimitive(1)): JsonObject = buildJsonObject {
        put("version", version); put("sha256Hash", id)
    }
    private fun envelope(source: String? = null, id: String = hash.value, selector: String? = null): String = buildJsonObject {
        source?.let { put("query", it) }
        selector?.let { put("operationName", it) }
        put("variables", buildJsonObject { put("secret", "not retained") })
        put("extensions", buildJsonObject { put("persistedQuery", metadata(id)) })
    }.toString()
    private fun post(body: String = envelope(), learn: Boolean = false, complete: Boolean = true, target: String = url) =
        resolver.decode("POST", target, "application/json", body.encodeToByteArray(), complete, learn)!!
    private fun simpleManifest(vararg sources: String): ByteArray = buildJsonObject {
        sources.forEach { put(GraphQLQueryHashing.sha256(it).value, it) }
    }.toString().encodeToByteArray()
    private fun import(bytes: ByteArray) = catalog.importManifest(endpoint, bytes)

    @Test fun `hashing uses exact UTF8 including whitespace comments and Unicode`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", GraphQLQueryHashing.sha256("").value)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", GraphQLQueryHashing.sha256("hello").value)
        assertNotEquals(hash, GraphQLQueryHashing.sha256(query.trim()))
        assertNotEquals(GraphQLQueryHashing.sha256("café"), GraphQLQueryHashing.sha256("cafe"))
    }

    @Test fun `unknown valid hash is GraphQL with honest unavailable source and no guessed operation`() {
        val result = post(envelope(selector = "GetUser"))
        assertNull(result.diagnostic)
        assertEquals(hash, result.persistedQuery?.hash)
        assertTrue(result.isPersistedQueryUnresolved)
        assertEquals(GraphQLQueryProvenance.UNAVAILABLE, result.queryProvenance)
        assertEquals("", result.queryText)
        assertEquals("", result.formattedQuery)
        assertNull(result.selectedOperation)
        assertTrue(result.matchableOperations.isEmpty())
        assertEquals(0L, catalog.revision.value)
    }

    @Test fun `APQ miss fallback capture and later hash-only request learn without changing request source`() {
        assertTrue(post().isPersistedQueryUnresolved)
        val fallback = envelope(query)
        val full = post(fallback, learn = true)
        assertNull(full.diagnostic)
        assertEquals(query, full.queryText)
        assertEquals(GraphQLQueryProvenance.REQUEST, full.queryProvenance)
        val raw = envelope()
        val result = post(raw)
        assertEquals("GetUser", result.selectedOperation?.name)
        assertEquals(query, result.resolvedQueryText)
        assertEquals("", result.queryText)
        assertEquals(GraphQLQueryProvenance.CAPTURED_TRAFFIC, result.queryProvenance)
        assertTrue(result.formattedQuery.contains("query GetUser"))
        assertFalse(result.isPersistedQueryUnresolved)
        assertEquals(raw, envelope())
        assertEquals(1L, catalog.revision.value)
        assertEquals(query, catalog.lookup(endpoint, hash)?.query)
    }

    @Test fun `authoring inspection incomplete capture and invalid syntax cannot teach mappings`() {
        post(envelope(query))
        assertTrue(post().isPersistedQueryUnresolved)
        assertEquals(GraphQLHttpDiagnostic.TRUNCATED, post(envelope(query), learn = true, complete = false).diagnostic)
        assertTrue(post().isPersistedQueryUnresolved)
        val invalid = "query Broken {"
        post(envelope(invalid, GraphQLQueryHashing.sha256(invalid).value), learn = true)
        assertNull(catalog.lookup(endpoint, GraphQLQueryHashing.sha256(invalid)))
        assertEquals(0L, catalog.revision.value)
    }

    @Test fun `mismatched supplied source remains viewable but never learns or operation matches`() {
        val result = post(envelope(query + " "), learn = true)
        assertEquals(GraphQLHttpDiagnostic.PERSISTED_QUERY_HASH_MISMATCH, result.diagnostic)
        assertEquals(query + " ", result.queryText)
        assertTrue(result.matchableOperations.isEmpty())
        assertTrue(post().isPersistedQueryUnresolved)
    }

    @Test fun `metadata rejects invalid versions hashes and field types without inventing a query`() {
        val invalid = listOf(
            "null", "true", "[]", "{}", """{"version":2,"sha256Hash":"${hash.value}"}""",
            """{"version":"1","sha256Hash":"${hash.value}"}""",
            """{"version":1,"sha256Hash":123}""", """{"version":1,"sha256Hash":"short"}""",
            """{"sha256Hash":"${hash.value}"}""",
        )
        for (value in invalid) {
            val result = post("""{"extensions":{"persistedQuery":$value}}""")
            assertEquals(GraphQLHttpDiagnostic.INVALID_PERSISTED_QUERY, result.diagnostic, value)
            assertNull(result.persistedQuery)
            assertTrue(result.matchableOperations.isEmpty())
        }
        assertEquals(hash, post(envelope(id = hash.value.uppercase())).persistedQuery?.hash)
    }

    @Test fun `canonical endpoint excludes parameters but separates scheme host port and exact path`() {
        assertEquals(endpoint, catalog.endpoint("https://FIXTURE.test:443/graphql?token=private#fragment"))
        for (invalid in listOf("/graphql", "file:///graphql", "https://user:secret@fixture.test/graphql", "https://fixture.test:0/graphql", "https://fixture.test:65536/graphql", "not a url"))
            assertNull(catalog.endpoint(invalid), invalid)
        post(envelope(query), learn = true)
        for (target in listOf("http://fixture.test/graphql", "https://other.test/graphql", "https://fixture.test:8443/graphql", "https://fixture.test/other", "/graphql"))
            assertTrue(post(target = target).isPersistedQueryUnresolved, target)
        assertFalse(post(target = "$url?session=other").isPersistedQueryUnresolved)
    }

    @Test fun `GET hash-only resolution applies selector and method safety without synthetic query parameters`() {
        val mutation = "query Read { x } mutation Write { save }"
        val id = GraphQLQueryHashing.sha256(mutation).value
        post(envelope(mutation, id, "Read"), learn = true)
        val suffix = URLEncoder.encode(buildJsonObject { put("persistedQuery", metadata(id)) }.toString(), Charsets.UTF_8)
        fun get(name: String?) = resolver.decode("GET", "$url?extensions=$suffix" + (name?.let { "&operationName=$it" } ?: ""))!!
        assertEquals("Read", get("Read").selectedOperation?.name)
        assertEquals(GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION, get(null).diagnostic)
        assertEquals(GraphQLHttpDiagnostic.UNKNOWN_OPERATION, get("Other").diagnostic)
        val write = get("Write")
        assertEquals(GraphQLHttpDiagnostic.GET_OPERATION_NOT_QUERY, write.diagnostic)
        assertTrue(write.matchableOperations.isEmpty())
        assertEquals("", write.queryText)
        assertTrue(write.formattedQuery.contains("mutation Write"))
    }

    @Test fun `unresolved batch entry prevents guessing but verified batch entries resolve individually`() {
        val second = "query Second { y }"
        val unknown = envelope(id = GraphQLQueryHashing.sha256(second).value)
        post(envelope(query), learn = true)
        val partiallyKnown = post("[${envelope()},$unknown]")
        assertEquals(2, partiallyKnown.batchEntries.size)
        assertTrue(partiallyKnown.matchableOperations.isEmpty())
        post(envelope(second, GraphQLQueryHashing.sha256(second).value), learn = true)
        val known = post("[${envelope()},$unknown]")
        assertEquals(listOf("GetUser", "Second"), known.matchableOperations.map { it.name })
        assertTrue(known.batchEntries.all { it.queryText.isEmpty() && it.resolvedQueryText != null })
    }

    @Test fun `Apollo version-one and simple manifests import exact endpoint scoped sources with provenance`() {
        val apollo = buildJsonObject {
            put("format", "apollo-persisted-query-manifest"); put("version", 1)
            put("operations", buildJsonArray { add(buildJsonObject {
                put("id", hash.value); put("body", query); put("name", "GetUser"); put("type", "query")
            }) })
        }.toString().encodeToByteArray()
        assertEquals(GraphQLManifestImportResult.Imported(1), import(apollo))
        assertEquals(GraphQLQueryProvenance.IMPORTED_MANIFEST, post().queryProvenance)
        assertEquals(GraphQLManifestImportResult.Imported(1), import(simpleManifest(query)))
        assertTrue(post(target = "https://other.test/graphql").isPersistedQueryUnresolved)
    }

    @Test fun `manifest failure is atomic for hash mismatch invalid source type selector and malformed UTF8`() {
        val second = "query Second { y }"
        val invalid = listOf(
            "[]".encodeToByteArray(), byteArrayOf(0xff.toByte()),
            """{"$hash":"different"}""".encodeToByteArray(),
            buildJsonObject { put(hash.value, query); put(GraphQLQueryHashing.sha256(second).value, "wrong") }.toString().encodeToByteArray(),
            simpleManifest("query Broken {"),
            """{"format":"apollo-persisted-query-manifest","version":2,"operations":[]}""".encodeToByteArray(),
            """{"format":"apollo-persisted-query-manifest","version":1,"operations":[{"id":"${hash.value}","body":${JsonPrimitive(query)},"type":"mutation"}]}""".encodeToByteArray(),
            """{"format":"apollo-persisted-query-manifest","version":1,"operations":[{"id":"${hash.value}","body":${JsonPrimitive(query)},"name":"Missing"}]}""".encodeToByteArray(),
        )
        for (bytes in invalid) {
            assertIs<GraphQLManifestImportResult.Rejected>(import(bytes))
            assertNull(catalog.lookup(endpoint, hash))
        }
        assertEquals(0L, catalog.revision.value)
    }

    @Test fun `manifest allocation depth count and weight limits reject before insertion`() {
        assertEquals(GraphQLManifestFailure.LIMIT_EXCEEDED,
            assertIs<GraphQLManifestImportResult.Rejected>(import(ByteArray(2_097_153))).reason)
        val deep = ("{" + "\"x\":{".repeat(130) + "\"x\":0" + "}".repeat(131)).encodeToByteArray()
        assertEquals(GraphQLManifestFailure.LIMIT_EXCEEDED, assertIs<GraphQLManifestImportResult.Rejected>(import(deep)).reason)
        val tiny = BoundedGraphQLPersistedQueryCatalog(maximumEntries = 1, maximumRetainedBytes = 1024)
        assertIs<GraphQLManifestImportResult.Rejected>(tiny.importManifest(endpoint, simpleManifest(query, "query Second { y }")))
        assertIs<GraphQLManifestImportResult.Rejected>(tiny.importManifest(endpoint, simpleManifest("#" + "x".repeat(1000) + "\n{ x }")))
        assertNull(catalog.lookup(endpoint, hash))
    }

    @Test fun `LRU count and weighted bounds eviction are deterministic and observed`() {
        val cache = BoundedGraphQLPersistedQueryCatalog(maximumEntries = 2)
        val queries = listOf(query, "query Second { y }", "query Third { z }")
        fun put(index: Int) = cache.rememberCaptured(endpoint, GraphQLQueryHashing.sha256(queries[index]), queries[index])
        put(0); put(1); cache.lookup(endpoint, hash); put(2)
        assertNotNull(cache.lookup(endpoint, hash))
        assertNull(cache.lookup(endpoint, GraphQLQueryHashing.sha256(queries[1])))
        assertNotNull(cache.lookup(endpoint, GraphQLQueryHashing.sha256(queries[2])))
        val weighted = BoundedGraphQLPersistedQueryCatalog(maximumRetainedBytes = 500)
        assertTrue(weighted.rememberCaptured(endpoint, hash, query))
        assertFalse(weighted.rememberCaptured(endpoint, GraphQLQueryHashing.sha256(query.repeat(10)), query.repeat(10)))
        assertNotNull(weighted.lookup(endpoint, hash))
    }

    @Test fun `absolute expiry is not extended by reading and repeated learning does not churn revision`() {
        var now = 0L
        val cache = BoundedGraphQLPersistedQueryCatalog(lifetimeMillis = 100, nowMillis = { now })
        cache.rememberCaptured(endpoint, hash, query)
        now = 50
        assertNotNull(cache.lookup(endpoint, hash))
        assertEquals(1L, cache.revision.value)
        now = 100
        assertNull(cache.lookup(endpoint, hash))
        assertEquals(2L, cache.revision.value)
        cache.rememberCaptured(endpoint, hash, query); cache.rememberCaptured(endpoint, hash, query)
        assertEquals(3L, cache.revision.value)
        cache.clear()
        assertEquals(4L, cache.revision.value)
        assertNull(cache.lookup(endpoint, hash))
        cache.clear(); assertEquals(4L, cache.revision.value)
    }
    @Test fun `rejected import never destroys existing verified mappings`() {
        assertEquals(GraphQLManifestImportResult.Imported(1), import(simpleManifest(query)))
        val revision = catalog.revision.value
        val invalid = buildJsonObject { put(hash.value, query + " ") }.toString().encodeToByteArray()
        assertIs<GraphQLManifestImportResult.Rejected>(import(invalid))
        assertEquals(query, catalog.lookup(endpoint, hash)?.query)
        assertEquals(revision, catalog.revision.value)
        post(envelope(query + " "), learn = true)
        assertEquals(query, catalog.lookup(endpoint, hash)?.query)
    }

    @Test fun `simple manifest operation count is rejected before preparing a second tree`() {
        val many = buildJsonObject { repeat(257) { put(it.toString(16).padStart(64, '0'), query) } }.toString().encodeToByteArray()
        assertEquals(GraphQLManifestFailure.LIMIT_EXCEEDED, assertIs<GraphQLManifestImportResult.Rejected>(import(many)).reason)
        assertEquals(0L, catalog.revision.value)
    }

    @Test fun `concurrent learn lookup and clear keep catalog usable and bounded`() {
        val cache = BoundedGraphQLPersistedQueryCatalog(maximumEntries = 4, maximumRetainedBytes = 4096)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val work = (1..40).map { index -> executor.submit {
                val source = "query Work$index { x }"
                val id = GraphQLQueryHashing.sha256(source)
                cache.rememberCaptured(endpoint, id, source)
                cache.lookup(endpoint, id)?.let { assertEquals(id, GraphQLQueryHashing.sha256(it.query)) }
                if (index % 7 == 0) cache.clear()
            } }
            work.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
            assertTrue(cache.rememberCaptured(endpoint, hash, query))
            assertEquals(query, cache.lookup(endpoint, hash)?.query)
        } finally { executor.shutdownNow() }
    }

}
