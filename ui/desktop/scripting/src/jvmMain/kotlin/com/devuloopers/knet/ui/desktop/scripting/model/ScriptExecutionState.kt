package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Script execution lifecycle states.
 */
enum class ScriptExecutionState {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
