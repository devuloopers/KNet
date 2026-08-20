package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorHeaderConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState

/**
 * Dedicated, interactive HTTP response body editor composable.
 *
 * Tailored exclusively for server response payloads:
 * - Excludes client-only upload encodings (form-data, x-www-form-urlencoded, GraphQL query syntax).
 * - Provides tailored response mode tabs: [ResponseBodyMode.NONE], [ResponseBodyMode.JSON],
 *   [ResponseBodyMode.XML], [ResponseBodyMode.HTML], [ResponseBodyMode.TEXT], and [ResponseBodyMode.RAW].
 * - Automatically configures syntax highlighting and dedicated code prettifiers via [ResponseBodyMode] SSOT.
 * - Maintains a single continuous [KNetCodeEditor] call site across text modes to prevent layout flashing and preserve editor caret/scroll state.
 *
 * @param state Immutable [ResponseBodyState] holding the response payload and active response mode.
 * @param onStateChange Callback invoked when the response body state or mode changes.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ResponseBodyEditor(
    state: ResponseBodyState,
    onStateChange: (ResponseBodyState) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Mode Selector Tab Row using reusable design system components
        KNetTabRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            ResponseBodyMode.entries.forEach { mode ->
                KNetTab(
                    title = mode.label,
                    selected = state.mode == mode,
                    onClick = { onStateChange(state.copy(mode = mode)) }
                )
            }
        }

        // Dynamic Response Body Panel Content
        if (state.mode == ResponseBodyMode.NONE) {
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
                        text = "This response does not have a body payload.",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted)
                    )
                }
            }
        } else {
            val prettifyAction: (() -> Unit)? = if (state.mode.isPrettifiable) {
                {
                    val formatted = state.mode.prettify(state.payloadText)
                    onStateChange(state.copy(payloadText = formatted))
                }
            } else {
                null
            }

            KNetCodeEditor(
                code = state.payloadText,
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = state.mode.codeLanguage,
                    header = CodeEditorHeaderConfiguration(
                        actions = if (state.mode.isPrettifiable) prettifyEditorHeaderActions else emptyList()
                    ),
                    placeholder = state.mode.placeholder
                ),
                actions = CodeEditorActions(
                    onTextChange = { onStateChange(state.copy(payloadText = it)) },
                    onCommand = if (prettifyAction == null) null else { command ->
                        dispatchPrettifyEditorHeaderAction(command, prettifyAction)
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
