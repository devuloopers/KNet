package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.graphql.GraphQLManifestImportResult
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorActions
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodyViewer

/** Read-only persisted-query lookup controls; importing or viewing never edits the wire request. */
@Composable
internal fun PersistedQueryToolbar(
    preview: BodyFormat.GraphQL,
    importResult: GraphQLManifestImportResult?,
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    var viewingQuery by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(KNetTheme.spacing.sm)) {
        Text("Persisted query", style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textPrimary)
        KNetInspectorActions {
            KNetButton(onClick = { viewingQuery = true }, enabled = preview.queryText.isNotBlank(), variant = ButtonVariant.Secondary) {
                Text("View query")
            }
            KNetButton(onClick = onImport, enabled = !isImporting, variant = ButtonVariant.Secondary) {
                Text(if (isImporting) "Importing…" else "Import manifest")
            }
        }
        KNetInspectorParagraph("SHA-256: ${preview.persistedQuery?.hash?.value.orEmpty()}")
        KNetInspectorParagraph(preview.queryProvenance.label)
        importResult?.let { result ->
            KNetInspectorParagraph(when (result) {
                is GraphQLManifestImportResult.Imported -> "Imported ${result.count} verified queries for this endpoint."
                is GraphQLManifestImportResult.Rejected -> result.reason.description
            })
        }
    }
    if (viewingQuery) {
        KNetDialog(onDismissRequest = { viewingQuery = false }, title = "Persisted query · ${preview.queryProvenance.label}") {
            GraphQLBodyViewer(format = preview, rawJsonText = "", modifier = Modifier.fillMaxHeight(.8f))
        }
    }
}
