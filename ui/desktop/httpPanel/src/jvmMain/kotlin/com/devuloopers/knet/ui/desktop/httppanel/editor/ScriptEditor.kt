package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.httppanel.model.ScriptSnippetRegistry
import com.devuloopers.knet.ui.desktop.httppanel.model.ScriptState
import com.devuloopers.knet.ui.desktop.httppanel.model.editorLabel

/**
 * Modern, interactive Script Editor supporting Pre-request and Test scripts with JS/Kotlin dual language engines.
 */
@Composable
fun ScriptEditor(
    state: ScriptState,
    onStateChange: (ScriptState) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val currentScript = if (state.activePhase == ScriptPhase.PRE_REQUEST) state.preRequestScript else state.testScript
    val onCurrentScriptChange: (String) -> Unit = { newScript ->
        if (state.activePhase == ScriptPhase.PRE_REQUEST) {
            onStateChange(state.copy(preRequestScript = newScript))
        } else {
            onStateChange(state.copy(testScript = newScript))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md)
    ) {
        // Phase & Engine Selection Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Phase Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(ScriptPhase.PRE_REQUEST, ScriptPhase.POST_RESPONSE).forEach { phase ->
                    val isSelected = state.activePhase == phase
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
                            .clickable { onStateChange(state.copy(activePhase = phase)) }
                            .handCursor()
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = phase.editorLabel,
                            style = typography.caption.copy(
                                color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Language Selector Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Engine:",
                    style = typography.caption.copy(color = themeColors.textMuted)
                )
                listOf(
                    Pair("JavaScript", ScriptLanguage.JAVASCRIPT),
                    Pair("Kotlin", ScriptLanguage.KOTLIN)
                ).forEach { (label, lang) ->
                    val isSelected = state.scriptLanguage == lang
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) themeColors.accent else themeColors.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) themeColors.accent else themeColors.border,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onStateChange(state.copy(scriptLanguage = lang)) }
                            .handCursor()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = typography.caption.copy(
                                color = if (isSelected) themeColors.surface else themeColors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }

        // Quick Snippets Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Snippets:",
                style = typography.caption.copy(color = themeColors.textMuted)
            )
            ScriptSnippetRegistry.DEFAULT_SNIPPETS.forEach { snippet ->
                val snippetCode =
                    if (state.scriptLanguage == ScriptLanguage.JAVASCRIPT) snippet.codeJs else snippet.codeKotlin
                Box(
                    modifier = Modifier
                        .background(
                            color = themeColors.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = themeColors.border,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            val newScript =
                                if (currentScript.isBlank()) snippetCode else "$currentScript\n\n$snippetCode"
                            onCurrentScriptChange(newScript)
                        }
                        .handCursor()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = snippet.title,
                        style = typography.caption.copy(
                            color = themeColors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Script Editor Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = spacing.sm)
                .border(width = 1.dp, color = themeColors.border, shape = RoundedCornerShape(4.dp))
        ) {
            val codeLang =
                if (state.scriptLanguage == ScriptLanguage.JAVASCRIPT) CodeLanguage.JAVASCRIPT else CodeLanguage.PLAIN
            KNetCodeEditor(
                code = currentScript,
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = codeLang,
                    placeholder = "// Write request script..."
                ),
                actions = CodeEditorActions(onTextChange = onCurrentScriptChange),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
