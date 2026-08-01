package com.devuloopers.knet.ui.desktop.scripting.viewmodel

import androidx.lifecycle.ViewModel
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptExecutionState
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptingIntent
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptingState

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing script console UDF state, loading, saving, and executing.
 */
class ScriptingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScriptingState())
    val uiState: StateFlow<ScriptingState> = _uiState.asStateFlow()

    fun processIntent(intent: ScriptingIntent) {
        when (intent) {
            is ScriptingIntent.LoadScript -> {
                _uiState.update {
                    it.copy(
                        activeScript = intent.script,
                        openScripts = if (it.openScripts.any { open -> open.id == intent.script.id }) it.openScripts else it.openScripts + intent.script
                    )
                }
            }

            is ScriptingIntent.UpdateCode -> {
                _uiState.update {
                    it.copy(
                        activeScript = it.activeScript?.copy(code = intent.code, isDirty = true)
                    )
                }
            }

            ScriptingIntent.SaveScript -> {
                _uiState.update {
                    it.copy(activeScript = it.activeScript?.copy(isDirty = false))
                }
            }

            ScriptingIntent.ExecuteScript -> {
                _uiState.update { it.copy(executionState = ScriptExecutionState.RUNNING) }
            }

            is ScriptingIntent.AddSnippet -> {
                _uiState.update {
                    val currentCode = it.activeScript?.code ?: ""
                    it.copy(
                        activeScript = it.activeScript?.copy(
                            code = if (currentCode.isEmpty()) intent.snippet else "$currentCode\n${intent.snippet}",
                            isDirty = true
                        )
                    )
                }
            }

            ScriptingIntent.ClearConsole -> {
                _uiState.update { it.copy(logs = emptyList()) }
            }

            is ScriptingIntent.SetConsoleFilter -> {
                _uiState.update { it.copy(consoleFilter = intent.level) }
            }

            ScriptingIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }
        }
    }
}
