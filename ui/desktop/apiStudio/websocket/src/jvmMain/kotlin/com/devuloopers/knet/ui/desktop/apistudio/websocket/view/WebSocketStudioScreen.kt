package com.devuloopers.knet.ui.desktop.apistudio.websocket.view

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioAuthoringEditorLayout
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioSplitWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioMessageKind
import com.devuloopers.knet.ui.desktop.apistudio.websocket.viewmodel.WebSocketStudioViewModel
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/** Renders WebSocket handshake authoring, message composition, and a live bidirectional timeline. */
@Composable
fun WebSocketStudioScreen(
    viewModel: WebSocketStudioViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val colors = KNetTheme.colors
    var authoringRatio by remember { mutableFloatStateOf(DEFAULT_AUTHORING_RATIO) }

    ApiStudioSplitWorkspace(
        authoringRatio = authoringRatio,
        onAuthoringRatioChange = { authoringRatio = it },
        authoringPane = { paneModifier ->
            Column(modifier = paneModifier.fillMaxSize()) {
                WebSocketTargetBar(
                    state = state,
                    onUrlChanged = viewModel::updateUrl,
                    onConnect = viewModel::connect,
                    onCancel = viewModel::cancelSession,
                    onClose = viewModel::closeSession,
                )
                HorizontalDivider(color = colors.border)
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth()
                            .background(colors.semantic.errorContainer)
                            .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
                        style = KNetTheme.typography.bodySmall.copy(color = colors.semantic.error),
                    )
                }
                WebSocketRequestPane(
                    state = state,
                    onTimeoutChanged = viewModel::updateTimeout,
                    onSubprotocolsChanged = viewModel::updateSubprotocols,
                    onAddHeader = viewModel::addHeader,
                    onUpdateHeader = viewModel::updateHeader,
                    onRemoveHeader = viewModel::removeHeader,
                    onMessageKindSelected = viewModel::selectMessageKind,
                    onMessageChanged = viewModel::updateMessageContent,
                    onSendMessage = viewModel::sendMessage,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        },
        resultPane = { paneModifier ->
            WebSocketEventPane(
                state = state,
                onEventSelected = viewModel::selectEvent,
                modifier = paneModifier.fillMaxSize(),
            )
        },
        modifier = modifier.fillMaxSize().background(colors.surface),
    )
}

@Composable
private fun WebSocketTargetBar(
    state: WebSocketStudioState,
    onUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = KNetTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
    ) {
        KNetTextField(
            value = state.url,
            onValueChange = onUrlChanged,
            modifier = Modifier.weight(1f),
            config = InputFieldConfig(placeholder = "ws:// or wss:// endpoint"),
        )
        when {
            state.isConnecting -> KNetButton(
                onClick = onCancel,
                loading = true,
                clickableWhileLoading = true,
            ) { Text("Cancel") }
            state.isConnected -> KNetButton(onClick = onClose, variant = ButtonVariant.Secondary) {
                Text("Close")
            }
            else -> KNetButton(onClick = onConnect, enabled = state.canConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun WebSocketRequestPane(
    state: WebSocketStudioState,
    onTimeoutChanged: (String) -> Unit,
    onSubprotocolsChanged: (String) -> Unit,
    onAddHeader: () -> Unit,
    onUpdateHeader: (Int, String?, String?) -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onMessageKindSelected: (WebSocketStudioMessageKind) -> Unit,
    onMessageChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val scrollState = rememberScrollState()
    ApiStudioAuthoringEditorLayout(
        modifier = modifier.padding(KNetTheme.spacing.md),
        controls = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = KNetTheme.spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                    ) {
                        KNetTextField(
                            value = state.subprotocols,
                            onValueChange = onSubprotocolsChanged,
                            modifier = Modifier.weight(1f),
                            config = InputFieldConfig(placeholder = "Subprotocols, comma separated"),
                        )
                        KNetTextField(
                            value = state.connectTimeoutMillis,
                            onValueChange = onTimeoutChanged,
                            modifier = Modifier.width(132.dp),
                            config = InputFieldConfig(placeholder = "Timeout ms"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Handshake headers",
                            style = KNetTheme.typography.labelMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        KNetButton(onClick = onAddHeader, variant = ButtonVariant.Ghost, size = ButtonSize.Compact) {
                            Text("+ Add")
                        }
                    }
                    if (state.headers.isEmpty()) {
                        Text(
                            "Optional headers only. WebSocket upgrade headers are generated automatically.",
                            modifier = Modifier.padding(top = KNetTheme.spacing.xs),
                            style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                        )
                    } else {
                        state.headers.forEachIndexed { index, header ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                            ) {
                                KNetTextField(
                                    value = header.name,
                                    onValueChange = { onUpdateHeader(index, it, null) },
                                    modifier = Modifier.weight(0.4f),
                                    config = InputFieldConfig(placeholder = "Header name"),
                                )
                                KNetTextField(
                                    value = header.value,
                                    onValueChange = { onUpdateHeader(index, null, it) },
                                    modifier = Modifier.weight(0.6f),
                                    config = InputFieldConfig(placeholder = "Value"),
                                )
                                KNetButton(
                                    onClick = { onRemoveHeader(index) },
                                    variant = ButtonVariant.Ghost,
                                    size = ButtonSize.Compact,
                                ) { Text("Remove") }
                            }
                        }
                    }
                }
                KNetVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        toolbar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
            ) {
                Text(
                    "Message",
                    style = KNetTheme.typography.labelMedium.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                KNetDropdown(
                    selectedItem = state.messageKind,
                    items = WebSocketStudioMessageKind.entries,
                    onItemSelected = onMessageKindSelected,
                    itemText = { kind -> if (kind == WebSocketStudioMessageKind.TEXT) "Text" else "Binary (Base64)" },
                )
                Spacer(Modifier.weight(1f))
                KNetButton(
                    onClick = onSendMessage,
                    enabled = state.canSend,
                    size = ButtonSize.Compact,
                ) { Text("Send") }
            }
        },
        editor = {
            KNetCodeEditor(
                code = state.messageContent,
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = CodeLanguage.PLAIN,
                    placeholder = if (state.messageKind == WebSocketStudioMessageKind.TEXT) {
                        "Enter a text message"
                    } else {
                        "Enter Base64-encoded binary data"
                    },
                ),
                actions = CodeEditorActions(onTextChange = onMessageChanged),
                modifier = Modifier.fillMaxSize().padding(top = KNetTheme.spacing.xs),
            )
        },
    )
}

