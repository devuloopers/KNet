package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.contract.graphql.*
import com.devuloopers.knet.domain.graphql.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest

/** Exact UTF-8 source hashing; whitespace and comments are significant and are never normalized. */
object GraphQLQueryHashing {
    /** Produces the Apollo SHA-256 identifier of [query] exactly as supplied. */
    fun sha256(query: String): GraphQLQueryHash = GraphQLQueryHash(
        buildString(64) {
            val hex = "0123456789abcdef"
            for (byte in MessageDigest.getInstance("SHA-256").digest(query.encodeToByteArray())) {
                val value = byte.toInt() and 255
                append(hex[value ushr 4]); append(hex[value and 15])
            }
        },
    )
}

/** Bounded LRU/absolute-TTL source catalog, synchronized across live capture and background UI preparation. */
class BoundedGraphQLPersistedQueryCatalog(
    private val maximumEntries: Int = 256,
    private val maximumRetainedBytes: Long = 4L * 1024 * 1024,
    private val lifetimeMillis: Long = 30L * 60 * 1000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : GraphQLPersistedQueryCatalog {
    private data class Key(val endpoint: GraphQLEndpoint, val hash: GraphQLQueryHash)
    private data class Entry(val mapping: GraphQLQueryMapping, val expiresAt: Long, val weight: Long)
    private val lock = Any()
    private val entries = LinkedHashMap<Key, Entry>(16, .75f, true)
    private var retainedBytes = 0L
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    init { require(maximumEntries > 0 && maximumRetainedBytes > 0 && lifetimeMillis > 0) }

    override fun endpoint(url: String): GraphQLEndpoint? = runCatching {
        if (url.length > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS) return null
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https") || uri.rawUserInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) { if (scheme == "https") 443 else 80 } else uri.port
        if (port !in 1..65535) return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        GraphQLEndpoint("$scheme://$host:$port$path")
    }.getOrNull()

    override fun lookup(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash): GraphQLQueryMapping? = synchronized(lock) {
        prune(nowMillis())
        entries[Key(endpoint, hash)]?.mapping
    }

    override fun rememberCaptured(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash, query: String): Boolean {
        if (query.isBlank() || query.length > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS || GraphQLQueryHashing.sha256(query) != hash) return false
        val weight = weight(endpoint, query)
        if (weight > maximumRetainedBytes) return false
        synchronized(lock) {
            val now = nowMillis()
            prune(now)
            val key = Key(endpoint, hash)
            val mapping = GraphQLQueryMapping(query, GraphQLQueryProvenance.CAPTURED_TRAFFIC)
            val previous = entries[key]
            put(key, Entry(mapping, now + lifetimeMillis, weight))
            if (previous?.mapping != mapping) changed()
        }
        return true
    }

    override fun importManifest(endpoint: GraphQLEndpoint, bytes: ByteArray): GraphQLManifestImportResult {
        fun reject(reason: GraphQLManifestFailure) = GraphQLManifestImportResult.Rejected(reason)
        if (bytes.size > 2_097_152) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
        val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
            ?: return reject(GraphQLManifestFailure.INVALID_FORMAT)
        if (!GraphQLHttpDecoder.withinJsonDepth(text)) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return reject(GraphQLManifestFailure.INVALID_FORMAT)
        val operations: List<JsonObject> = if ("operations" in root) {
            if ((root["format"] as? JsonPrimitive)?.content != "apollo-persisted-query-manifest" ||
                (root["version"] as? JsonPrimitive)?.let { !it.isString && it.intOrNull == 1 } != true)
                return reject(GraphQLManifestFailure.INVALID_FORMAT)
            val values = root["operations"] as? JsonArray ?: return reject(GraphQLManifestFailure.INVALID_FORMAT)
            if (values.size > maximumEntries) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
            if (values.any { it !is JsonObject }) return reject(GraphQLManifestFailure.INVALID_FORMAT)
            values.filterIsInstance<JsonObject>()
        } else {
            if (root.size > maximumEntries) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
            root.map { (hash, query) -> buildJsonObject { put("id", hash); put("body", query) } }
        }
        if (operations.isEmpty() || operations.size > maximumEntries) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
        val prepared = LinkedHashMap<Key, Entry>()
        val decoder = GraphQLHttpDecoder()
        var totalWeight = 0L
        for (operation in operations) {
            fun string(name: String) = (operation[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val id = string("id") ?: return reject(GraphQLManifestFailure.INVALID_FORMAT)
            val query = string("body") ?: return reject(GraphQLManifestFailure.INVALID_FORMAT)
            if (!Regex("[0-9a-fA-F]{64}").matches(id)) return reject(GraphQLManifestFailure.INVALID_FORMAT)
            if (query.length > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
            val hash = GraphQLQueryHash(id.lowercase())
            if (GraphQLQueryHashing.sha256(query) != hash) return reject(GraphQLManifestFailure.HASH_MISMATCH)
            if (listOf("name", "type").any { it in operation && string(it) == null }) return reject(GraphQLManifestFailure.INVALID_FORMAT)
            val decoded = decoder.decodeResolvedDocument(GraphQLHttpDocument(GraphQLHttpSource.RAW_DOCUMENT,
                suppliedOperationName = string("name")), query, false)
            val selected = decoded.selectedOperation
            if (decoded.diagnostic != null || selected == null) return reject(GraphQLManifestFailure.INVALID_QUERY)
            if (string("type")?.let { it != selected.type.name.lowercase() } == true)
                return reject(GraphQLManifestFailure.INVALID_FORMAT)
            val key = Key(endpoint, hash)
            if (key !in prepared) {
                val weight = weight(endpoint, query)
                totalWeight += weight
                if (totalWeight > maximumRetainedBytes) return reject(GraphQLManifestFailure.LIMIT_EXCEEDED)
                prepared[key] = Entry(GraphQLQueryMapping(query, GraphQLQueryProvenance.IMPORTED_MANIFEST), 0L, weight)
            }
        }
        synchronized(lock) {
            val now = nowMillis()
            prune(now)
            prepared.forEach { (key, entry) -> put(key, entry.copy(expiresAt = now + lifetimeMillis)) }
            changed()
        }
        return GraphQLManifestImportResult.Imported(prepared.size)
    }

    override fun clear() = synchronized(lock) {
        if (entries.isNotEmpty()) { entries.clear(); retainedBytes = 0L; changed() }
    }

    private fun weight(endpoint: GraphQLEndpoint, query: String): Long = (query.length.toLong() + endpoint.value.length + 64) * 2 + 128
    private fun changed() { mutableRevision.value += 1 }
    private fun put(key: Key, entry: Entry) {
        retainedBytes -= entries.remove(key)?.weight ?: 0L
        entries[key] = entry
        retainedBytes += entry.weight
        while (entries.size > maximumEntries || retainedBytes > maximumRetainedBytes) {
            val oldest = entries.entries.iterator().next()
            retainedBytes -= oldest.value.weight
            entries.remove(oldest.key)
        }
    }
    private fun prune(now: Long) {
        var removed = false
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (now >= entry.expiresAt) { retainedBytes -= entry.weight; iterator.remove(); removed = true }
        }
        if (removed) changed()
    }
}

/** One process-local catalog shared by capture, live matching and read-only formatters, cleared at shutdown. */
object GraphQLPersistedQueryRuntime {
    /** Bounded source-only catalog; independent catalogs can be supplied to tests and isolated consumers. */
    val catalog: GraphQLPersistedQueryCatalog = BoundedGraphQLPersistedQueryCatalog()
}

/** Resolves verified source as derived data while keeping the captured query field and envelope untouched. */
class GraphQLPersistedQueryResolver(private val catalog: GraphQLPersistedQueryCatalog = GraphQLPersistedQueryRuntime.catalog) {
    private val decoder = GraphQLHttpDecoder()
    /** Parses GET/POST (including batches), optionally learning only a complete captured source. */
    fun decode(method: String, url: String, contentType: String = "", body: ByteArray? = null,
        bodyComplete: Boolean = true, learnCaptured: Boolean = false): GraphQLHttpDocument? {
        val endpoint = catalog.endpoint(url)
        return decoder.decode(method, url, contentType, body, bodyComplete) { document ->
            val metadata = document.persistedQuery
            if (metadata == null) document
            else if (document.queryText.isNotBlank()) {
                if (GraphQLQueryHashing.sha256(document.queryText) != metadata.hash) document.copy(
                    diagnostic = GraphQLHttpDiagnostic.PERSISTED_QUERY_HASH_MISMATCH, matchableOperations = emptyList())
                else {
                    if (learnCaptured && bodyComplete && endpoint != null && document.diagnostic == null)
                        catalog.rememberCaptured(endpoint, metadata.hash, document.queryText)
                    document
                }
            } else if (document.diagnostic != null || endpoint == null) document
            else {
                val mapping = catalog.lookup(endpoint, metadata.hash)
                if (mapping == null) document else decoder.decodeResolvedDocument(document, mapping.query,
                    method.equals("GET", true)).copy(queryText = document.queryText, resolvedQueryText = mapping.query,
                    queryProvenance = mapping.provenance)
            }
        }
    }
}
