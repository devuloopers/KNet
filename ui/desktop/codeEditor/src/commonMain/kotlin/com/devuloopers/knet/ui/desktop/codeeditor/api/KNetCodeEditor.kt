package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorHeaderToolbar
import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorSearchPanel
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.component.rememberClipboardCopyAction
import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorLanguageRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorLanguageEditing
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorSyntaxEngine
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenizedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.language.builtin.BuiltInEditorLanguages
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchEngine
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorChangeOrigin
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorStyle
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val FOLD_RECALCULATION_DEBOUNCE_MILLIS = 120L

/**
 * Primary stateful reusable editor composable.
 *
 * Consumers that require scalable editing should retain [state] and consume versioned deltas through
 * [actions]. The editor owns rendering and interaction only; language registration, formatting,
 * persistence, and feature semantics remain externally composed.
 *
 * @param state Stable editor state and UI-neutral session.
 * @param configuration Editor behavior and selected language.
 * @param actions Consumer callbacks.
 * @param registry Immutable language contributions available to this editor.
 * @param style Visual editor style.
 * @param modifier Layout modifier for the editor container.
 */
@Composable
fun KNetCodeEditor(
    state: CodeEditorState,
    configuration: CodeEditorConfiguration = CodeEditorConfiguration(),
    actions: CodeEditorActions = CodeEditorActions(),
    registry: EditorLanguageRegistry = BuiltInEditorLanguages.registry,
    style: CodeEditorStyle = CodeEditorStyle(),
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val languageSupport = remember(registry, configuration.language) {
        registry.resolve(configuration.language)
    }
    var tokenizedDocument by remember(state, languageSupport.language.id) {
        mutableStateOf<EditorTokenizedDocument?>(null)
    }
    var foldRegions by remember(state, languageSupport.language.id) { mutableStateOf<List<FoldRegion>>(emptyList()) }
    var collapsedFoldStarts by remember(state) { mutableStateOf<Set<Int>>(emptySet()) }
    val currentActions by rememberUpdatedState(actions)

    LaunchedEffect(snapshot.version, languageSupport) {
        val previous = tokenizedDocument
        val changes = state.latestEvent
            ?.takeIf { it.snapshot.version == snapshot.version }
            ?.documentChanges
            .orEmpty()
        tokenizedDocument = withContext(Dispatchers.Default) {
            val workerContext = currentCoroutineContext()
            EditorSyntaxEngine.tokenize(
                snapshot = snapshot,
                support = languageSupport,
                previous = previous,
                changes = changes,
                checkpoint = EditorCancellationCheckpoint { workerContext.ensureActive() }
            )
        }
    }

    LaunchedEffect(snapshot.version, languageSupport, configuration.isFoldingEnabled) {
        if (!configuration.isFoldingEnabled || languageSupport.foldingProvider == null) {
            foldRegions = emptyList()
            collapsedFoldStarts = emptySet()
            return@LaunchedEffect
        }
        delay(FOLD_RECALCULATION_DEBOUNCE_MILLIS)
        val calculated = withContext(Dispatchers.Default) {
            val workerContext = currentCoroutineContext()
            languageSupport.foldingProvider.calculate(
                snapshot,
                EditorCancellationCheckpoint { workerContext.ensureActive() }
            )
        }
        foldRegions = calculated
        val validStarts = calculated.mapTo(mutableSetOf(), FoldRegion::startLine)
        collapsedFoldStarts = collapsedFoldStarts.intersect(validStarts)
    }

    LaunchedEffect(configuration.search, configuration.isSearchEnabled) {
        when {
            !configuration.isSearchEnabled -> state.closeSearch()
            configuration.search != null -> state.openSearch(configuration.search)
        }
    }

    LaunchedEffect(snapshot.version, state.searchOptions, state.isSearchVisible) {
        val options = state.searchOptions
        state.updateSearchResult(
            if (!state.isSearchVisible || options.query.isEmpty()) null
            else withContext(Dispatchers.Default) {
                val workerContext = currentCoroutineContext()
                EditorSearchEngine.search(
                    snapshot,
                    options,
                    EditorCancellationCheckpoint { workerContext.ensureActive() }
                )
            }
        )
    }

    DisposableEffect(state.session) {
        val subscription = state.session.subscribe { event ->
            val changesDocument = event.documentChanges.isNotEmpty() ||
                event.origin == EditorChangeOrigin.Undo ||
                event.origin == EditorChangeOrigin.Redo
            if (event.origin != EditorChangeOrigin.External && changesDocument) {
                currentActions.onDocumentChange(event)
                currentActions.onTextChange?.invoke(event.snapshot.text())
            }
        }
        onDispose(subscription::cancel)
    }

    val copyAction = rememberClipboardCopyAction()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(style.backgroundColor, RoundedCornerShape(CodeEditorTokens.ContainerCornerRadius))
            .border(
                CodeEditorTokens.BorderWidth,
                EditorColors.BorderDark.copy(alpha = 0.5f),
                RoundedCornerShape(CodeEditorTokens.ContainerCornerRadius)
            )
            .padding(CodeEditorTokens.ContainerPadding)
    ) {
        EditorHeaderToolbar(
            totalLines = snapshot.lineCount,
            showLineCountHeader = configuration.header.showLineCount,
            showFoldActionsHeader = configuration.header.showFoldActions,
            hasFoldRegions = foldRegions.isNotEmpty(),
            strings = configuration.strings,
            onCopyAll = { copyAction(snapshot.text()) },
            onPrettify = actions.onPrettify,
            onExpandAll = { collapsedFoldStarts = emptySet() },
            onCollapseAll = { collapsedFoldStarts = foldRegions.mapTo(mutableSetOf(), FoldRegion::startLine) }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyCodeBody(
                snapshot = snapshot,
                mode = if (configuration.mode == EditorMode.Editable) {
                    LazyCodeBodyMode.Editable
                } else {
                    LazyCodeBodyMode.ReadOnly
                },
                tokenizedDocument = tokenizedDocument,
                searchResult = state.searchResult,
                activeSearchMatch = state.activeSearchMatch?.range,
                semanticColors = style.semanticColors,
                strings = configuration.strings,
                foldRegions = foldRegions,
                collapsedFoldStartLines = collapsedFoldStarts,
                onToggleFold = { lineIndex ->
                    val isCollapsing = lineIndex !in collapsedFoldStarts
                    collapsedFoldStarts = if (isCollapsing) {
                        collapsedFoldStarts + lineIndex
                    } else {
                        collapsedFoldStarts - lineIndex
                    }
                    if (isCollapsing) {
                        val region = foldRegions.firstOrNull { it.startLine == lineIndex }
                        if (region != null && state.caret.line in (lineIndex + 1)..region.endLine) {
                            state.session.moveCaret(EditorPosition(lineIndex, snapshot.line(lineIndex).length))
                        }
                    }
                },
                isFoldingEnabled = configuration.isFoldingEnabled,
                isWordWrapEnabled = configuration.isWordWrapEnabled,
                fontSize = style.fontSize,
                lineHeight = style.lineHeight,
                caret = state.caret,
                onCaretChange = state.session::moveCaret,
                selection = state.selection,
                onSelectionChange = state.session::select,
                onDeleteSelection = {
                    state.selection?.let { selection ->
                        state.session.apply(EditorTextEdit(selection.range, "", EditorEditKind.Structural))
                    }
                },
                onDeleteCurrentLine = {
                    deleteCurrentLine(state)
                },
                onPaste = { text ->
                    state.session.insert(text, EditorEditKind.Structural)
                },
                onSelectAll = {
                    val lastLine = snapshot.lineCount - 1
                    state.session.select(
                        EditorSelection(
                            anchor = EditorPosition(0, 0),
                            active = EditorPosition(lastLine, snapshot.line(lastLine).length)
                        )
                    )
                },
                onLineChanged = { lineIndex, newText ->
                    EditorLanguageEditing.applyLineChange(
                        session = state.session,
                        lineIndex = lineIndex,
                        newText = newText,
                        bracketProvider = languageSupport.bracketProvider
                    )
                },
                onLineSplit = { lineIndex, column ->
                    val position = EditorPosition(lineIndex, column)
                    val indentation = languageSupport.indentationProvider
                        ?.indentationForNewLine(snapshot, position)
                        .orEmpty()
                    state.session.splitLine(position, indentation)
                },
                onLineMerge = state.session::mergeWithPreviousLine,
                onMultiLinePaste = { lineIndex, column, pastedText ->
                    val range = state.selection?.range ?: EditorRange.caret(EditorPosition(lineIndex, column))
                    state.session.apply(EditorTextEdit(range, pastedText, EditorEditKind.Structural))
                },
                onUndo = { state.session.undo() },
                onRedo = { state.session.redo() },
                onToggleComment = if (
                    configuration.mode == EditorMode.Editable && languageSupport.comments != null
                ) {
                    { EditorLanguageEditing.toggleComment(state.session, languageSupport.comments) }
                } else {
                    null
                },
                shouldRequestEditorFocus = !state.isSearchVisible,
                onOpenSearch = if (configuration.isSearchEnabled) state::openSearch else null,
                onCloseSearch = if (state.isSearchVisible) state::closeSearch else null,
                modifier = Modifier.fillMaxSize()
            )

            if (state.isSearchVisible && configuration.isSearchEnabled) {
                EditorSearchPanel(
                    options = state.searchOptions,
                    replacement = state.searchReplacement,
                    result = state.searchResult,
                    activeMatchIndex = state.activeSearchMatchIndex,
                    isEditable = configuration.mode == EditorMode.Editable,
                    strings = configuration.strings,
                    onOptionsChange = state::updateSearchOptions,
                    onReplacementChange = state::updateSearchReplacement,
                    onPrevious = { state.previousSearchMatch() },
                    onNext = { state.nextSearchMatch() },
                    onReplace = { state.replaceActiveSearchMatch(state.searchReplacement) },
                    onReplaceAll = { state.replaceAllSearchMatches(state.searchReplacement) },
                    onClose = state::closeSearch,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                )
            }

            if (
                configuration.mode == EditorMode.Editable &&
                snapshot.lineCount == 1 &&
                snapshot.line(0).isEmpty() &&
                configuration.placeholder.isNotEmpty()
            ) {
                Text(
                    text = configuration.placeholder,
                    color = EditorColors.TextSecondary.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    style = CodeEditorTokens.editorTextStyle(style.fontSize, style.lineHeight),
                    modifier = Modifier.padding(
                        start = CodeEditorTokens.PlaceholderStartPadding,
                        top = CodeEditorTokens.PlaceholderTopPadding
                    )
                )
            }
        }
    }
}