@Composable
private fun WebSocketEventPane(
    state: WebSocketStudioState,
    onEventSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val listState = rememberLazyListState()
    val selectedEvent = state.selectedEventIndex?.let(state.events::getOrNull)
    Column(modifier = modifier.padding(KNetTheme.spacing.md)) {
        Text("Session timeline", style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary))
        if (state.events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Connect to see the handshake, outbound messages, inbound messages, and close status.",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textMuted),
                )
            }
            return@Column
        }
        Box(modifier = Modifier.fillMaxWidth().weight(0.45f).padding(top = KNetTheme.spacing.sm)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = KNetTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
            ) {
                itemsIndexed(state.events) { index, event ->
                    val selected = index == state.selectedEventIndex
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .border(
                                1.dp,
                                if (selected) colors.borderFocused else colors.border,
                                KNetTheme.shapes.small,
                            )
                            .background(
                                if (selected) colors.interaction.selectedOverlay else colors.surfaceVariant,
                                KNetTheme.shapes.small,
                            )
                            .clickable { onEventSelected(index) }
                            .padding(KNetTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            event.title(),
                            modifier = Modifier.weight(1f),
                            style = KNetTheme.typography.labelMedium.copy(color = colors.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(event.detail(), style = KNetTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
            KNetVerticalScrollbar(
                lazyListState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = KNetTheme.spacing.sm),
            color = colors.border,
        )
        KNetCodeEditor(
            code = selectedEvent.presentationText(),
            configuration = CodeEditorConfiguration(
                mode = EditorMode.ReadOnly,
                language = selectedEvent.presentationLanguage(),
                placeholder = "Select a session event",
            ),
            modifier = Modifier.fillMaxWidth().weight(0.55f),
        )
    }
}

private fun ApiStudioProtocolExecutionEvent.title(): String = when (this) {
    is ApiStudioProtocolExecutionEvent.Started -> "Connected"
    is ApiStudioProtocolExecutionEvent.Message -> when (message.direction) {
        ApiStudioProtocolMessageDirection.OUTBOUND -> "Client message ${message.sequence}"
        ApiStudioProtocolMessageDirection.INBOUND -> "Server message ${message.sequence}"
    }
    is ApiStudioProtocolExecutionEvent.Completed -> "Closed"
    is ApiStudioProtocolExecutionEvent.Failed -> "Failed"
}

private fun ApiStudioProtocolExecutionEvent.detail(): String = when (this) {
    is ApiStudioProtocolExecutionEvent.Started -> "Open"
    is ApiStudioProtocolExecutionEvent.Message -> "${message.copyPayload().size} B"
    is ApiStudioProtocolExecutionEvent.Completed -> statusCode
    is ApiStudioProtocolExecutionEvent.Failed -> code
}

private fun ApiStudioProtocolExecutionEvent?.presentationText(): String = when (this) {
    null -> ""
    is ApiStudioProtocolExecutionEvent.Started -> summary
    is ApiStudioProtocolExecutionEvent.Message -> message.displayText
    is ApiStudioProtocolExecutionEvent.Completed -> buildString {
        append("Close code: ").append(statusCode)
        statusMessage?.let { append("\nReason: ").append(it) }
        append("\nProtocol: ").append(actualProtocol)
    }
    is ApiStudioProtocolExecutionEvent.Failed -> buildString {
        append("Code: ").append(code)
        append("\nMessage: ").append(message)
        append("\nRetryable: ").append(if (retryable) "yes" else "no")
    }
}

private fun ApiStudioProtocolExecutionEvent?.presentationLanguage(): CodeLanguage {
    val text = (this as? ApiStudioProtocolExecutionEvent.Message)?.message?.displayText?.trimStart().orEmpty()
    return if (text.startsWith('{') || text.startsWith('[')) CodeLanguage.JSON else CodeLanguage.PLAIN
}

private const val DEFAULT_AUTHORING_RATIO = 0.5f
