package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.port.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorHeaderConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/** Immutable input for the protocol-neutral framed-message intercept drawer. */
data class ProtocolMessageInterceptDrawerState(
    val events: List<PendingProtocolMessageBreakpoint>,
    val activeEvent: PendingProtocolMessageBreakpoint?,
    val isVisible: Boolean,
)

/** Actions supported by every framed protocol; format-specific editors can be added separately. */
data class ProtocolMessageInterceptDrawerActions(
    val selectEvent: (String) -> Unit,
    val continueUnchanged: (String) -> Unit,
    val replaceAndContinue: (String, ByteArray) -> Unit,
    val dropStream: (String) -> Unit,
    val dismiss: (String) -> Unit,
)

/**
 * Reusable drawer for one complete framed protocol message.
 *
 * The base editor deliberately uses a reversible hexadecimal representation. A protobuf-aware or
 * future protocol editor can be contributed later without changing the breakpoint gate or proxy.
 */
@Composable
fun ProtocolMessageInterceptDrawer(
    state: ProtocolMessageInterceptDrawerState,
    actions: ProtocolMessageInterceptDrawerActions,
    modifier: Modifier = Modifier,
) {
    var retainedEvent by remember { mutableStateOf(state.activeEvent) }
    LaunchedEffect(state.activeEvent) {
        state.activeEvent?.let { retainedEvent = it }
    }
    val event = state.activeEvent ?: retainedEvent

    KNetSideDrawer(
        visible = state.isVisible && state.activeEvent != null,
        size = KNetSideDrawerSize.EXPANDED,
        modifier = modifier,
    ) {
        val displayed = event ?: return@KNetSideDrawer
        Row(Modifier.fillMaxSize()) {
            MessageQueue(
                events = state.events,
                selectedId = displayed.id,
                onSelect = actions.selectEvent,
                modifier = Modifier.width(250.dp).fillMaxHeight(),
            )
            MessageEditor(
                event = displayed,
                onContinue = { actions.continueUnchanged(displayed.id) },
                onReplace = { bytes -> actions.replaceAndContinue(displayed.id, bytes) },
                onDrop = { actions.dropStream(displayed.id) },
                onDismiss = { actions.dismiss(displayed.id) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MessageQueue(
    events: List<PendingProtocolMessageBreakpoint>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    Column(modifier.background(colors.surfaceVariant).border(1.dp, colors.border)) {
        Text(
            text = "MESSAGE QUEUE  ${events.size}",
            style = KNetTheme.typography.labelMedium.copy(
                color = colors.textSecondary,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(events, key = { it.id }) { item ->
                val selected = item.id == selectedId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) colors.accent.copy(alpha = 0.14f) else colors.surface,
                            RoundedCornerShape(6.dp),
                        )
                        .border(
                            1.dp,
                            if (selected) colors.accent else colors.border,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelect(item.id) }
                        .handCursor()
                        .padding(10.dp),
                ) {
                    Text(
                        text = "${item.matchedProtocolId.value.uppercase()}  #${item.candidate.sequence}",
                        style = KNetTheme.typography.labelMedium.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = item.candidate.request.absoluteUrl(),
                        style = KNetTheme.typography.codeSmall.copy(color = colors.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageEditor(
    event: PendingProtocolMessageBreakpoint,
    onContinue: () -> Unit,
    onReplace: (ByteArray) -> Unit,
    onDrop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val candidate = event.candidate
    val retainedBytes = remember(event.id) { candidate.body.copyBytes(MAXIMUM_EDITOR_BYTES) }
    val truncated = candidate.body.size > retainedBytes.size
    var hexText by remember(event.id) { mutableStateOf(retainedBytes.toHexEditorText()) }
    val decoded = remember(hexText) { hexText.decodeHexEditorText() }

    Column(modifier.background(colors.surface).padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Intercepted ${event.matchedProtocolId.value.uppercase()} message",
                    style = KNetTheme.typography.heading.copy(color = colors.textPrimary),
                )
                Text(
                    text = buildString {
                        append(if (candidate.direction == TrafficDirection.CLIENT_TO_SERVER) "Client → server" else "Server → client")
                        append("  •  message #${candidate.sequence}  •  ${candidate.declaredBytes} bytes")
                        if (candidate.compressed) append("  •  compressed (${candidate.compressionEncoding ?: "unknown"})")
                    },
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Icon(Icons.Default.Close, contentDescription = "Drop intercepted stream", tint = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = candidate.request.absoluteUrl(),
            style = KNetTheme.typography.codeSmall.copy(color = colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        KNetCodeEditor(
            code = hexText,
            configuration = CodeEditorConfiguration(
                mode = EditorMode.Editable,
                language = CodeLanguage.PLAIN,
                isFoldingEnabled = false,
                isWordWrapEnabled = true,
                header = CodeEditorHeaderConfiguration(showLineCount = true, showFoldActions = false),
                placeholder = "Enter hexadecimal message bytes",
            ),
            actions = CodeEditorActions(onTextChange = { hexText = it }),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (truncated) {
            Text(
                text = "The message exceeds the ${MAXIMUM_EDITOR_BYTES / 1024} KB edit limit. Forward unchanged or drop the stream.",
                style = KNetTheme.typography.caption.copy(color = colors.semantic.warning),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (decoded == null) {
            Text(
                text = "Hexadecimal input must contain complete byte pairs.",
                style = KNetTheme.typography.caption.copy(color = colors.semantic.error),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val actionScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(actionScrollState),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KNetButton(onClick = onDrop, variant = ButtonVariant.Danger) { Text("Drop Stream") }
                Spacer(Modifier.width(8.dp))
                KNetButton(onClick = onContinue, variant = ButtonVariant.Secondary) { Text("Forward Unchanged") }
                Spacer(Modifier.width(8.dp))
                KNetButton(
                    onClick = { decoded?.let(onReplace) },
                    enabled = decoded != null && !truncated,
                ) { Text("Apply & Forward") }
            }
            KNetHorizontalScrollbar(
                scrollState = actionScrollState,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

private fun ByteArray.toHexEditorText(): String =
    asSequence()
        .chunked(16)
        .joinToString("\n") { row -> row.joinToString(" ") { byte -> "%02X".format(byte) } }

private fun String.decodeHexEditorText(): ByteArray? {
    val compact = filterNot(Char::isWhitespace)
    if (compact.length % 2 != 0 || compact.any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(compact.length / 2) { index ->
        val high = compact[index * 2].digitToInt(16)
        val low = compact[index * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}

private const val MAXIMUM_EDITOR_BYTES = 1_048_576