/**
 * Convenience controlled-string facade over the primary stateful editor API.
 *
 * Supplying [CodeEditorActions.onTextChange] opts into full serialization after document edits.
 * Reusable editor integrations should prefer the stateful overload and [CodeEditorActions.onDocumentChange].
 *
 * @param code Externally controlled complete text.
 * @param configuration Editor behavior.
 * @param actions Consumer callbacks.
 * @param registry Immutable language registry.
 * @param style Visual editor style.
 * @param modifier Layout modifier.
 */
@Composable
fun KNetCodeEditor(
    code: String,
    configuration: CodeEditorConfiguration = CodeEditorConfiguration(),
    actions: CodeEditorActions = CodeEditorActions(),
    registry: EditorLanguageRegistry = BuiltInEditorLanguages.registry,
    style: CodeEditorStyle = CodeEditorStyle(),
    modifier: Modifier = Modifier
) {
    val state = rememberCodeEditorState(code)
    var lastInternallyEmittedText by remember { mutableStateOf(code) }
    val currentActions by rememberUpdatedState(actions)

    LaunchedEffect(code) {
        if (code != lastInternallyEmittedText) {
            lastInternallyEmittedText = code
            state.replaceFromExternal(code)
        }
    }

    KNetCodeEditor(
        state = state,
        configuration = configuration,
        actions = CodeEditorActions(
            onDocumentChange = { currentActions.onDocumentChange(it) },
            onTextChange = if (actions.onTextChange == null) null else { text ->
                lastInternallyEmittedText = text
                currentActions.onTextChange?.invoke(text)
            },
            onPrettify = actions.onPrettify
        ),
        registry = registry,
        style = style,
        modifier = modifier
    )
}

private fun deleteCurrentLine(state: CodeEditorState) {
    val snapshot = state.snapshot
    val lineIndex = state.caret.line
    val range = when {
        snapshot.lineCount == 1 -> EditorRange(EditorPosition(0, 0), EditorPosition(0, snapshot.line(0).length))
        lineIndex < snapshot.lineCount - 1 -> EditorRange(EditorPosition(lineIndex, 0), EditorPosition(lineIndex + 1, 0))
        else -> {
            val previousLine = lineIndex - 1
            EditorRange(
                EditorPosition(previousLine, snapshot.line(previousLine).length),
                EditorPosition(lineIndex, snapshot.line(lineIndex).length)
            )
        }
    }
    state.session.apply(EditorTextEdit(range, "", EditorEditKind.Structural))
}
