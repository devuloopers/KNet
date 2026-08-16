package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.graphql.GraphQLQuerySynchronizer
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Strongly-typed sub-tabs for structured GraphQL request authoring.
 *
 * Serves as the Single Source of Truth (SSOT) for sub-tab metadata, syntax highlighting,
 * empty templates, payload accessors, and prettification logic.
 *
 * @property label User-facing display label for the sub-tab chip.
 * @property codeLanguage Strongly-typed [CodeLanguage] passed to [KNetCodeEditor] for syntax highlighting.
 * @property placeholder Default template or placeholder string shown when the editor buffer is empty.
 */
enum class GraphQlSubTab(
    val label: String,
    val codeLanguage: CodeLanguage,
    val placeholder: String
) {
    QUERY(
        label = "Query",
        codeLanguage = CodeLanguage.GRAPHQL,
        placeholder = "# Enter GraphQL Query or Mutation...\nquery GetUser {\n  user(id: 1) {\n    name\n    email\n  }\n}"
    ),
    VARIABLES(
        label = "Variables (JSON)",
        codeLanguage = CodeLanguage.JSON,
        placeholder = "// Enter GraphQL variables as JSON...\n{\n  \"id\": \"123\"\n}"
    ),
    EXTENSIONS(
        label = "Extensions (JSON)",
        codeLanguage = CodeLanguage.JSON,
        placeholder = "// Enter GraphQL extensions metadata as JSON...\n{\n  \"clientLibrary\": {\n    \"name\": \"apollo-kotlin\",\n    \"version\": \"5.0.0\"\n  }\n}"
    );

    /**
     * Extracts the active payload text for this sub-tab from the given [GraphQlState].
     *
     * @param state Immutable [GraphQlState] holding the GraphQL parameters.
     * @return String content for the active sub-tab.
     */
    fun getPayload(state: GraphQlState): String = when (this) {
        QUERY -> state.queryText
        VARIABLES -> state.variablesText
        EXTENSIONS -> state.extensionsText
    }

    /**
     * Updates the appropriate payload field in [GraphQlState] with new user-authored text.
     * For [QUERY], automatically extracts and synchronizes the active operation name.
     *
     * @param state Current [GraphQlState].
     * @param newText New payload text entered by the user.
     * @return Updated [GraphQlState].
     */
    fun updatePayload(state: GraphQlState, newText: String): GraphQlState = when (this) {
        QUERY -> {
            val extractedOpName = GraphQLQuerySynchronizer.extractOperationName(newText) ?: ""
            state.copy(queryText = newText, operationName = extractedOpName)
        }
        VARIABLES -> state.copy(variablesText = newText)
        EXTENSIONS -> state.copy(extensionsText = newText)
    }

    /**
     * Prettifies the active sub-tab payload and returns an updated [GraphQlState].
     *
     * @param state Current [GraphQlState].
     * @return Updated [GraphQlState] with formatted payload.
     */
    fun prettify(state: GraphQlState): GraphQlState = when (this) {
        QUERY -> {
            val formatted = GraphQLBodyFormatter.formatQuery(state.queryText)
            val extractedOpName = GraphQLQuerySynchronizer.extractOperationName(formatted) ?: ""
            state.copy(queryText = formatted, operationName = extractedOpName)
        }
        VARIABLES -> state.copy(variablesText = formatGraphQlJson(state.variablesText))
        EXTENSIONS -> state.copy(extensionsText = formatGraphQlJson(state.extensionsText))
    }

    private fun formatGraphQlJson(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed == "{}") {
            GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
        } else {
            JsonBodyFormatter.prettyPrintJson(raw)
        }
    }
}

/**
 * Immutable state model holding structured GraphQL request components.
 *
 * @property queryText Raw GraphQL query or mutation document syntax (clean unescaped text).
 * @property variablesText Pretty-printed JSON for GraphQL `$variables`.
 * @property operationName Optional name of the active GraphQL operation to execute.
 * @property extensionsText Pretty-printed JSON for GraphQL `$extensions`.
 * @property activeSubTab Currently selected GraphQL editor sub-tab ([GraphQlSubTab]).
 */
data class GraphQlState(
    val queryText: String = "",
    val variablesText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val operationName: String = "",
    val extensionsText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val activeSubTab: GraphQlSubTab = GraphQlSubTab.QUERY
) {
    companion object {
        const val DEFAULT_JSON_OBJECT_PLACEHOLDER: String = "{\n  \n}"
    }
}
