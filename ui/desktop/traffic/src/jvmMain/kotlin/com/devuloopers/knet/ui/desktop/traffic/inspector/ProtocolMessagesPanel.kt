package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.inspector.*
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.traffic.model.ProtocolMessagesUiState

/** Protocol-neutral list/detail workspace; protocol-owned decoders supply selected-message facts. */
@Composable
internal fun ProtocolMessagesPanel(
    state: ProtocolMessagesUiState,
    onMessageSelected: (ProtocolMessageId) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    events: Boolean = false,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val typography = KNetTheme.typography
    if (state.items.isEmpty() && !state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(if (state.isLoading) "Loading captured records…" else if (events) "No stream records captured" else "No messages captured",
                    style = typography.bodyMedium.copy(color = colors.textMuted))
            }
        }
        return
    }
    KNetInspectorMessageLayout(modifier.fillMaxSize(), contentIdentity = state.exchangeId, list = {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                MessageText(if (events) "Records" else "Messages", Modifier.weight(1f), bold = true)
                MessageText(state.totalCount.toString(), color = colors.textMuted)
            }
            HorizontalDivider()
            val listState = remember(state.exchangeId) { LazyListState() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = KNetTheme.dimensions.scrollbarWidth)) {
                    items(state.items, key = { it.id.value }) { message ->
                        ProtocolMessageRow(message, message.id == state.selectedMessageId) { onMessageSelected(message.id) }
                    }
                    if (state.nextCursor != null) item(key = "load-more") {
                        KNetButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                            variant = ButtonVariant.Secondary, size = ButtonSize.Compact, loading = state.isLoading) {
                            Text("Load more")
                        }
                    }
                }
                KNetVerticalScrollbar(lazyListState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }, detail = {
        val message = state.selectedMessage
        if (message == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MessageText(if (state.isLoading) "Loading captured records…" else if (events) "Select a stream record" else "Select a message", color = colors.textMuted)
            }
        } else {
            val presentation = state.selectedBodyPresentation.takeUnless { state.isBodyLoading }
            KNetInspectorMessageMetadata(Modifier.fillMaxWidth(), contentIdentity = message.id.value) {
                MessageText(presentation?.title ?: "${message.kind.displayName()} · ${message.direction.displayName()}",
                    Modifier.fillMaxWidth(), bold = true)
                KNetInspectorFields(buildList {
                    add(KNetInspectorField("Direction", message.direction.displayName()))
                    add(KNetInspectorField("State", message.state.displayName()))
                    add(KNetInspectorField("Captured size", formatBytes(message.observedBytes)))
                    presentation?.fields.orEmpty().forEach { add(KNetInspectorField(it.label, it.value, true)) }
                    presentation?.schemaName?.takeIf { presentation.fields.isEmpty() }?.let {
                        add(KNetInspectorField("Schema", it, true))
                    }
                    add(KNetInspectorField("Compression", if (message.compressed) message.compressionEncoding ?: "Encoding unavailable" else "Uncompressed"))
                    message.terminationReason?.code?.value?.let { add(KNetInspectorField("Termination", it, true)) }
                })
                if (state.selectedBodyTruncated || message.state == ProtocolMessageState.TRUNCATED)
                    KNetInspectorParagraph("Preview truncated. Only retained bytes are shown.", color = colors.semantic.warning)
            }
            HorizontalDivider()
            val payloadSpec = remember(message.id, state.selectedBodyBytes, presentation, state.isBodyLoading) {
                when {
                    state.isBodyLoading -> PayloadInspectionSpec(isPreparing = true)
                    presentation != null -> PayloadInspectionSpec.fromPayload(
                        headers = listOf("content-type" to presentation.contentType), rawBody = presentation.text)
                    state.selectedBodyBytes == null -> PayloadInspectionSpec.EMPTY
                    else -> PayloadInspectionSpec.fromPayload(headers = listOf("content-type" to "text/plain"),
                        rawBody = state.selectedBodyBytes.toHexDump())
                }
            }
            SmartBodyViewer(spec = payloadSpec,
                emptyTitle = if (state.selectedBodyUnavailable) "Message Body Unavailable" else "Empty Message",
                emptySubtitle = if (state.selectedBodyUnavailable)
                    "Metadata was retained, but the payload is unavailable in body storage."
                    else "This captured record contains no payload bytes.",
                modifier = Modifier.weight(1f).fillMaxWidth())
        }
    })
}

@Composable
private fun ProtocolMessageRow(message: ProtocolMessageSnapshot, selected: Boolean, onClick: () -> Unit) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val stateColor = when (message.state) {
        ProtocolMessageState.COMPLETE -> colors.textMuted
        ProtocolMessageState.IN_PROGRESS, ProtocolMessageState.TRUNCATED -> colors.semantic.warning
        ProtocolMessageState.FAILED, ProtocolMessageState.CANCELLED -> colors.semantic.error
    }
    Column(Modifier.fillMaxWidth().background(if (selected) colors.interaction.selectedOverlay else colors.surface)
        .clickable(onClick = onClick).handCursor().padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MessageText("#${message.sequence}", color = colors.textMuted)
            MessageText(message.direction.displayName(), Modifier.weight(1f), color = colors.accent)
        }
        MessageText(message.kind.displayName(), Modifier.fillMaxWidth(), bold = true)
        MessageText("${formatBytes(message.observedBytes)} · ${message.state.displayName()}", Modifier.fillMaxWidth(), color = stateColor)
    }
}

@Composable
private fun MessageText(text: String, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = KNetTheme.colors.textPrimary, bold: Boolean = false) {
    KNetInspectorText(text, modifier, color = color, bold = bold)
}

private fun TrafficDirection.displayName(): String = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> "Sent"
    TrafficDirection.SERVER_TO_CLIENT -> "Received"
}

private fun ProtocolMessageState.displayName(): String = when (this) {
    ProtocolMessageState.IN_PROGRESS -> "In progress"
    ProtocolMessageState.COMPLETE -> "Complete"
    ProtocolMessageState.TRUNCATED -> "Truncated"
    ProtocolMessageState.FAILED -> "Failed"
    ProtocolMessageState.CANCELLED -> "Cancelled"
}

private fun com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.displayName(): String = when (this) {
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.TEXT -> "Text"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.BINARY -> "Binary"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.PING -> "Ping"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.PONG -> "Pong"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.CLOSE -> "Close"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.RECORD -> "Stream record"
    com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.DATA -> "Data"
    else -> value
}

private fun ByteArray.toHexDump(): String = buildString(size * 3) {
    this@toHexDump.forEachIndexed { index, byte ->
        if (index > 0) {
            if (index % 16 == 0) append('\n') else append(' ')
        }
        append(byte.toUByte().toString(16).padStart(2, '0'))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "${"%.2f".format(bytes / 1_048_576.0)} MB"
    bytes >= 1_024L -> "${"%.2f".format(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}
