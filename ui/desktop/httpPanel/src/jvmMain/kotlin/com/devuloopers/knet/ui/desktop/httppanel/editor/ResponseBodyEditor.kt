package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.formatter.formatters.HtmlBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.XmlBodyFormatter
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState

/**
 * Dedicated, interactive HTTP response body editor composable.
 *
 * Tailored exclusively for server response payloads:
 * - Excludes client-only upload encodings (form-data, x-www-form-urlencoded, GraphQL query syntax).
 * - Provides tailored response mode tabs: [ResponseBodyMode.NONE], [ResponseBodyMode.JSON],
 *   [ResponseBodyMode.XML], [ResponseBodyMode.HTML], [ResponseBodyMode.TEXT], and [ResponseBodyMode.RAW].
 * - Automatically configures syntax highlighting and dedicated code prettifiers.
 *
 * @param state Immutable [ResponseBodyState] holding the response payload and active response mode.
 * @param onStateChange Callback invoked when the response body state or mode changes.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun ResponseBodyEditor(
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
        ScrollableTabRow(
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
        when (state.mode) {
            ResponseBodyMode.NONE -> {
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
            }

            ResponseBodyMode.JSON -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = {
                            val formatted = JsonBodyFormatter().prettyPrintJson(state.payloadText)
                            onStateChange(state.copy(payloadText = formatted))
                        },
                        placeholder = "// Enter JSON response body...\n{\n  \"status\": \"success\"\n}"
                    ),
                    language = CodeLanguage.JSON,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            ResponseBodyMode.XML -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = {
                            val formatted = XmlBodyFormatter().prettyPrint(state.payloadText)
                            onStateChange(state.copy(payloadText = formatted))
                        },
                        placeholder = "<!-- Enter XML response body -->\n<response>\n  <status>success</status>\n</response>"
                    ),
                    language = CodeLanguage.XML,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            ResponseBodyMode.HTML -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        onPrettify = {
                            val formatted = HtmlBodyFormatter.prettyPrintHtml(state.payloadText)
                            onStateChange(state.copy(payloadText = formatted))
                        },
                        placeholder = "<!-- Enter HTML response body -->\n<!DOCTYPE html>\n<html>\n<body>\n  <h1>200 OK</h1>\n</body>\n</html>"
                    ),
                    language = CodeLanguage.HTML,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            ResponseBodyMode.TEXT -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        placeholder = "// Enter plain text response body..."
                    ),
                    language = CodeLanguage.PLAIN,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            ResponseBodyMode.RAW -> {
                KNetCodeEditor(
                    code = state.payloadText,
                    mode = EditorMode.Editable(
                        onCodeChange = { onStateChange(state.copy(payloadText = it)) },
                        placeholder = "// Enter raw response payload..."
                    ),
                    language = CodeLanguage.PLAIN,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}
