package com.devuloopers.knet.ui.desktop.apistudio.grpc.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioSplitWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.grpc.file.GrpcDescriptorFilePicker
import com.devuloopers.knet.ui.desktop.apistudio.grpc.file.NativeGrpcDescriptorFilePicker
import com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState
import com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel.GrpcStudioViewModel
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

@Composable
fun GrpcStudioScreen(
    viewModel: GrpcStudioViewModel,
    modifier: Modifier = Modifier,
    filePicker: GrpcDescriptorFilePicker = NativeGrpcDescriptorFilePicker,
) {
    val state by viewModel.state.collectAsState()
    val colors = KNetTheme.colors
    var authoringRatio by remember { mutableFloatStateOf(DEFAULT_AUTHORING_RATIO) }

    ApiStudioSplitWorkspace(
        authoringRatio = authoringRatio,
        onAuthoringRatioChange = { authoringRatio = it },
        authoringPane = { paneModifier ->
            Column(modifier = paneModifier.fillMaxSize()) {
                GrpcTargetBar(
                    state = state,
                    onHostChanged = viewModel::updateTargetHost,
                    onPortChanged = viewModel::updateTargetPort,
                    onToggleTls = viewModel::toggleTls,
                    onImportDescriptor = {
                        filePicker.choose { result ->
                            result.onSuccess { path -> path?.let(viewModel::importDescriptor) }
                                .onFailure { error ->
                                    viewModel.reportDescriptorImportFailure(
                                        error.message ?: "Unable to open the descriptor picker.",
                                    )
                                }
                        }
                    },
                    onReflect = viewModel::reflect,
                    onExecute = viewModel::execute,
                    onCancel = viewModel::cancel,
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
                GrpcRequestPane(
                    state = state,
                    onOperationSelected = { operation -> viewModel.selectOperation(operation.id) },
                    onDeadlineChanged = viewModel::updateDeadline,
                    onMessageSelected = viewModel::selectOutboundMessage,
                    onMessageChanged = viewModel::updateOutboundMessage,
                    onAddMessage = viewModel::addOutboundMessage,
                    onRemoveMessage = viewModel::removeSelectedOutboundMessage,
                    onSendMessage = viewModel::sendSelectedMessage,
                    onHalfClose = viewModel::halfClose,
                    onAddMetadata = viewModel::addMetadata,
                    onUpdateMetadata = viewModel::updateMetadata,
                    onRemoveMetadata = viewModel::removeMetadata,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        },
        resultPane = { paneModifier ->
            GrpcEventPane(
                state = state,
                onEventSelected = viewModel::selectEvent,
                modifier = paneModifier.fillMaxSize(),
            )
        },
        modifier = modifier.fillMaxSize().background(colors.surface),
    )
}

@Composable
private fun GrpcTargetBar(
    state: GrpcStudioState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onToggleTls: () -> Unit,
    onImportDescriptor: () -> Unit,
    onReflect: () -> Unit,
    onExecute: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            KNetTextField(
                value = state.targetHost,
                onValueChange = onHostChanged,
                modifier = Modifier.weight(1f),
                config = InputFieldConfig(placeholder = "gRPC host"),
            )
            KNetTextField(
                value = state.targetPort,
                onValueChange = onPortChanged,
                modifier = Modifier.width(92.dp),
                config = InputFieldConfig(placeholder = "Port"),
            )
            KNetButton(
                onClick = onToggleTls,
                variant = if (state.useTls) ButtonVariant.Primary else ButtonVariant.Secondary,
            ) { Text(if (state.useTls) "TLS" else "Plaintext") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            KNetButton(onClick = onImportDescriptor, variant = ButtonVariant.Secondary) {
                Text(if (state.schemaSourceId == null) "Import descriptor" else "Replace descriptor")
            }
            KNetButton(
                onClick = onReflect,
                enabled = state.hasValidTarget && !state.isReflecting && !state.isExecuting,
                loading = state.isReflecting,
                variant = ButtonVariant.Secondary,
            ) {
                Text("Reflect")
            }
            Spacer(Modifier.weight(1f))
            KNetButton(
                onClick = if (state.isExecuting) onCancel else onExecute,
                enabled = state.isExecuting || (state.canInvoke && !state.isReflecting),
                loading = state.isExecuting,
                clickableWhileLoading = true,
            ) { Text(if (state.isExecuting) "Cancel" else "Invoke") }
        }
    }
}

@Composable
private fun GrpcRequestPane(
    state: GrpcStudioState,
    onOperationSelected: (ApiStudioProtocolOperation) -> Unit,
    onDeadlineChanged: (String) -> Unit,
    onMessageSelected: (Int) -> Unit,
    onMessageChanged: (String) -> Unit,
    onAddMessage: () -> Unit,
    onRemoveMessage: () -> Unit,
    onSendMessage: () -> Unit,
    onHalfClose: () -> Unit,
    onAddMetadata: () -> Unit,
    onUpdateMetadata: (Int, String?, String?) -> Unit,
    onRemoveMetadata: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    Column(modifier = modifier.padding(KNetTheme.spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            KNetDropdown(
                selectedItem = state.selectedOperation,
                items = listOf<ApiStudioProtocolOperation?>(null) + state.operations,
                onItemSelected = { operation -> operation?.let(onOperationSelected) },
                modifier = Modifier.weight(1f),
                placeholder = if (state.operations.isEmpty()) "Import a descriptor set" else "Choose RPC method",
                defaultItem = null,
                itemText = { operation -> operation?.displayName ?: "Choose RPC method" },
            )
            KNetTextField(
                value = state.deadlineMillis,
                onValueChange = onDeadlineChanged,
                modifier = Modifier.width(116.dp),
                config = InputFieldConfig(placeholder = "Deadline ms"),
            )
        }
        state.selectedOperation?.let { operation ->
            Text(
                text = "${operation.shape}  •  ${operation.requestType} → ${operation.responseType}",
                modifier = Modifier.padding(top = KNetTheme.spacing.xs),
                style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Metadata",
                style = KNetTheme.typography.labelMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.weight(1f))
            KNetButton(onClick = onAddMetadata, variant = ButtonVariant.Ghost, size = ButtonSize.Compact) {
                Text("+ Add")
            }
        }
        if (state.metadata.isEmpty()) {
            Text(
                "No custom metadata. Standard gRPC headers are generated automatically.",
                style = KNetTheme.typography.caption.copy(color = colors.textMuted),
            )
        } else {
            state.metadata.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                ) {
                    KNetTextField(
                        value = entry.name,
                        onValueChange = { onUpdateMetadata(index, it, null) },
                        modifier = Modifier.weight(0.4f),
                        config = InputFieldConfig(placeholder = "name"),
                    )
                    KNetTextField(
                        value = entry.value,
                        onValueChange = { onUpdateMetadata(index, null, it) },
                        modifier = Modifier.weight(0.6f),
                        config = InputFieldConfig(placeholder = "value"),
                    )
                    KNetButton(
                        onClick = { onRemoveMetadata(index) },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Compact,
                    ) { Text("Remove") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
        ) {
            Text(
                "Messages",
                style = KNetTheme.typography.labelMedium.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            state.outboundMessages.forEachIndexed { index, _ ->
                KNetButton(
                    onClick = { onMessageSelected(index) },
                    variant = if (index == state.selectedOutboundIndex) ButtonVariant.Primary else ButtonVariant.Ghost,
                    size = ButtonSize.Compact,
                ) { Text("${index + 1}") }
            }
            Spacer(Modifier.weight(1f))
            KNetButton(onClick = onAddMessage, variant = ButtonVariant.Ghost, size = ButtonSize.Compact) {
                Text("+ Message")
            }
            KNetButton(
                onClick = onRemoveMessage,
                enabled = state.outboundMessages.size > 1,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
            ) { Text("Remove") }
            if (state.isInteractiveSession) {
                KNetButton(
                    onClick = onSendMessage,
                    enabled = !state.isRequestHalfClosed && state.selectedOutboundMessage.isNotBlank(),
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact,
                ) { Text("Send selected") }
                KNetButton(
                    onClick = onHalfClose,
                    enabled = !state.isRequestHalfClosed,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact,
                ) { Text(if (state.isRequestHalfClosed) "Half-closed" else "Half-close") }
            }
        }
        KNetCodeEditor(
            code = state.selectedOutboundMessage,
            configuration = CodeEditorConfiguration(
                mode = EditorMode.Editable,
                language = CodeLanguage.JSON,
                placeholder = "Enter protobuf JSON for this outbound message",
            ),
            actions = CodeEditorActions(onTextChange = onMessageChanged),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = KNetTheme.spacing.xs),
        )
    }
}

@Composable
private fun GrpcEventPane(
    state: GrpcStudioState,
    onEventSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val selectedEvent = state.selectedEventIndex?.let(state.events::getOrNull)
    Column(modifier = modifier.padding(KNetTheme.spacing.md)) {
        Text(
            "Stream timeline",
            style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
        )
        if (state.events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Invoke a method to see outbound messages, inbound messages, status, and trailers.",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textMuted),
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(0.44f).padding(top = KNetTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
        ) {
            itemsIndexed(state.events) { index, event ->
                val selected = index == state.selectedEventIndex
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, if (selected) colors.borderFocused else colors.border, KNetTheme.shapes.small)
                        .background(
                            if (selected) colors.interaction.selectedOverlay else colors.surfaceVariant,
                            KNetTheme.shapes.small
                        )
                        .clickable { onEventSelected(index) }
                        .padding(KNetTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        event.title(),
                        style = KNetTheme.typography.labelMedium.copy(color = colors.textPrimary),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(event.detail(), style = KNetTheme.typography.caption.copy(color = colors.textSecondary))
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = KNetTheme.spacing.sm),
            color = colors.border,
        )
        KNetCodeEditor(
            code = selectedEvent.presentationText(),
            configuration = CodeEditorConfiguration(
                mode = EditorMode.ReadOnly,
                language = if (selectedEvent is ApiStudioProtocolExecutionEvent.Message &&
                    selectedEvent.message.contentType == "application/json"
                ) CodeLanguage.JSON else CodeLanguage.PLAIN,
                placeholder = "Select a message or terminal event",
            ),
            modifier = Modifier.fillMaxWidth().weight(0.56f),
        )
    }
}

private fun ApiStudioProtocolExecutionEvent.title(): String = when (this) {
    is ApiStudioProtocolExecutionEvent.Started -> "Started"
    is ApiStudioProtocolExecutionEvent.Message -> when (message.direction) {
        ApiStudioProtocolMessageDirection.OUTBOUND -> "↑ Client message ${message.sequence}"
        ApiStudioProtocolMessageDirection.INBOUND -> "↓ Server message ${message.sequence}"
    }

    is ApiStudioProtocolExecutionEvent.Completed -> "Completed"
    is ApiStudioProtocolExecutionEvent.Failed -> "Failed"
}

private fun ApiStudioProtocolExecutionEvent.detail(): String = when (this) {
    is ApiStudioProtocolExecutionEvent.Started -> summary
    is ApiStudioProtocolExecutionEvent.Message -> "${message.copyPayload().size} B"
    is ApiStudioProtocolExecutionEvent.Completed -> "$statusCode • $actualProtocol"
    is ApiStudioProtocolExecutionEvent.Failed -> actualProtocol?.let { "$code • $it" } ?: code
}

private fun ApiStudioProtocolExecutionEvent?.presentationText(): String = when (this) {
    null -> ""
    is ApiStudioProtocolExecutionEvent.Started -> summary
    is ApiStudioProtocolExecutionEvent.Message -> message.displayText
    is ApiStudioProtocolExecutionEvent.Completed -> buildString {
        append("Status: ").append(statusCode)
        statusMessage?.let { append("\nMessage: ").append(it) }
        append("\nProtocol: ").append(actualProtocol)
        if (trailers.isNotEmpty()) {
            append("\n\nTrailers\n")
            trailers.forEach { (name, value) -> append(name).append(": ").append(value).append('\n') }
        }
    }

    is ApiStudioProtocolExecutionEvent.Failed -> buildString {
        append("Status: ").append(code)
        append("\nMessage: ").append(message)
        actualProtocol?.let { append("\nProtocol: ").append(it) }
        append("\nRetryable: ").append(if (retryable) "yes" else "no")
        if (trailers.isNotEmpty()) {
            append("\n\nTrailers\n")
            trailers.forEach { (name, value) -> append(name).append(": ").append(value).append('\n') }
        }
    }
}

private const val DEFAULT_AUTHORING_RATIO = 0.5f
