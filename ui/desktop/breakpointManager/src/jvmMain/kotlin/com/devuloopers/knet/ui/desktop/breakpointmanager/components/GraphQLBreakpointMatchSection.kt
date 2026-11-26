package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointMatch
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointScope
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorText
import com.devuloopers.knet.ui.core.components.inspector.KNetSha256Disclosure
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Current GraphQL fields, including the implicit scope of a saved version-one rule. */
internal data class GraphQLMatchEditorState(
    val scope: GraphQLBreakpointScope,
    val hashText: String,
    val operationName: String,
    val requestOperation: String,
) {
    val hash = GraphQLBreakpointMatch.parseHash(hashText)
    val isComplete: Boolean
        get() = when (scope) {
            GraphQLBreakpointScope.CHOOSE -> false
            GraphQLBreakpointScope.EXACT -> hash != null
            GraphQLBreakpointScope.OPERATION -> GraphQLBreakpointMatch.isOperationName(operationName.trim())
            GraphQLBreakpointScope.ENDPOINT -> true
        }

    companion object {
        fun from(values: List<ProtocolCriteriaValue>): GraphQLMatchEditorState {
            val fields = values.associate { it.fieldId to it.value }
            val operation = fields[GraphQLBreakpointMatch.operationNameField]
            val scope = fields[GraphQLBreakpointMatch.scopeField]?.let { token ->
                GraphQLBreakpointScope.entries.firstOrNull { it.token == token } ?: GraphQLBreakpointScope.CHOOSE
            } ?: when {
                operation == null -> GraphQLBreakpointScope.CHOOSE
                operation.isBlank() -> GraphQLBreakpointScope.ENDPOINT
                else -> GraphQLBreakpointScope.OPERATION
            }
            return GraphQLMatchEditorState(scope, fields[GraphQLBreakpointMatch.hashField].orEmpty(),
                operation.orEmpty(), fields[GraphQLBreakpointMatch.requestOperationField].orEmpty())
        }
    }
}

/** Renders only the GraphQL matching section of the shared drawer. */
@Composable
internal fun GraphQLBreakpointMatchSection(
    state: GraphQLMatchEditorState,
    hashPrefilled: Boolean,
    onFieldChange: (ProtocolCriteriaFieldId, String) -> Unit,
) {
    val colors = KNetTheme.colors
    val scopeChoices = GraphQLBreakpointScope.entries.filter {
        it != GraphQLBreakpointScope.CHOOSE &&
            (it != GraphQLBreakpointScope.OPERATION || !hashPrefilled || state.operationName.isNotBlank())
    }
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Text("Match", style = KNetTheme.typography.caption, color = colors.textMuted)
        KNetDropdown(
            selectedItem = state.scope,
            items = scopeChoices,
            onItemSelected = { onFieldChange(GraphQLBreakpointMatch.scopeField, it.token) },
            modifier = Modifier.fillMaxWidth(),
            itemText = { it.label },
        )
        when (state.scope) {
            GraphQLBreakpointScope.EXACT -> {
                val hash = state.hash
                if (hashPrefilled && hash != null) {
                    KNetSha256Disclosure(hash.value)
                } else {
                    KNetTextField(state.hashText, { onFieldChange(GraphQLBreakpointMatch.hashField, it) },
                        Modifier.fillMaxWidth(), config = InputFieldConfig(placeholder = "64-character SHA-256"))
                }
                if (state.requestOperation.isNotBlank()) {
                    KNetInspectorText("From request · ${state.requestOperation}", Modifier.fillMaxWidth(), code = true)
                }
                if (hash != null) KNetInspectorParagraph("Matches this persisted query only.")
            }
            GraphQLBreakpointScope.OPERATION -> {
                KNetTextField(state.operationName, { onFieldChange(GraphQLBreakpointMatch.operationNameField, it) },
                    Modifier.fillMaxWidth(), config = InputFieldConfig(placeholder = "Operation name"))
                KNetInspectorParagraph("Matches requests with this verified operation name.")
            }
            GraphQLBreakpointScope.ENDPOINT ->
                KNetInspectorParagraph("Matches every GraphQL operation within the endpoint and method filters.",
                    color = colors.semantic.warning)
            GraphQLBreakpointScope.CHOOSE -> KNetInspectorParagraph("Choose what this rule should pause.")
        }
    }
}
