package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorStyle

/**
 * Public high-level code editor & viewer facade composable.
 *
 * Automatically delegates to [EditableCodeEditor] for [EditorMode.Editable] or [ReadOnlyCodeViewer] for [EditorMode.ReadOnly].
 *
 * @param code Raw string payload to view or edit.
 * @param mode Editor operational mode ([EditorMode.ReadOnly] or [EditorMode.Editable]).
 * @param document Optional off-thread pre-processed document model.
 * @param style Visual styling configuration (colors, font sizes, line heights).
 * @param language Strongly-typed programming language token for syntax highlighting.
 * @param languageHint String programming language token hint fallback.
 * @param searchQuery Optional search query string to filter matching lines in read-only mode.
 * @param isFoldingEnabled True if code folding toggles are enabled.
 * @param showLineCountHeader True if header line count stat is visible.
 * @param showFoldActionsHeader True if expand/collapse all header icons are visible.
 * @param isWordWrapEnabled True if long lines wrap vertically.
 * @param modifier Layout modifier applied to the container.
 */
@Composable
fun KNetCodeEditor(
    code: String = "",
    mode: EditorMode = EditorMode.ReadOnly,
    document: PreparedDocument? = null,
    style: CodeEditorStyle = CodeEditorStyle(),
    language: CodeLanguage? = null,
    languageHint: String? = null,
    searchQuery: String = "",
    isFoldingEnabled: Boolean = true,
    showLineCountHeader: Boolean = true,
    showFoldActionsHeader: Boolean = true,
    isWordWrapEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val resolvedLang = language
        ?: CodeLanguage.fromId(languageHint)

    when (mode) {
        is EditorMode.Editable -> {
            EditableCodeEditor(
                code = code,
                mode = mode,
                style = style,
                languageHint = resolvedLang.id,
                isFoldingEnabled = isFoldingEnabled,
                showLineCountHeader = showLineCountHeader,
                showFoldActionsHeader = showFoldActionsHeader,
                isWordWrapEnabled = isWordWrapEnabled,
                modifier = modifier
            )
        }
        is EditorMode.ReadOnly -> {
            ReadOnlyCodeViewer(
                code = code,
                document = document,
                style = style,
                languageHint = resolvedLang.id,
                searchQuery = searchQuery,
                isFoldingEnabled = isFoldingEnabled,
                showLineCountHeader = showLineCountHeader,
                showFoldActionsHeader = showFoldActionsHeader,
                isWordWrapEnabled = isWordWrapEnabled,
                modifier = modifier
            )
        }
    }
}
