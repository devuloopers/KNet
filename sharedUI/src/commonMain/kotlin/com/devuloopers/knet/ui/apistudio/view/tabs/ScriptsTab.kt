package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.sandbox.ScriptSanitizer
import com.devuloopers.knet.scriptengine.snippets.SnippetRegistry
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState
import com.devuloopers.knet.ui.apistudio.view.CodeEditorWidget
import com.devuloopers.knet.ui.apistudio.view.preRequestScript
import com.devuloopers.knet.ui.apistudio.view.scriptLanguage
import com.devuloopers.knet.ui.apistudio.view.testScript

/**
 * Pre-request Script tab content for the Request Builder panel.
 *
 * Displays a language selector (JavaScript / Kotlin), snippet insertion buttons,
 * the code editor, and a live security-diagnostic banner for invalid scripts.
 *
 * @param uiState The current [ApiStudioUiState] exposing script content and language.
 * @param onScriptLanguageChange Callback when the scripting language is changed.
 * @param onPreRequestScriptChange Callback when the pre-request script text changes.
 */
@Composable
internal fun PreRequestScriptTab(
    uiState: ApiStudioUiState,
    onScriptLanguageChange: (ScriptLanguage) -> Unit,
    onPreRequestScriptChange: (String) -> Unit
) {
    ScriptEditorTab(
        label = "pre-request script",
        scriptText = uiState.preRequestScript,
        scriptLanguage = uiState.scriptLanguage,
        placeholder = "// Enter pre-request script (e.g. env[\"timestamp\"] = ...)...",
        editorAccent = Color(0xFFA855F7),
        onScriptLanguageChange = onScriptLanguageChange,
        onScriptChange = onPreRequestScriptChange,
        onSnippetInsert = { code ->
            val newCode = if (uiState.preRequestScript.isBlank()) code else "${uiState.preRequestScript}\n\n$code"
            onPreRequestScriptChange(newCode)
        }
    )
}

/**
 * Tests Script tab content for the Request Builder panel.
 *
 * Displays a language selector (JavaScript / Kotlin), snippet insertion buttons,
 * the code editor, and a live security-diagnostic banner for invalid scripts.
 *
 * @param uiState The current [ApiStudioUiState] exposing script content and language.
 * @param onScriptLanguageChange Callback when the scripting language is changed.
 * @param onTestScriptChange Callback when the test script text changes.
 */
@Composable
internal fun TestScriptTab(
    uiState: ApiStudioUiState,
    onScriptLanguageChange: (ScriptLanguage) -> Unit,
    onTestScriptChange: (String) -> Unit
) {
    ScriptEditorTab(
        label = "test assertions",
        scriptText = uiState.testScript,
        scriptLanguage = uiState.scriptLanguage,
        placeholder = "// Enter test script assertions...",
        editorAccent = Color(0xFFF59E0B),
        onScriptLanguageChange = onScriptLanguageChange,
        onScriptChange = onTestScriptChange,
        onSnippetInsert = { code ->
            val newCode = if (uiState.testScript.isBlank()) code else "${uiState.testScript}\n\n$code"
            onTestScriptChange(newCode)
        }
    )
}

/**
 * Shared scaffold for both pre-request and test script editors.
 */
@Composable
private fun ScriptEditorTab(
    label: String,
    scriptText: String,
    scriptLanguage: ScriptLanguage,
    placeholder: String,
    editorAccent: Color,
    onScriptLanguageChange: (ScriptLanguage) -> Unit,
    onScriptChange: (String) -> Unit,
    onSnippetInsert: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language selector pills
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Language: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
                listOf("JavaScript" to ScriptLanguage.JAVASCRIPT, "Kotlin" to ScriptLanguage.KOTLIN).forEach { (langLabel, langEnum) ->
                    val isSelected = scriptLanguage == langEnum
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(3.dp))
                            .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(3.dp))
                            .clickable { onScriptLanguageChange(langEnum) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(langLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // Snippet insertion buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SnippetRegistry.SNIPPETS.forEach { snip ->
                    val snippetCode = remember(snip, scriptLanguage) { SnippetRegistry.getCode(snip, scriptLanguage) }
                    Box(
                        modifier = Modifier
                            .background(KNetColors.FieldDark, RoundedCornerShape(3.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(3.dp))
                            .clickable { onSnippetInsert(snippetCode) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("+ ${snip.title}", color = KNetColors.ActiveBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        CodeEditorWidget(code = scriptText, onCodeChange = onScriptChange, placeholder = placeholder, textColor = editorAccent, modifier = Modifier.weight(1f).fillMaxWidth())

        // Live security diagnostic banner
        val sanitization = remember(scriptText) { ScriptSanitizer.validate(scriptText) }
        if (!sanitization.isValid && sanitization.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔴 Line ${sanitization.line ?: 1}: ${sanitization.errorMessage}",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
