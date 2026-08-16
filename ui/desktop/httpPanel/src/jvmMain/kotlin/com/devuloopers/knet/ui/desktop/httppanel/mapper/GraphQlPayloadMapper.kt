package com.devuloopers.knet.ui.desktop.httppanel.mapper

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.payload.PayloadStrategy
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTO data class representing standard GraphQL HTTP POST JSON payload structure.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GraphQlPayloadDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val query: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val operationName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val variables: JsonElement? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val extensions: JsonElement? = null
)

/**
 * Thread-safe [PayloadStrategy] implementation responsible for bidirectional
 * transformation between serialized HTTP POST GraphQL JSON payload strings and structured [GraphQlState] models.
 *
 * Leverages Kotlin Serialization DTO ([GraphQlPayloadDto]) for direct deserialization and serialization,
 * and automatically formats GraphQL document AST syntax via [GraphQLBodyFormatter].
 *
 * @param jsonFormatter Configurable JSON formatter used for pretty-printing `$variables` and `$extensions`.
 * @param graphQlFormatter Formatter used for pretty-printing GraphQL query document AST syntax.
 */
class GraphQlPayloadMapper(
    private val jsonFormatter: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
    private val graphQlFormatter: GraphQLBodyFormatter = GraphQLBodyFormatter()
) : PayloadStrategy {

    override val bodyType: RequestBodyType = RequestBodyType.GRAPHQL

    override fun parse(rawText: String): StructuredPayloadState {
        val state = parseToUi(rawText)
        return StructuredPayloadState.GraphQL(
            queryText = state.queryText,
            variablesText = state.variablesText,
            operationName = state.operationName,
            extensionsText = state.extensionsText
        )
    }

    override fun serialize(state: StructuredPayloadState): String {
        val graphQl = when (state) {
            is StructuredPayloadState.GraphQL -> GraphQlState(
                queryText = state.queryText,
                variablesText = state.variablesText,
                operationName = state.operationName,
                extensionsText = state.extensionsText
            )

            is StructuredPayloadState.RawText -> return state.content
        }
        return serializeFromUi(graphQl)
    }

    /**
     * Parses a raw payload string (JSON blob or raw GraphQL query text) into a structured UI [GraphQlState].
     * Automatically pretty-prints query document AST syntax, variables, and extensions.
     *
     * @param payloadText Raw body payload string (e.g., from Traffic capture or saved session).
     * @return Formatted [GraphQlState] with populated query, variables, operationName, and extensions.
     */
    fun parseToUi(payloadText: String): GraphQlState {
        val trimmed = payloadText.trim()
        if (trimmed.isEmpty()) {
            return GraphQlState()
        }

        // If payload is a JSON object containing GraphQL fields
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val dto = jsonFormatter.decodeFromString<GraphQlPayloadDto>(trimmed)
                if (dto.query != null || dto.operationName != null) {
                    val formattedQuery = dto.query?.let { graphQlFormatter.formatQuery(it) } ?: ""
                    val varsStr = dto.variables?.let { jsonFormatter.encodeToString(it) }
                        ?: GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
                    val extStr = dto.extensions?.let { jsonFormatter.encodeToString(it) }
                        ?: GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER

                    return GraphQlState(
                        queryText = formattedQuery,
                        variablesText = varsStr,
                        operationName = dto.operationName ?: "",
                        extensionsText = extStr,
                        activeSubTab = GraphQlSubTab.QUERY
                    )
                }
            } catch (_: Exception) {
                // If decoding JSON fails, fallback to raw unescaped query text
            }
        }

        // If payload is raw unescaped GraphQL query text
        return GraphQlState(
            queryText = graphQlFormatter.formatQuery(trimmed),
            variablesText = GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER,
            operationName = "",
            extensionsText = GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER,
            activeSubTab = GraphQlSubTab.QUERY
        )
    }

    /**
     * Serializes a structured UI [GraphQlState] model back into a valid HTTP POST JSON payload string.
     *
     * @param state Target [GraphQlState] containing query, variables, operationName, and extensions.
     * @return Serialized JSON string suitable for HTTP transport.
     */
    fun serializeFromUi(state: GraphQlState): String {
        val trimmedQuery = state.queryText.trim()
        val trimmedOpName = state.operationName.trim()
        val varsJsonElement = parseJsonElementOrNull(state.variablesText)
        val extJsonElement = parseJsonElementOrNull(state.extensionsText)

        if (
            trimmedQuery.isEmpty() && trimmedOpName.isEmpty() &&
            varsJsonElement == null && extJsonElement == null
            ) {
            return ""
        }

        val dto = GraphQlPayloadDto(
            query = trimmedQuery.ifEmpty { null },
            operationName = trimmedOpName.ifEmpty { null },
            variables = varsJsonElement,
            extensions = extJsonElement
        )

        return jsonFormatter.encodeToString(dto)
    }

    private fun parseJsonElementOrNull(jsonStr: String): JsonElement? {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val element = jsonFormatter.parseToJsonElement(trimmed)
            if (element is JsonObject && element.isEmpty()) null else element
        } catch (_: Exception) {
            null
        }
    }
}
