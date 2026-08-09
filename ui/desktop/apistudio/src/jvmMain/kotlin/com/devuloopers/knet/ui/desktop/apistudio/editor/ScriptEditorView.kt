package com.devuloopers.knet.ui.desktop.apistudio.editor

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptPhase
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptSnippetRegistry
import com.devuloopers.knet.ui.desktop.apistudio.model.ScriptState
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor

/**
 * Modern, interactive Script Editor View supporting Pre-request and Test scripts with JS/Kotlin dual language engines.
 */
@Composable
public fun ScriptEditorView(
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
    val phaseAccent = if (state.activePhase == ScriptPhase.PRE_REQUEST) Color(0xFFA855F7) else Color(0xFFF59E0B)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Toolbar Row: Phase Selector + Language Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phase Toggle Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScriptPhase.entries.forEach { phase ->
                    val isSelected = phase == state.activePhase
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
                            text = phase.label,
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
                val snippetCode = if (state.scriptLanguage == ScriptLanguage.JAVASCRIPT) snippet.codeJs else snippet.codeKotlin
                Box(
                    modifier = Modifier
                        .background(themeColors.surfaceVariant, RoundedCornerShape(4.dp))
                        .border(1.dp, themeColors.border, RoundedCornerShape(4.dp))
                        .clickable {
                            val newScript = if (currentScript.isBlank()) snippetCode else "$currentScript\n\n$snippetCode"
                            onCurrentScriptChange(newScript)
                        }
                        .handCursor()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ ${snippet.title}",
                        style = typography.caption.copy(
                            color = themeColors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // Informational Caption Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = KNetIcons.Info,
                contentDescription = "Info",
                modifier = Modifier.size(14.dp),
                tint = phaseAccent
            )
            Text(
                text = if (state.activePhase == ScriptPhase.PRE_REQUEST) {
                    "Pre-request scripts run before sending the HTTP request. Use pm.environment or request mutation APIs."
                } else {
                    "Test scripts run after receiving the response. Assert status codes, response headers, or JSON body data."
                },
                style = typography.caption.copy(color = themeColors.textMuted)
            )
        }

        // Embedded KNetCodeEditor
        KNetCodeEditor(
            code = currentScript,
            mode = EditorMode.Editable(
                onCodeChange = onCurrentScriptChange,
                placeholder = if (state.activePhase == ScriptPhase.PRE_REQUEST) {
                    "// Enter pre-request script (e.g. pm.environment.set(\"timestamp\", Date.now()))..."
                } else {
                    "// Enter post-response test assertions (e.g. pm.test(\"Status code is 200\", ...))..."
                }
            ),
            languageHint = if (state.scriptLanguage == ScriptLanguage.JAVASCRIPT) "javascript" else "plain",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}
