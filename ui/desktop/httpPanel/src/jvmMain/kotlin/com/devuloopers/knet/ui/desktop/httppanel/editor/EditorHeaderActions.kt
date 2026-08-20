package com.devuloopers.knet.ui.desktop.httppanel.editor

import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorHeaderAction
import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommand
import com.devuloopers.knet.ui.desktop.codeeditor.command.EditorCommandId

/** Stable identity for the HTTP-panel formatting contribution. */
internal val prettifyEditorCommandId = EditorCommandId.Custom("http-panel.prettify")

/**
 * Declarative HTTP-panel formatting contribution shared by request, response, and GraphQL editors.
 *
 * The code-editor module only renders and dispatches this declaration; formatting ownership remains in the
 * active HTTP body format.
 */
internal val prettifyEditorHeaderActions = listOf(
    CodeEditorHeaderAction(
        commandId = prettifyEditorCommandId,
        label = "Prettify"
    )
)

/**
 * Routes a generic editor-header action to the HTTP panel's formatting command.
 *
 * @param command Custom command emitted by the editor toolbar.
 * @param executePrettify Format-specific command owned by the active HTTP body mode.
 */
internal fun dispatchPrettifyEditorHeaderAction(
    command: EditorCommand.Custom,
    executePrettify: () -> Unit
) {
    if (command.id == prettifyEditorCommandId) executePrettify()
}
