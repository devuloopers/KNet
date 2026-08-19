package com.devuloopers.knet.ui.desktop.codeeditor.command

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession

/**
 * Strongly typed command accepted by an [EditorCommandDispatcher].
 *
 * Commands describe user intent independently from Compose or platform key events.
 */
sealed interface EditorCommand {
    /**
     * Inserts or replaces text at the session selection.
     *
     * @property text Text to insert.
     * @property kind Edit category used for undo grouping.
     */
    data class InsertText(
        val text: String,
        val kind: EditorEditKind = EditorEditKind.Insertion
    ) : EditorCommand

    /**
     * Replaces an explicit document range.
     *
     * @property range Range to replace.
     * @property text Replacement text.
     * @property kind Edit category used for undo grouping.
     */
    data class ReplaceRange(
        val range: EditorRange,
        val text: String,
        val kind: EditorEditKind = EditorEditKind.Replacement
    ) : EditorCommand

    /**
     * Splits a logical line.
     *
     * @property position Split position.
     * @property indentation Indentation inserted on the new line.
     */
    data class SplitLine(
        val position: EditorPosition,
        val indentation: String = ""
    ) : EditorCommand

    /**
     * Merges a logical line into its predecessor.
     *
     * @property lineIndex Line whose preceding newline should be removed.
     */
    data class MergeWithPreviousLine(val lineIndex: Int) : EditorCommand

    /**
     * Moves the session caret.
     *
     * @property position Requested caret position.
     */
    data class MoveCaret(val position: EditorPosition) : EditorCommand

    /**
     * Changes the session selection.
     *
     * @property selection New directional selection, or `null` to clear it.
     */
    data class Select(val selection: EditorSelection?) : EditorCommand

    /** Selects the complete current document. */
    data object SelectAll : EditorCommand

    /** Reverts the newest undo group. */
    data object Undo : EditorCommand

    /** Reapplies the newest redo group. */
    data object Redo : EditorCommand

    /**
     * Extension command whose behavior is supplied by an external handler.
     *
     * @property id Validated stable identifier owned by the extension.
     */
    data class Custom(val id: EditorCommandId.Custom) : EditorCommand
}

/**
 * Extensible editor-command identifier.
 */
sealed interface EditorCommandId {
    /** Stable identifier value. */
    val value: String

    /**
     * Validated identifier for a command contributed outside the editor foundation.
     *
     * @property value Namespaced command identifier such as `knet.prettify`.
     */
    data class Custom(override val value: String) : EditorCommandId {
        init {
            require(value.isNotBlank()) { "Custom editor command identifier must not be blank." }
            require(value.none(Char::isWhitespace)) { "Custom editor command identifier must not contain whitespace." }
        }
    }
}

/**
 * Optional extension handler consulted for commands not handled by the built-in dispatcher.
 */
fun interface EditorCommandHandler {
    /**
     * Attempts to execute a command.
     *
     * @param command Command to inspect.
     * @param session Session that should receive any resulting state changes.
     * @return `true` when the command was handled.
     */
    fun handle(command: EditorCommand, session: EditorSession): Boolean
}

/**
 * Dispatches platform-neutral commands into an [EditorSession].
 *
 * @param extensionHandlers Ordered handlers for product- or language-specific custom commands.
 */
class EditorCommandDispatcher(
    private val extensionHandlers: List<EditorCommandHandler> = emptyList()
) {
    /**
     * Executes one command.
     *
     * @param command Command to execute.
     * @param session Target editor session.
     * @return `true` when the command was recognized and applied.
     */
    fun dispatch(command: EditorCommand, session: EditorSession): Boolean {
        return when (command) {
            is EditorCommand.InsertText -> {
                session.insert(command.text, command.kind)
                true
            }
            is EditorCommand.ReplaceRange -> {
                session.apply(EditorTextEdit(command.range, command.text, command.kind))
                true
            }
            is EditorCommand.SplitLine -> {
                session.splitLine(command.position, command.indentation)
                true
            }
            is EditorCommand.MergeWithPreviousLine -> session.mergeWithPreviousLine(command.lineIndex) != null
            is EditorCommand.MoveCaret -> {
                session.moveCaret(command.position)
                true
            }
            is EditorCommand.Select -> {
                session.select(command.selection)
                true
            }
            EditorCommand.SelectAll -> {
                val snapshot = session.snapshot
                val lastLine = snapshot.lineCount - 1
                session.select(
                    EditorSelection(
                        anchor = EditorPosition(0, 0),
                        active = EditorPosition(lastLine, snapshot.line(lastLine).length)
                    )
                )
                true
            }
            EditorCommand.Undo -> session.undo()
            EditorCommand.Redo -> session.redo()
            is EditorCommand.Custom -> extensionHandlers.any { it.handle(command, session) }
        }
    }
}
