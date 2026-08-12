package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.SelectionEngine
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection

/**
 * Empty implementation of Compose [TextContextMenu] used to suppress default white context menu popups on [BasicTextField].
 */
@OptIn(ExperimentalFoundationApi::class)
object EmptyTextContextMenu : TextContextMenu {
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
 * Builds and remembers a list of fold-aware [ContextMenuItem] definitions for [LazyCodeBody].
 *
 * Dynamically computes Copy, Cut, Paste, and Select All items based on current selection and editing mode.
 */
@Composable
fun rememberEditorContextMenuItems(
    rawLines: List<String>,
    effectiveSelection: EditorSelection?,
    foldRegions: List<FoldRegion>,
    collapsedFoldStartLines: Set<Int>,
    mode: LazyCodeBodyMode,
    copyAction: (String) -> Unit,
    pasteAction: () -> String?,
    onDocumentLinesChanged: ((List<String>) -> Unit)?,
    onSelectionChange: ((EditorSelection?) -> Unit)?
): List<ContextMenuItem> = remember(
    copyAction,
    pasteAction,
    rawLines,
    effectiveSelection,
    foldRegions,
    collapsedFoldStartLines,
    mode
) {
    val selectedText = if (effectiveSelection != null && !effectiveSelection.isEmpty) {
        SelectionEngine.extractSelectedText(
            buffer = DocumentBuffer(rawLines),
            selection = effectiveSelection,
            foldRegions = foldRegions,
            collapsedFoldStartLines = collapsedFoldStartLines
        )
    } else ""

    val menuItems = mutableListOf<ContextMenuItem>()

    if (selectedText.isNotEmpty()) {
        menuItems.add(
            ContextMenuItem(
                label = "Copy",
                shortcut = "Ctrl+C",
                onClick = { copyAction(selectedText) }
            )
        )
        if (mode == LazyCodeBodyMode.Editable && effectiveSelection != null) {
            menuItems.add(
                ContextMenuItem(
                    label = "Cut",
                    shortcut = "Ctrl+X",
                    onClick = {
                        copyAction(selectedText)
                        val docBuffer = DocumentBuffer(rawLines)
                        SelectionEngine.deleteSelectedText(
                            buffer = docBuffer,
                            selection = effectiveSelection,
                            foldRegions = foldRegions,
                            collapsedFoldStartLines = collapsedFoldStartLines
                        )
                        onDocumentLinesChanged?.invoke(docBuffer.getLines())
                        onSelectionChange?.invoke(null)
                    }
                )
            )
        }
    }

    if (mode == LazyCodeBodyMode.Editable) {
        menuItems.add(
            ContextMenuItem(
                label = "Paste",
                shortcut = "Ctrl+V",
                onClick = { pasteAction() }
            )
        )
    }

    menuItems.add(
        ContextMenuItem(
            label = "Select All",
            shortcut = "Ctrl+A",
            onClick = {
                val fullSelection = EditorSelection(
                    startLine = 0,
                    startCol = 0,
                    endLine = (rawLines.size - 1).coerceAtLeast(0),
                    endCol = rawLines.lastOrNull()?.length ?: 0
                )
                onSelectionChange?.invoke(fullSelection)
            }
        )
    )

    menuItems
}
