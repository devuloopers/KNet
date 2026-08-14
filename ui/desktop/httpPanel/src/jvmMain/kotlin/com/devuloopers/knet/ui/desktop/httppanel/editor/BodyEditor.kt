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
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.RawSubFormat

/**
 * Multi-mode Body Payload Editor supporting none, JSON, form-data, x-www-form-urlencoded, raw, and GraphQL.
 *
 * Switches dynamically between [KNetCodeEditor] for text-based modes and [KNetKeyValueEditor]
 * for form-based modes. The raw mode exposes a [KNetDropdown] sub-format selector that controls
 * the syntax highlighting language passed to [KNetCodeEditor].
 *
 * @param state Immutable [BodyState] representing the current body payload configuration.
 * @param onStateChange Callback invoked with an updated [BodyState] whenever any configuration changes.
 * @param modifier Composable modifier applied to the root [Column] layout.
 */
@Composable
public fun BodyEditor(
    state: BodyState,
    onStateChange: (BodyState) -> Unit,
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
        // Mode Selector Pills Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyMode.entries.forEach { mode ->
                val isSelected = state.mode == mode
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) themeColors.accent.copy(alpha = 0.15f) else themeColors.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) themeColors.accent else themeColors.border,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onStateChange(state.copy(mode = mode)) }
                        .handCursor()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = typography.caption.copy(
                            color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Raw Sub-Format Selector (visible only in RAW mode)
        if (state.mode == BodyMode.RAW) {
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
            BodyMode.NONE -> {
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

            BodyMode.FORM_DATA -> {
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

            BodyMode.X_WWW_FORM_URLENCODED -> {
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

            BodyMode.JSON -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = {
                            val formatted = JsonBodyFormatter().prettyPrintJson(state.payloadText)
                            onStateChange(state.copy(payloadText = formatted))
                        },
                        placeholder = "// Enter JSON payload...\n{\n  \"key\": \"value\"\n}"
                    ),
                    language = CodeLanguage.JSON,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            BodyMode.RAW -> {
                val codeLang = when (state.rawSubFormat) {
                    RawSubFormat.TEXT -> CodeLanguage.PLAIN
                    RawSubFormat.JSON -> CodeLanguage.JSON
                    RawSubFormat.HTML -> CodeLanguage.HTML
                    RawSubFormat.XML -> CodeLanguage.XML
                    RawSubFormat.JAVASCRIPT -> CodeLanguage.JAVASCRIPT
                }
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = {
                            if (state.rawSubFormat == RawSubFormat.JSON) {
                                val formatted = JsonBodyFormatter().prettyPrintJson(state.payloadText)
                                onStateChange(state.copy(payloadText = formatted))
                            }
                        },
                        placeholder = "// Enter raw payload..."
                    ),
                    language = codeLang,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            BodyMode.GRAPHQL -> {
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
