package com.devuloopers.knet.ui.desktop.scripting.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.scripting.console.ConsoleActions
import com.devuloopers.knet.ui.desktop.scripting.console.ConsoleFilter
import com.devuloopers.knet.ui.desktop.scripting.console.ConsoleToolbar
import com.devuloopers.knet.ui.desktop.scripting.console.ConsoleView
import com.devuloopers.knet.ui.desktop.scripting.diagnostics.DiagnosticsView
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptingIntent
import com.devuloopers.knet.ui.desktop.scripting.viewmodel.ScriptingViewModel
import com.devuloopers.knet.ui.desktop.scripting.workspace.ContextExplorer
import com.devuloopers.knet.ui.desktop.scripting.workspace.ScriptEditor
import com.devuloopers.knet.ui.desktop.scripting.workspace.ScriptExplorer
import com.devuloopers.knet.ui.desktop.scripting.workspace.ScriptTabs
import com.devuloopers.knet.ui.desktop.scripting.workspace.VariablesExplorer

/**
 * ScriptingScreen renders KNet's primary Automation Development Environment.
 */
@Composable
fun ScriptingScreen(
    viewModel: ScriptingViewModel,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        // Left Column: script lists
        ScriptExplorer(
            scripts = uiState.openScripts,
            onScriptSelect = { viewModel.processIntent(ScriptingIntent.LoadScript(it)) },
            modifier = Modifier.fillMaxHeight()
        )

        // Middle Editor area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ScriptTabs(
                openScripts = uiState.openScripts,
                activeScript = uiState.activeScript,
                onScriptSelect = { viewModel.processIntent(ScriptingIntent.LoadScript(it)) }
            )

            val activeScript = uiState.activeScript
            if (activeScript != null) {
                ScriptEditor(
                    code = activeScript.code,
                    onCodeChange = { viewModel.processIntent(ScriptingIntent.UpdateCode(it)) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "No script open. Select one from explorer.",
                    style = typography.caption.copy(color = themeColors.textSecondary),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Lower horizontal panel for console output
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors.surfaceVariant)
                ) {
                    ConsoleToolbar(
                        autoScroll = uiState.autoScroll,
                        onToggleAutoScroll = { viewModel.processIntent(ScriptingIntent.ToggleAutoScroll) },
                        onClear = { viewModel.processIntent(ScriptingIntent.ClearConsole) },
                        modifier = Modifier.weight(1f)
                    )
                    ConsoleFilter(
                        selectedFilter = uiState.consoleFilter,
                        onFilterSelected = { viewModel.processIntent(ScriptingIntent.SetConsoleFilter(it)) }
                    )
                    ConsoleActions(logs = uiState.logs)
                }
                ConsoleView(
                    logs = uiState.logs,
                    filter = uiState.consoleFilter,
                    autoScroll = uiState.autoScroll,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Right side: variables, environment, and diagnostics overview
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
        ) {
            VariablesExplorer(variables = uiState.context.variables)
            ContextExplorer(contextProperties = uiState.context.requests)
            DiagnosticsView(
                diagnostics = uiState.diagnostics,
                onDiagnosticSelect = {}
            )
        }
    }
}

