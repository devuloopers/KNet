package com.devuloopers.knet.ui.desktop.codeeditor.api

import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommandId

/**
 * Declarative presentation state for one consumer-contributed editor header action.
 *
 * The declaration intentionally contains no callback. Consumers route interaction through
 * [CodeEditorActions.onCommand], keeping editor configuration immutable and callback-free. Reusing
 * [EditorCommandId.Custom] also lets a feature expose the same command through future shortcuts or a command
 * palette without defining another identity system.
 *
 * @property commandId Stable, namespaced command identity used for rendering and dispatch.
 * @property label User-facing action label supplied by the contributing feature.
 * @property enabled Whether the action can currently be invoked. Disable an existing declaration instead of
 * removing it while asynchronous capability analysis is running so the toolbar layout remains stable.
 */
data class CodeEditorHeaderAction(
    val commandId: EditorCommandId.Custom,
    val label: String,
    val enabled: Boolean = true
)
