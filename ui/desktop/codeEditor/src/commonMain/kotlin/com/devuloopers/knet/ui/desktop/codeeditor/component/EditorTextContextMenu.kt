package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.devuloopers.knet.ui.core.components.menu.ContextMenuItem
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection

/** Suppresses the platform text field menu so the editor can present one consistent context menu. */
@OptIn(ExperimentalFoundationApi::class)
internal object EmptyTextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit
    ) {
        content()
    }
}

/**
 * Builds context-menu actions over an immutable snapshot and command callbacks.
 */
@Composable
internal fun rememberEditorContextMenuItems(
    snapshot: EditorDocumentSnapshot,
    selection: EditorSelection?,
    mode: LazyCodeBodyMode,
    strings: CodeEditorStrings,
    copyAction: (String) -> Unit,
    pasteAction: ((String) -> Unit) -> Unit,
    onDeleteSelection: () -> Unit,
    onPaste: (String) -> Unit,
    onSelectAll: () -> Unit
): List<ContextMenuItem> = remember(
    snapshot,
    selection,
    mode,
    strings,
    copyAction,
    pasteAction,
    onDeleteSelection,
    onPaste,
    onSelectAll
) {
    val selectedText = selection
        ?.takeUnless(EditorSelection::isEmpty)
        ?.let { snapshot.text(it.range) }
        .orEmpty()
    buildList {
        if (selectedText.isNotEmpty()) {
            add(ContextMenuItem(label = strings.copy, shortcut = "Ctrl/Cmd+C", onClick = { copyAction(selectedText) }))
            if (mode == LazyCodeBodyMode.Editable) {
                add(
                    ContextMenuItem(
                        label = strings.cut,
                        shortcut = "Ctrl/Cmd+X",
                        onClick = {
                            copyAction(selectedText)
                            onDeleteSelection()
                        }
                    )
                )
            }
        }
        if (mode == LazyCodeBodyMode.Editable) {
            add(
                ContextMenuItem(
                    label = strings.paste,
                    shortcut = "Ctrl/Cmd+V",
                    onClick = { pasteAction(onPaste) }
                )
            )
        }
        add(ContextMenuItem(label = strings.selectAll, shortcut = "Ctrl/Cmd+A", onClick = onSelectAll))
    }
}
