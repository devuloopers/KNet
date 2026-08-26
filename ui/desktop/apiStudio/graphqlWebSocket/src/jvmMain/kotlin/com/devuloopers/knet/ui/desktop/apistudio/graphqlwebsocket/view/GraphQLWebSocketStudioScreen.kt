package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.view

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
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioAuthoringEditorLayout
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioSplitWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketAuthoringTab
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.viewmodel.GraphQLWebSocketStudioViewModel
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/** Renders modern GraphQL subscription authoring and its bounded streamed event timeline. */
@Composable
fun GraphQLWebSocketStudioScreen(
    viewModel: GraphQLWebSocketStudioViewModel,
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
                TargetBar(
                    state = state,
                    onUrlChanged = viewModel::updateUrl,
                    onConnect = viewModel::connect,
                    onStop = viewModel::stopSession,
                    onCancel = viewModel::cancelSession,
                )
                HorizontalDivider(color = colors.border)
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth()
                            .background(colors.semantic.errorContainer)
                            .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.sm),
                        style = KNetTheme.typography.bodySmall.copy(color = colors.semantic.error),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AuthoringPane(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        },
        resultPane = { paneModifier ->
            EventPane(
                state = state,
                onEventSelected = viewModel::selectEvent,
                modifier = paneModifier.fillMaxSize(),
            )
        },
        modifier = modifier.fillMaxSize().background(colors.surface),
    )
}

