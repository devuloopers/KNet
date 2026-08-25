package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.port.apistudio.HttpLiveResponseRecord
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec

/** Generic bounded timeline for semantic records emitted by a long-lived HTTP response interpreter. */
@Composable
internal fun LiveHttpResponseView(
    state: ResponseInspectorState,
    actions: ResponseInspectorActions,
    modifier: Modifier = Modifier,
) {
    val live = requireNotNull(state.liveResponse)
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    var query by remember { mutableStateOf("") }
    val visibleRecords = remember(live.records, query) {
        query.trim().takeIf(String::isNotEmpty)?.let { needle ->
            live.records.filter { record ->
                record.title.contains(needle, ignoreCase = true) ||
                    record.data.contains(needle, ignoreCase = true) ||
                    record.attributes.any { (name, value) ->
                        name.contains(needle, ignoreCase = true) || value.contains(needle, ignoreCase = true)
                    }
            }
        } ?: live.records
    }
    val selected = live.records.firstOrNull { it.sequence == live.selectedSequence }
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${live.protocolLabel} · ${state.statusCode} ${state.statusText}".trim(),
                    style = typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = buildString {
                        if (query.isBlank()) {
                            append(live.records.size).append(" retained records · ")
                        } else {
                            append(visibleRecords.size).append(" matching · ")
                            append(live.records.size).append(" retained · ")
                        }
                        append(live.receivedBytes).append(" B received")
                        if (live.gapCount > 0L) append(" · ").append(live.gapCount).append(" gaps")
                        live.lastGapReason?.let { reason ->
                            append(" · Last gap: ").append(humanReadableStreamGapReason(reason))
                        }
                        if (live.droppedRecordCount > 0L) {
                            append(" · ").append(live.droppedRecordCount).append(" older records dropped")
                        }
                    },
                    style = typography.caption.copy(color = colors.textSecondary),
                )
            }
            KNetSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search events",
                modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
            )
            KNetButton(
                onClick = actions.onClearVisibleLiveRecords,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
                enabled = live.records.isNotEmpty(),
            ) { Text("Clear events") }
        }
        HorizontalDivider(color = colors.border)

        Box(modifier = Modifier.fillMaxWidth().weight(0.44f)) {
            if (visibleRecords.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "Waiting for the first stream record…" else "No matching records",
                        style = typography.bodyMedium.copy(color = colors.textMuted),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = KNetTheme.spacing.sm),
                ) {
                    items(visibleRecords, key = HttpLiveResponseRecord::sequence) { record ->
                        val selectedRow = record.sequence == live.selectedSequence
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedRow) colors.interaction.selectedOverlay else colors.background)
                                .clickable { actions.onLiveRecordSelected(record.sequence) }
                                .handCursor()
                                .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
                        ) {
                            Text(
                                text = record.sequence.toString(),
                                style = typography.bodySmall.copy(color = colors.textMuted, fontFamily = FontFamily.Monospace),
                                modifier = Modifier.width(48.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.title,
                                    style = typography.bodyMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = record.attributes.joinToString(" · ") { (name, value) -> "$name: $value" }
                                        .ifBlank { record.data.lineSequence().firstOrNull().orEmpty() },
                                    style = typography.caption.copy(color = colors.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            KNetCopyButton(textToCopy = record.raw, contentDescription = "Copy raw stream record")
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }
                KNetVerticalScrollbar(
                    lazyListState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
        HorizontalDivider(color = colors.border)
        Column(modifier = Modifier.fillMaxWidth().weight(0.56f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.let { "Record ${it.sequence} data" } ?: "Record data",
                    style = typography.labelMedium.copy(color = colors.textSecondary),
                    modifier = Modifier.weight(1f),
                )
                selected?.let { record ->
                    KNetCopyButton(textToCopy = record.data, contentDescription = "Copy stream record data")
                }
            }
            SmartBodyViewer(
                spec = PayloadInspectionSpec.fromPayload(
                    headers = listOf("content-type" to "text/plain; charset=utf-8"),
                    rawBody = selected?.data.orEmpty(),
                ),
                emptyTitle = "No record selected",
                emptySubtitle = "Select a stream record to inspect its data.",
                modifier = Modifier.fillMaxSize(),
            )
        }
        live.terminalReason?.let { reason ->
            KNetSurface(
                color = colors.surfaceVariant,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = reason,
                    style = typography.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(KNetTheme.spacing.sm),
                )
            }
        }
    }
}

/** Converts stable machine-readable stream failure codes into compact presentation copy. */
internal fun humanReadableStreamGapReason(reason: String): String = reason
    .split('_')
    .filter(String::isNotBlank)
    .joinToString(" ") { word -> word.lowercase() }
    .replaceFirstChar { character -> character.titlecase() }
