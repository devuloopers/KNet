package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.application.contract.graphql.GraphQLManifestImportResult
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.inspector.KNetSha256Disclosure
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
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
    val hash = preview.persistedQuery?.hash?.value
    val sourceAvailable = preview.queryText.isNotBlank()
    var viewingQuery by remember(hash, preview.queryText) { mutableStateOf(false) }
    val spacing = KNetTheme.spacing
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    KNetSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md).padding(bottom = spacing.sm),
        color = colors.surfaceVariant,
        shape = KNetTheme.shapes.medium,
        border = BorderStroke(KNetTheme.dimensions.borderWidth, colors.border),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Column(Modifier.align(Alignment.CenterVertically)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        Icon(KNetIcons.Code, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall), tint = colors.accent)
                        Text("Persisted query", style = typography.labelMedium, color = colors.textPrimary, maxLines = 1, softWrap = false)
                        Text(preview.queryProvenance.label, style = typography.caption, color = colors.textSecondary,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    hash?.let { KNetSha256Disclosure(it) }
                }
                FlowRow(Modifier.align(Alignment.CenterVertically).padding(start = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (sourceAvailable) KNetButton(onClick = { viewingQuery = true }, variant = ButtonVariant.Ghost, size = ButtonSize.Compact) {
                        Text("View query", maxLines = 1, softWrap = false)
                    }
                    KNetButton(onClick = onImport, enabled = !isImporting, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
                        Text(if (isImporting) "Importing…" else "Import manifest", maxLines = 1, softWrap = false)
                    }
                }
            }
            importResult?.let { result ->
                KNetInspectorParagraph(when (result) {
                    is GraphQLManifestImportResult.Imported -> "Imported ${result.count} verified queries for this endpoint."
                    is GraphQLManifestImportResult.Rejected -> result.reason.description
                }, modifier = Modifier.padding(top = spacing.xs),
                    color = if (result is GraphQLManifestImportResult.Rejected) colors.semantic.error else colors.textSecondary)
            }
        }
    }
    if (viewingQuery) {
        KNetDialog(onDismissRequest = { viewingQuery = false }, title = "Persisted query · ${preview.queryProvenance.label}") {
            GraphQLBodyViewer(format = preview, rawJsonText = "", modifier = Modifier.fillMaxHeight(.8f))
        }
    }
}
