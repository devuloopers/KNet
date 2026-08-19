package com.devuloopers.knet.ui.desktop.codeeditor.api

import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchOptions
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSessionEvent

/**
 * Header visibility options for the editor surface.
 *
 * @property showLineCount Whether to display the logical line count.
 * @property showFoldActions Whether to display expand-all and collapse-all actions when folds exist.
 */
data class CodeEditorHeaderConfiguration(
    val showLineCount: Boolean = true,
    val showFoldActions: Boolean = true
)

/**
 * User-facing editor labels, kept outside rendering so applications can localize or rebrand them.
 *
 * @property singularLine Singular line-count noun.
 * @property pluralLines Plural line-count noun.
 * @property prettify Formatting action label.
 * @property expandAll Expand-all folds label.
 * @property collapseAll Collapse-all folds label.
 * @property expandBlock Expand-one-fold accessibility label.
 * @property collapseBlock Collapse-one-fold accessibility label.
 * @property copy Copy action label.
 * @property cut Cut action label.
 * @property paste Paste action label.
 * @property selectAll Select-all action label.
 * @property find Find-field placeholder and accessibility label.
 * @property replaceWith Replacement-field placeholder and accessibility label.
 * @property previousMatch Previous-match action label.
 * @property nextMatch Next-match action label.
 * @property closeSearch Close-search action label.
 * @property matchCase Match-case toggle label.
 * @property wholeWord Whole-word toggle label.
 * @property regularExpression Regular-expression toggle label.
 * @property replace Replace-current action label.
 * @property replaceAll Replace-all action label.
 * @property noMatches Empty search-result label.
 */
data class CodeEditorStrings(
    val singularLine: String = "line",
    val pluralLines: String = "lines",
    val prettify: String = "Prettify",
    val expandAll: String = "Expand All",
    val collapseAll: String = "Collapse All",
    val expandBlock: String = "Expand block",
    val collapseBlock: String = "Collapse block",
    val copy: String = "Copy",
    val cut: String = "Cut",
    val paste: String = "Paste",
    val selectAll: String = "Select All",
    val find: String = "Find",
    val replaceWith: String = "Replace with",
    val previousMatch: String = "Previous match",
    val nextMatch: String = "Next match",
    val closeSearch: String = "Close search",
    val matchCase: String = "Match case",
    val wholeWord: String = "Whole word",
    val regularExpression: String = "Regular expression",
    val replace: String = "Replace",
    val replaceAll: String = "Replace All",
    val noMatches: String = "No matches"
)

/**
 * Cohesive behavior configuration for one editor surface.
 *
 * @property mode Read-only or editable behavior.
 * @property language Language contribution selected from the active registry.
 * @property isFoldingEnabled Whether language-provided folding is enabled.
 * @property isWordWrapEnabled Whether long logical lines wrap. Code-editor default is disabled.
 * @property header Header visibility configuration.
 * @property placeholder Empty editable-document placeholder.
 * @property isSearchEnabled Whether the built-in find/replace surface and shortcut are enabled.
 * @property search Optional externally supplied initial search options.
 * @property strings Localizable editor labels.
 */
data class CodeEditorConfiguration(
    val mode: EditorMode = EditorMode.ReadOnly,
    val language: CodeLanguage = CodeLanguage.PLAIN,
    val isFoldingEnabled: Boolean = true,
    val isWordWrapEnabled: Boolean = false,
    val header: CodeEditorHeaderConfiguration = CodeEditorHeaderConfiguration(),
    val placeholder: String = "",
    val isSearchEnabled: Boolean = true,
    val search: EditorSearchOptions? = null,
    val strings: CodeEditorStrings = CodeEditorStrings()
)

/**
 * Interaction callbacks supplied by an editor consumer.
 *
 * [onDocumentChange] is the scalable primary callback and exposes versioned snapshots plus exact
 * deltas. [onTextChange] is a convenience adapter for controlled string state and performs explicit
 * O(document size) serialization only when supplied.
 *
 * @property onDocumentChange Called synchronously for every user, undo, or redo document transition.
 * @property onTextChange Optional full-text controlled-state callback.
 * @property onPrettify Optional consumer-owned formatting command.
 */
data class CodeEditorActions(
    val onDocumentChange: (EditorSessionEvent) -> Unit = {},
    val onTextChange: ((String) -> Unit)? = null,
    val onPrettify: (() -> Unit)? = null
)
