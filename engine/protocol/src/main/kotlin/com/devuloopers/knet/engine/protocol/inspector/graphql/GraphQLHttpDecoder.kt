package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic
import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument
import com.devuloopers.knet.domain.graphql.GraphQLHttpOperation
import com.devuloopers.knet.domain.graphql.GraphQLHttpParameter
import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.domain.graphql.GraphQLPersistedQuery
import com.devuloopers.knet.domain.graphql.GraphQLQueryHash
import graphql.language.AstPrinter
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.parser.ParserEnvironment
import graphql.parser.ParserOptions
import graphql.parser.exceptions.ParseCancelledException
import graphql.parser.exceptions.ParseCancelledTooDeepException
import graphql.parser.exceptions.ParseCancelledTooManyCharsException
import graphql.parser.exceptions.ParseCancelledTooManyNumericLiteralCharactersException
import kotlinx.serialization.json.*

/**
 * Shared bounded extractor for GraphQL POST JSON and GET form parameters. It never changes a request.
 * Limits apply before JSON/AST allocation; parser options are local and do not alter JVM-wide defaults.
 */
class GraphQLHttpDecoder {
    /**
     * Decodes the source selected by [method], returning null for ordinary HTTP data. [bodyComplete]
     * prevents retained prefixes from producing operation-specific matches. GET never consumes [body].
     */
    fun decode(
        method: String,
        url: String,
        contentType: String = "",
        body: ByteArray? = null,
        bodyComplete: Boolean = true,
        resolvePersisted: ((GraphQLHttpDocument) -> GraphQLHttpDocument)? = null,
    ): GraphQLHttpDocument? {
        val get = method.equals("GET", true)
        val source = if (get) GraphQLHttpSource.URL_PARAMETERS else GraphQLHttpSource.JSON_BODY
        val hint = url.substringBefore('?').contains("graphql", true) || contentType.substringBefore(';').trim().equals("application/graphql", true)
        if (url.length > MAXIMUM_INPUT_CHARACTERS) return failure(source, GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        if (get) {
            val parameters = try { decodeParameters(url) } catch (_: LimitException) {
                return failure(source, GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
            } catch (_: Exception) {
                return if (hint || hasRecognizedParameter(url)) failure(source, GraphQLHttpDiagnostic.MALFORMED_ENCODING) else null
            }
            val recognized = parameters.filter { it.first in FIELDS }
            if (recognized.isEmpty()) return if (hint) failure(source, GraphQLHttpDiagnostic.MISSING_QUERY) else null
            if (recognized.groupingBy { it.first }.eachCount().any { it.value > 1 }) {
                return failure(source, GraphQLHttpDiagnostic.AMBIGUOUS_PARAMETERS)
            }
            val fields = recognized.toMap()
            if (!hint && "query" !in fields && fields["extensions"]?.let {
                    (parseJson(it) as? JsonObject)?.containsKey("persistedQuery") } != true) return null
            val decoded = decodeEnvelope(buildJsonObject {
                fields.forEach { (name, value) ->
                    if (name == "variables" || name == "extensions") {
                        if (value.isNotEmpty()) put(name, parseJson(value) ?: JsonPrimitive(value))
                    } else if (value.isNotEmpty()) put(name, JsonPrimitive(value))
                }
            }, source, get = true, rawParameters = fields, resolvePersisted = resolvePersisted)
            return decoded.takeIf { hint || looksLikeDocument(it.queryText.trim()) || it.queryText.isBlank() }
        }
        if (!method.equals("POST", true)) return if (hint) failure(source, GraphQLHttpDiagnostic.MISSING_QUERY) else null
        if (body != null && body.size > MAXIMUM_INPUT_CHARACTERS) return failure(source, GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        if (!bodyComplete) return if (hint || body?.decodeToString()?.let { "\"query\"" in it || "\"persistedQuery\"" in it } == true) failure(source, GraphQLHttpDiagnostic.TRUNCATED) else null
        if (body == null || body.isEmpty()) return if (hint) failure(source, GraphQLHttpDiagnostic.MISSING_QUERY) else null
        val text = try { strictUtf8(body).trim() } catch (_: Exception) {
            return if (hint) failure(source, GraphQLHttpDiagnostic.MALFORMED_ENCODING) else null
        }
        if (!withinJsonDepth(text)) return failure(source, GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        val root = parseJson(text)
        return when {
            root is JsonObject && (hint || envelopeHint(root)) -> decodeEnvelope(root, source, resolvePersisted = resolvePersisted).takeIf {
                hint || looksLikeDocument(it.queryText.trim()) || it.queryText.isBlank()
            }
            root is JsonArray -> {
                if (root.size > MAXIMUM_OPERATIONS) return failure(GraphQLHttpSource.JSON_BATCH, GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
                val envelopes = root.filterIsInstance<JsonObject>()
                if (envelopes.isEmpty() || envelopes.none(::envelopeHint)) return null
                val decoded = envelopes.map { decodeEnvelope(it, GraphQLHttpSource.JSON_BATCH, resolvePersisted = resolvePersisted) }
                val diagnostic = decoded.firstNotNullOfOrNull { it.diagnostic }
                    ?: GraphQLHttpDiagnostic.MALFORMED_ENVELOPE.takeIf { envelopes.size != root.size }
                GraphQLHttpDocument(source = GraphQLHttpSource.JSON_BATCH,
                    matchableOperations = if (diagnostic == null && decoded.none { it.isPersistedQueryUnresolved }) decoded.flatMap { it.matchableOperations } else emptyList(),
                    diagnostic = diagnostic, batchEntries = decoded)
            }
            root == null && (contentType.substringBefore(';').trim().equals("application/graphql", true) || looksLikeDocument(text)) ->
                decodeDocument(GraphQLHttpDocument(GraphQLHttpSource.RAW_DOCUMENT, queryText = text))
            hint -> failure(source, if (root == null) GraphQLHttpDiagnostic.MALFORMED_ENVELOPE else GraphQLHttpDiagnostic.MISSING_QUERY)
            else -> null
        }
    }

    /** Formats a standalone bounded document through the same parser used by operation matching. */
    fun formatQuery(query: String): String = decodeDocument(
        GraphQLHttpDocument(GraphQLHttpSource.RAW_DOCUMENT, queryText = query.trim()),
    ).formattedQuery

    private fun decodeEnvelope(root: JsonObject, source: GraphQLHttpSource, get: Boolean = false,
        rawParameters: Map<String, String>? = null,
        resolvePersisted: ((GraphQLHttpDocument) -> GraphQLHttpDocument)? = null): GraphQLHttpDocument {
        fun string(name: String): String? = (root[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val query = string("query").orEmpty()
        val name = string("operationName")?.let { if (get && it.isEmpty()) null else it }
        val variables = root["variables"]
        val extensions = root["extensions"]
        val malformed = root["query"]?.let { it !is JsonPrimitive || !it.isString } == true ||
            root["operationName"]?.let { it != JsonNull && (it !is JsonPrimitive || !it.isString) } == true ||
            listOf(variables, extensions).any { it != null && it != JsonNull && it !is JsonObject } ||
            name?.let { it.length > MAXIMUM_NAME_CHARACTERS || it.isNotEmpty() && !NAME.matches(it) } == true
        val persisted = (extensions as? JsonObject)?.get("persistedQuery")
        val hash = (persisted as? JsonObject)?.get("sha256Hash") as? JsonPrimitive
        val version = (persisted as? JsonObject)?.get("version") as? JsonPrimitive
        val metadata = if (hash?.isString == true && HASH.matches(hash.content) && version?.isString == false && version.intOrNull == 1)
            GraphQLPersistedQuery(GraphQLQueryHash(hash.content.lowercase())) else null
        val document = GraphQLHttpDocument(source = source, queryText = query, persistedQuery = metadata,
            variablesJson = variables?.takeUnless { it == JsonNull }?.let { rawParameters?.get("variables") ?: it.toString() }.orEmpty(),
            extensionsJson = extensions?.takeUnless { it == JsonNull }?.let { rawParameters?.get("extensions") ?: it.toString() }.orEmpty(),
            suppliedOperationName = name)
        if (malformed) return document.copy(diagnostic = GraphQLHttpDiagnostic.MALFORMED_ENVELOPE)
        val decoded = if (query.isBlank() && metadata != null) document else decodeDocument(document, get)
        if (persisted != null && metadata == null) return decoded.copy(
            diagnostic = GraphQLHttpDiagnostic.INVALID_PERSISTED_QUERY, matchableOperations = emptyList())
        return resolvePersisted?.invoke(decoded) ?: decoded
    }

    /** Parses a lookup-only source using the original operation selector and HTTP method constraints. */
    fun decodeResolvedDocument(input: GraphQLHttpDocument, query: String, get: Boolean): GraphQLHttpDocument =
        decodeDocument(input.copy(queryText = query, formattedQuery = query, diagnostic = null), get)

    private fun decodeDocument(input: GraphQLHttpDocument, get: Boolean = false): GraphQLHttpDocument {
        val query = input.queryText
        if (query.length > MAXIMUM_INPUT_CHARACTERS) return input.copy(queryText = "", formattedQuery = "", diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        if (query.isBlank()) return input.copy(diagnostic = GraphQLHttpDiagnostic.MISSING_QUERY)
        val document = try {
            Parser().parseDocument(ParserEnvironment.newParserEnvironment().document(query).parserOptions(PARSER_OPTIONS).build())
        } catch (_: ParseCancelledTooDeepException) {
            return input.copy(diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        } catch (_: ParseCancelledTooManyCharsException) {
            return input.copy(diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        } catch (_: ParseCancelledTooManyNumericLiteralCharactersException) {
            return input.copy(diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        } catch (_: ParseCancelledException) {
            return input.copy(diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        } catch (_: Exception) {
            return input.copy(diagnostic = GraphQLHttpDiagnostic.INVALID_DOCUMENT)
        }
        val definitions = document.definitions.filterIsInstance<OperationDefinition>()
        val formatted = AstPrinter.printAst(document)
        if (formatted.length > MAXIMUM_INPUT_CHARACTERS * 2) return input.copy(diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        if (definitions.size > MAXIMUM_OPERATIONS) return input.copy(formattedQuery = formatted, diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        if (definitions.isEmpty() || document.definitions.any { it !is OperationDefinition && it !is FragmentDefinition }) {
            return input.copy(formattedQuery = formatted, diagnostic = GraphQLHttpDiagnostic.INVALID_DOCUMENT)
        }
        val duplicates = definitions.mapNotNull { it.name }.groupingBy { it }.eachCount().any { it.value > 1 }
        if (duplicates || definitions.size > 1 && definitions.any { it.name == null }) {
            return input.copy(formattedQuery = formatted, diagnostic = GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION)
        }
        val selected = if (input.suppliedOperationName != null) definitions.singleOrNull { it.name == input.suppliedOperationName }
            else definitions.singleOrNull()
        if (selected == null) return input.copy(formattedQuery = formatted, diagnostic =
            if (input.suppliedOperationName != null) GraphQLHttpDiagnostic.UNKNOWN_OPERATION else GraphQLHttpDiagnostic.AMBIGUOUS_OPERATION)
        if (selected.name?.length?.let { it > MAXIMUM_NAME_CHARACTERS } == true)
            return input.copy(formattedQuery = formatted, diagnostic = GraphQLHttpDiagnostic.LIMIT_EXCEEDED)
        val operation = GraphQLHttpOperation(selected.name, when (selected.operation) {
            OperationDefinition.Operation.QUERY -> GraphQLOperationType.QUERY
            OperationDefinition.Operation.MUTATION -> GraphQLOperationType.MUTATION
            OperationDefinition.Operation.SUBSCRIPTION -> GraphQLOperationType.SUBSCRIPTION
        })
        val diagnostic = GraphQLHttpDiagnostic.GET_OPERATION_NOT_QUERY.takeIf { get && operation.type != GraphQLOperationType.QUERY }
        return input.copy(formattedQuery = formatted, selectedOperation = operation,
            matchableOperations = if (diagnostic == null) listOf(operation) else emptyList(), diagnostic = diagnostic)
    }

    private fun envelopeHint(root: JsonObject): Boolean = "query" in root ||
        (root["extensions"] as? JsonObject)?.containsKey("persistedQuery") == true

    private fun parseJson(text: String): JsonElement? = if (text.length <= MAXIMUM_INPUT_CHARACTERS && withinJsonDepth(text))
        runCatching { JSON.parseToJsonElement(text) }.getOrNull() else null

    private fun failure(source: GraphQLHttpSource, diagnostic: GraphQLHttpDiagnostic) = GraphQLHttpDocument(source, diagnostic = diagnostic)

    /** Shared bounds for decoding, parsing and source-preserving edits. No cache or unbounded AST is retained. */
    companion object {
        /** Maximum raw bytes or decoded UTF-16 characters accepted for inspection. */
        const val MAXIMUM_INPUT_CHARACTERS: Int = 1_048_576
        private const val MAXIMUM_OPERATIONS = 256
        private const val MAXIMUM_NAME_CHARACTERS = 256
        private const val MAXIMUM_PARAMETERS = 4_096
        private val DECLARATION = Regex("^(query|mutation|subscription|fragment)\\b")
        private val HASH = Regex("[0-9a-fA-F]{64}")
        private val NAME = Regex("[_A-Za-z][_0-9A-Za-z]*")
        private val FIELDS = GraphQLHttpParameter.entries.mapTo(mutableSetOf()) { it.wireName }
        private val HINT_FIELDS = setOf("query", "operationName", "extensions")
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val HEX = "0123456789ABCDEF"
        private val PARSER_OPTIONS = ParserOptions.newParserOptions().maxCharacters(MAXIMUM_INPUT_CHARACTERS)
            .maxTokens(32_768).maxWhitespaceTokens(32_768).maxRuleDepth(128).captureLineComments(false)
            .captureSourceLocation(false).readerTrackData(false).redactTokenParserErrorMessages(true).build()

        /** Updates only edited JSON envelope fields under the same allocation/depth bounds as decoding.
         * Unknown fields survive. Invalid optional JSON stays a scalar for honest authoring validation. */
        fun updateJsonEnvelope(body: String, changes: Map<GraphQLHttpParameter, String?>): Result<String> = runCatching {
            require(body.length <= MAXIMUM_INPUT_CHARACTERS && withinJsonDepth(body))
            val root = JSON.parseToJsonElement(body) as? JsonObject ?: error("Expected a JSON envelope")
            JsonObject(root.toMutableMap().apply {
                changes.forEach { (field, value) ->
                    require(value == null || value.length <= MAXIMUM_INPUT_CHARACTERS && withinJsonDepth(value))
                    if (value == null) remove(field.wireName) else put(field.wireName,
                        if (field == GraphQLHttpParameter.QUERY || field == GraphQLHttpParameter.OPERATION_NAME) JsonPrimitive(value)
                        else runCatching { JSON.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) })
                }
            }).toString().also { require(it.length <= MAXIMUM_INPUT_CHARACTERS) }
        }

        /**
         * Replaces only explicitly edited GraphQL fields. Untouched raw pairs, order, spelling and fragment
         * remain byte-for-byte identical. Ambiguous/invalid targets fail rather than silently deleting data.
         */
        fun updateUrlParameters(url: String, edits: Map<GraphQLHttpParameter, String?>): Result<String> = runCatching {
            val changes = edits.mapKeys { it.key.wireName }
            require(url.length <= MAXIMUM_INPUT_CHARACTERS && changes.keys.all(FIELDS::contains))
            val decoded = decodeParameters(url)
            require(decoded.filter { it.first in changes }.groupingBy { it.first }.eachCount().none { it.value > 1 })
            val fragment = url.substringAfter('#', "").let { if ('#' in url) "#$it" else "" }
            val target = url.substringBefore('#')
            val base = target.substringBefore('?')
            val raw = if ('?' in target) target.substringAfter('?').split('&') else emptyList()
            val remaining = changes.toMutableMap()
            val result = raw.mapNotNull { pair ->
                val name = decodeForm(pair.substringBefore('='))
                if (name !in remaining) pair else {
                    val value = remaining.remove(name)
                    if (value == null) null else if (decodeForm(pair.substringAfter('=', "")) == value) pair
                    else "${pair.substringBefore('=')}=${encodeForm(value)}"
                }
            }.toMutableList()
            remaining.forEach { (name, value) -> if (value != null) result += "$name=${encodeForm(value)}" }
            (base + (if (result.isEmpty()) "" else "?${result.joinToString("&")}") + fragment).also {
                require(it.length <= MAXIMUM_INPUT_CHARACTERS)
            }
        }

        /** Cheap source-name predicate used before a semantic inspector loads any retained body. */
        fun hasGraphQLParameters(url: String): Boolean = url.length <= MAXIMUM_INPUT_CHARACTERS &&
            url.substringAfter('?', "").substringBefore('#').split('&', limit = MAXIMUM_PARAMETERS + 1)
                .take(MAXIMUM_PARAMETERS).any {
                    runCatching { decodeForm(it.substringBefore('=')) }.getOrNull() in HINT_FIELDS
                }

        private fun hasRecognizedParameter(url: String): Boolean = url.substringAfter('?', "").substringBefore('#')
            .split('&', limit = MAXIMUM_PARAMETERS + 1).take(MAXIMUM_PARAMETERS).any {
                runCatching { decodeForm(it.substringBefore('=')) }.getOrNull() in FIELDS
            }

        private fun decodeParameters(url: String): List<Pair<String, String>> {
            val target = url.substringBefore('#')
            if ('?' !in target) return emptyList()
            val pairs = target.substringAfter('?').split('&', limit = MAXIMUM_PARAMETERS + 1)
            if (pairs.size > MAXIMUM_PARAMETERS) throw LimitException()
            return pairs.map { decodeForm(it.substringBefore('=')) to decodeForm(it.substringAfter('=', "")) }
        }

        private fun decodeForm(text: String): String {
            val bytes = ByteArray(text.length * 3)
            var written = 0
            var offset = 0
            while (offset < text.length) {
                when (text[offset]) {
                    '%' -> {
                        require(offset + 2 < text.length)
                        val high = text[offset + 1].digitToIntOrNull(16) ?: error("Invalid percent encoding")
                        val low = text[offset + 2].digitToIntOrNull(16) ?: error("Invalid percent encoding")
                        bytes[written++] = (high * 16 + low).toByte(); offset += 3
                    }
                    '+' -> { bytes[written++] = 32; offset++ }
                    else -> {
                        val end = text.indexOfAny(charArrayOf('%', '+'), offset).let { if (it < 0) text.length else it }
                        val raw = text.substring(offset, end).encodeToByteArray(throwOnInvalidSequence = true)
                        raw.copyInto(bytes, written); written += raw.size; offset = end
                    }
                }
            }
            return bytes.decodeToString(0, written, throwOnInvalidSequence = true)
        }

        private fun strictUtf8(bytes: ByteArray): String = bytes.decodeToString(throwOnInvalidSequence = true)

        private fun encodeForm(value: String): String = buildString {
            value.encodeToByteArray(throwOnInvalidSequence = true).forEach { byte ->
                val code = byte.toInt() and 255
                when {
                    code in 65..90 || code in 97..122 || code in 48..57 || code.toChar() in "-._*" -> append(code.toChar())
                    code == 32 -> append('+')
                    else -> { append('%'); append(HEX[code ushr 4]); append(HEX[code and 15]) }
                }
            }
        }

        private fun looksLikeDocument(text: String): Boolean = DECLARATION.containsMatchIn(text) || text.startsWith("#") ||
            text.startsWith("{") && !text.drop(1).trimStart().startsWith('"') || text.startsWith('\uFEFF')

        // The JSON parser is recursive; reject deeply nested envelopes before invoking it. Strings and escapes
        // are scanned explicitly so braces in GraphQL source never count toward JSON nesting.
        /** Checks recursive JSON depth before allocating a JSON tree; strings and escapes do not count. */
        fun withinJsonDepth(text: String): Boolean {
            var depth = 0; var quoted = false; var escaped = false
            for (char in text) {
                if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
                else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; if (depth > 128) return false }
                    '}', ']' -> depth--
                }
            }
            return true
        }
        private class LimitException : RuntimeException()
    }
}