@Composable
private fun TargetBar(
    state: GraphQLWebSocketStudioState,
    onUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
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
            config = InputFieldConfig(placeholder = "wss:// GraphQL subscription endpoint"),
        )
        when {
            state.isConnecting -> KNetButton(
                onClick = onCancel,
                loading = true,
                clickableWhileLoading = true,
            ) { Text("Cancel") }
            state.isConnected -> KNetButton(onClick = onStop, variant = ButtonVariant.Secondary) {
                Text("Stop")
            }
            else -> KNetButton(onClick = onConnect, enabled = state.canConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun AuthoringPane(
    state: GraphQLWebSocketStudioState,
    viewModel: GraphQLWebSocketStudioViewModel,
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
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                    ) {
                        KNetTextField(
                            value = state.operationId,
                            onValueChange = viewModel::updateOperationId,
                            modifier = Modifier.weight(0.4f),
                            config = InputFieldConfig(placeholder = "Operation ID (generated automatically)"),
                        )
                        KNetTextField(
                            value = state.operationName,
                            onValueChange = viewModel::updateOperationName,
                            modifier = Modifier.weight(0.6f),
                            config = InputFieldConfig(placeholder = "Operation name (optional)"),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                    ) {
                        KNetTextField(
                            value = state.connectTimeoutMillis,
                            onValueChange = viewModel::updateConnectTimeout,
                            modifier = Modifier.weight(1f),
                            config = InputFieldConfig(placeholder = "Connection timeout ms (default 30000)"),
                        )
                        KNetTextField(
                            value = state.acknowledgementTimeoutMillis,
                            onValueChange = viewModel::updateAcknowledgementTimeout,
                            modifier = Modifier.weight(1f),
                            config = InputFieldConfig(placeholder = "Acknowledgement timeout ms (default 10000)"),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Handshake headers",
                            style = KNetTheme.typography.labelMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        KNetButton(
                            onClick = viewModel::addHeader,
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Compact,
                        ) { Text("+ Add") }
                    }
                    if (state.headers.isEmpty()) {
                        Text(
                            "Optional authentication headers. Upgrade and GraphQL subprotocol headers are generated automatically.",
                            style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                        )
                    } else {
                        state.headers.forEachIndexed { index, header ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                            ) {
                                KNetTextField(
                                    value = header.name,
                                    onValueChange = { value -> viewModel.updateHeader(index, name = value) },
                                    modifier = Modifier.weight(0.4f),
                                    config = InputFieldConfig(placeholder = "Header name"),
                                )
                                KNetTextField(
                                    value = header.value,
                                    onValueChange = { value -> viewModel.updateHeader(index, value = value) },
                                    modifier = Modifier.weight(0.6f),
                                    config = InputFieldConfig(placeholder = "Value"),
                                )
                                KNetButton(
                                    onClick = { viewModel.removeHeader(index) },
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
                modifier = Modifier.fillMaxWidth().padding(top = KNetTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
            ) {
                GraphQLWebSocketAuthoringTab.entries.forEach { tab ->
                    KNetButton(
                        onClick = { viewModel.selectAuthoringTab(tab) },
                        variant = if (tab == state.selectedAuthoringTab) {
                            ButtonVariant.Secondary
                        } else {
                            ButtonVariant.Ghost
                        },
                        size = ButtonSize.Compact,
                    ) { Text(tab.label()) }
                }
            }
        },
        editor = {
            KNetCodeEditor(
                code = state.selectedContent(),
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = state.selectedLanguage(),
                    placeholder = state.selectedPlaceholder(),
                ),
                actions = CodeEditorActions(onTextChange = { value ->
                    when (state.selectedAuthoringTab) {
                        GraphQLWebSocketAuthoringTab.QUERY -> viewModel.updateQuery(value)
                        GraphQLWebSocketAuthoringTab.VARIABLES -> viewModel.updateVariables(value)
                        GraphQLWebSocketAuthoringTab.EXTENSIONS -> viewModel.updateExtensions(value)
                        GraphQLWebSocketAuthoringTab.CONNECTION_PARAMETERS -> viewModel
                            .updateConnectionParameters(value)
                    }
                }),
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun EventPane(
    state: GraphQLWebSocketStudioState,
    onEventSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    val listState = rememberLazyListState()
    val selectedEvent = state.selectedEventIndex?.let(state.events::getOrNull)
    Column(modifier = modifier.padding(KNetTheme.spacing.md)) {
        Text("Subscription timeline", style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary))
        if (state.events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Connect to see initialization, subscription data, errors, completion, and close status.",
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
        HorizontalDivider(modifier = Modifier.padding(vertical = KNetTheme.spacing.sm), color = colors.border)
        KNetCodeEditor(
            code = selectedEvent.presentationText(),
            configuration = CodeEditorConfiguration(
                mode = EditorMode.ReadOnly,
                language = selectedEvent.presentationLanguage(),
                placeholder = "Select a subscription event",
            ),
            modifier = Modifier.fillMaxWidth().weight(0.55f),
        )
    }
}

private fun GraphQLWebSocketAuthoringTab.label(): String = when (this) {
    GraphQLWebSocketAuthoringTab.QUERY -> "Query"
    GraphQLWebSocketAuthoringTab.VARIABLES -> "Variables"
    GraphQLWebSocketAuthoringTab.EXTENSIONS -> "Extensions"
    GraphQLWebSocketAuthoringTab.CONNECTION_PARAMETERS -> "Connection params"
}

private fun GraphQLWebSocketStudioState.selectedContent(): String = when (selectedAuthoringTab) {
    GraphQLWebSocketAuthoringTab.QUERY -> query
    GraphQLWebSocketAuthoringTab.VARIABLES -> variablesJson
    GraphQLWebSocketAuthoringTab.EXTENSIONS -> extensionsJson
    GraphQLWebSocketAuthoringTab.CONNECTION_PARAMETERS -> connectionParametersJson
}

private fun GraphQLWebSocketStudioState.selectedLanguage(): CodeLanguage = when (selectedAuthoringTab) {
    GraphQLWebSocketAuthoringTab.QUERY -> CodeLanguage.GRAPHQL
    GraphQLWebSocketAuthoringTab.VARIABLES,
    GraphQLWebSocketAuthoringTab.EXTENSIONS,
    GraphQLWebSocketAuthoringTab.CONNECTION_PARAMETERS -> CodeLanguage.JSON
}

private fun GraphQLWebSocketStudioState.selectedPlaceholder(): String = when (selectedAuthoringTab) {
    GraphQLWebSocketAuthoringTab.QUERY -> "subscription OperationName { ... }"
    GraphQLWebSocketAuthoringTab.VARIABLES -> "Optional variables JSON object"
    GraphQLWebSocketAuthoringTab.EXTENSIONS -> "Optional extensions JSON object"
    GraphQLWebSocketAuthoringTab.CONNECTION_PARAMETERS -> "Optional connection_init payload JSON object"
}

private fun ApiStudioProtocolExecutionEvent.title(): String = when (this) {
    is ApiStudioProtocolExecutionEvent.Started -> "Connected"
    is ApiStudioProtocolExecutionEvent.Message -> when (message.direction) {
        ApiStudioProtocolMessageDirection.OUTBOUND -> "Client message ${message.sequence}"
        ApiStudioProtocolMessageDirection.INBOUND -> "Server message ${message.sequence}"
    }
    is ApiStudioProtocolExecutionEvent.Completed -> "Completed"
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
        append("Status: ").append(statusCode)
        statusMessage?.let { message -> append("\nMessage: ").append(message) }
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
