package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState

/**
 * Multi-mode Request Body Payload Editor supporting none, JSON, form-data, x-www-form-urlencoded, raw, and GraphQL.
 *
 * Switches dynamically between [KNetCodeEditor] for text-based modes and [KNetKeyValueEditor]
 * for form-based modes. The raw mode exposes a [KNetDropdown] sub-format selector that controls
 * the syntax highlighting language passed to [KNetCodeEditor].
 *
 * @param state Immutable [RequestBodyState] representing the current request body payload configuration.
 * @param onStateChange Callback invoked with an updated [RequestBodyState] whenever any configuration changes.
 * @param onGraphQlStateChange Optional callback for structured GraphQL updates.
 * @param modifier Composable modifier applied to the root [Column] layout.
 */
@Composable
public fun RequestBodyEditor(
    state: RequestBodyState,
    onStateChange: (RequestBodyState) -> Unit,
    onGraphQlStateChange: ((GraphQlState) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Mode Selector Tab Row using reusable design system components
        ScrollableTabRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            RequestBodyMode.entries.forEach { mode ->
                KNetTab(
                    title = mode.label,
                    selected = state.mode == mode,
                    onClick = { onStateChange(state.copy(mode = mode)) }
                )
            }
        }

        // Raw Sub-Format Selector (visible only in RAW mode)
        if (state.mode == RequestBodyMode.RAW) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Format:",
                    style = typography.caption.copy(
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
                KNetDropdown(
                    selectedItem = state.rawSubFormat.label,
                    items = RawSubFormat.entries.map { it.label },
                    onItemSelected = { selectedLabel ->
                        val format = RawSubFormat.entries.find { it.label == selectedLabel } ?: RawSubFormat.TEXT
                        onStateChange(state.copy(rawSubFormat = format))
                    }
                )
            }
        }

        // Dynamic Body Panel Content
        when (state.mode) {
            RequestBodyMode.NONE -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(themeColors.surfaceVariant, RoundedCornerShape(6.dp))
                        .border(1.dp, themeColors.border, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Icon(
                            imageVector = KNetIcons.Info,
                            contentDescription = "No Body",
                            modifier = Modifier.size(20.dp),
                            tint = themeColors.textMuted
                        )
                        Text(
                            text = "This request does not have a body payload.",
                            style = typography.bodyMedium.copy(color = themeColors.textMuted)
                        )
                    }
                }
            }

            RequestBodyMode.FORM_DATA -> {
                KNetKeyValueEditor(
                    entries = state.formDataEntries,
                    onEntryChange = { index, updatedEntry ->
                        val updatedList = state.formDataEntries.toMutableList().apply { set(index, updatedEntry) }
                        onStateChange(state.copy(formDataEntries = updatedList))
                    },
                    onAddEntry = {
                        onStateChange(
                            state.copy(
                                formDataEntries = state.formDataEntries + KeyValueEntry("fd_${System.currentTimeMillis()}", "", "")
                            )
                        )
                    },
                    onRemoveEntry = { index ->
                        val updated = state.formDataEntries.toMutableList().apply { removeAt(index) }
                        onStateChange(state.copy(formDataEntries = updated))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            RequestBodyMode.X_WWW_FORM_URLENCODED -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = KNetIcons.Info,
                            contentDescription = "Info",
                            modifier = Modifier.size(14.dp),
                            tint = themeColors.textMuted
                        )
                        Text(
                            text = "Parameters are URL-encoded and sent as 'application/x-www-form-urlencoded'.",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    }
                    KNetKeyValueEditor(
                        entries = state.urlEncodedEntries,
                        onEntryChange = { index, updated ->
                            val newEntries = state.urlEncodedEntries.toMutableList().apply { set(index, updated) }
                            onStateChange(state.copy(urlEncodedEntries = newEntries))
                        },
                        onAddEntry = {
                            onStateChange(
                                state.copy(
                                    urlEncodedEntries = state.urlEncodedEntries + KeyValueEntry("ue_${System.currentTimeMillis()}", "", "")
                                )
                            )
                        },
                        onRemoveEntry = { index ->
                            val newEntries = state.urlEncodedEntries.toMutableList().apply { removeAt(index) }
                            onStateChange(state.copy(urlEncodedEntries = newEntries))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            RequestBodyMode.JSON,
            RequestBodyMode.RAW -> {
                val isJson = state.mode == RequestBodyMode.JSON
                val codeLang = if (isJson) CodeLanguage.JSON else state.rawSubFormat.codeLanguage
                val placeholder = if (isJson) {
                    "// Enter JSON payload...\n{\n  \"key\": \"value\"\n}"
                } else {
                    "// Enter raw payload..."
                }
                val isPrettifiable = if (isJson) true else state.rawSubFormat.isPrettifiable
                val prettifyAction: (() -> Unit)? = if (isPrettifiable) {
                    {
                        val formatted = if (isJson) {
                            JsonBodyFormatter().prettyPrintJson(state.payloadText)
                        } else {
                            state.rawSubFormat.prettify(state.payloadText)
                        }
                        onStateChange(state.copy(payloadText = formatted))
                    }
                } else {
                    null
                }

                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = prettifyAction,
                        placeholder = placeholder
                    ),
                    language = codeLang,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            RequestBodyMode.GRAPHQL -> {
                val graphQlMapper = remember { GraphQlPayloadMapper() }
                GraphQlEditor(
                    state = state.graphQlState,
                    onStateChange = { updatedGraphQlState ->
                        if (onGraphQlStateChange != null) {
                            onGraphQlStateChange(updatedGraphQlState)
                        } else {
                            val newPayload = graphQlMapper.serializePayload(updatedGraphQlState)
                            onStateChange(
                                state.copy(
                                    graphQlState = updatedGraphQlState,
                                    payloadText = newPayload
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}
