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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Protocol-neutral framed-message browser shared by gRPC and future duplex protocols. */
@Composable
internal fun ProtocolMessagesPanel(
    state: ProtocolMessagesUiState,
    onMessageSelected: (ProtocolMessageId) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    if (state.items.isEmpty() && !state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No framed protocol messages captured",
                style = typography.bodyMedium.copy(color = colors.textMuted),
            )
        }
        return
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .border(width = 1.dp, color = colors.border),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages", style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    state.totalCount.toString(),
                    style = typography.codeSmall.copy(color = colors.textMuted),
                )
            }
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { message -> message.id.value }) { message ->
                        ProtocolMessageRow(
                            message = message,
                            selected = message.id == state.selectedMessageId,
                            onClick = { onMessageSelected(message.id) },
                        )
                    }
                    if (state.nextCursor != null) {
                        item(key = "load-more") {
                            KNetButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                variant = ButtonVariant.Secondary,
                                size = ButtonSize.Compact,
                                loading = state.isLoading,
                            ) {
                                Text("Load older")
                            }
                        }
                    }
                }
                KNetVerticalScrollbar(
                    lazyListState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            state.selectedMessage?.let { message ->
                ProtocolMessageMetadata(
                    message = message,
                    truncated = state.selectedBodyTruncated,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val payloadSpec = remember(
                state.selectedMessageId,
                state.selectedBodyBytes,
                state.selectedBodyPresentation,
                state.isBodyLoading,
            ) {
                val bytes = state.selectedBodyBytes
                val presentation = state.selectedBodyPresentation
                when {
                    state.isBodyLoading -> PayloadInspectionSpec(isPreparing = true)
                    presentation != null -> PayloadInspectionSpec.fromPayload(
                        headers = listOf("content-type" to presentation.contentType),
                        rawBody = presentation.text,
                    )
                    bytes == null -> PayloadInspectionSpec.EMPTY
                    else -> PayloadInspectionSpec.fromPayload(
                        headers = listOf("content-type" to "text/plain"),
                        rawBody = bytes.toHexDump(),
                    )
                }
            }
            SmartBodyViewer(
                spec = payloadSpec,
                emptyTitle = if (state.selectedBodyUnavailable) "Message Body Unavailable" else "Empty Message",
                emptySubtitle = if (state.selectedBodyUnavailable) {
                    "The message metadata was retained, but its payload is not available in body storage."
                } else {
                    "This framed protocol message contains no payload bytes."
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProtocolMessageRow(
    message: ProtocolMessageSnapshot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val direction = when (message.direction) {
        TrafficDirection.CLIENT_TO_SERVER -> "REQUEST"
        TrafficDirection.SERVER_TO_CLIENT -> "RESPONSE"
    }
    val stateColor = when (message.state) {
        ProtocolMessageState.COMPLETE -> colors.semantic.success
        ProtocolMessageState.IN_PROGRESS -> colors.semantic.warning
        ProtocolMessageState.TRUNCATED,
        ProtocolMessageState.FAILED,
        ProtocolMessageState.CANCELLED -> colors.semantic.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.interaction.selectedOverlay else colors.surface)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${message.sequence}",
                style = typography.codeSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.width(8.dp))
            Text(direction, style = typography.codeSmall.copy(color = colors.accent))
            Spacer(Modifier.weight(1f))
            Text(message.state.name, style = typography.codeSmall.copy(color = stateColor))
        }
        Text(
            text = "${formatBytes(message.observedBytes)}${if (message.compressed) " · compressed" else ""}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = typography.codeSmall.copy(color = colors.textMuted),
        )
    }
}

@Composable
private fun ProtocolMessageMetadata(
    message: ProtocolMessageSnapshot,
    truncated: Boolean,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    Row(
        modifier = modifier
            .border(width = 1.dp, color = colors.border)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message.protocol.value.uppercase(),
            style = typography.codeSmall.copy(color = colors.accent, fontWeight = FontWeight.Bold),
        )
        Text("Message ${message.sequence}", style = typography.bodyMedium)
        Text(formatBytes(message.observedBytes), style = typography.codeSmall.copy(color = colors.textSecondary))
        message.compressionEncoding?.let { encoding ->
            Text(encoding, style = typography.codeSmall.copy(color = colors.textSecondary))
        }
        if (truncated) {
            Text("Preview truncated", style = typography.codeSmall.copy(color = colors.semantic.warning))
        }
        message.terminationReason?.code?.value?.let { error ->
            Text(
                text = error,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.codeSmall.copy(color = colors.semantic.error),
            )
        }
    }
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
