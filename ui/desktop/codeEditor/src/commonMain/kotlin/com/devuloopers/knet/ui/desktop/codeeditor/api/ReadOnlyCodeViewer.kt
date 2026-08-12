package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.components.placeholder.KNetBodyLoadingPlaceholder
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorHeaderToolbar
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.component.rememberClipboardCopyAction
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorStyle
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



/**
 * Internal state model holding computed payload preview data for large document rendering.
 */
internal data class ProcessedPayloadState(
    val displayedText: String,
    val totalLineCount: Int,
    val displayedLineCount: Int,
    val isTruncated: Boolean
)

internal const val LARGE_PAYLOAD_LINE_THRESHOLD = 5000


/**
 * Controller composable for rendering a read-only code viewer.
 *
 * Supports off-thread large payload parsing, search query filtering, and document fold actions.
 */
@Composable
internal fun ReadOnlyCodeViewer(
    code: String,
    document: PreparedDocument?,
    style: CodeEditorStyle,
    languageHint: String?,
    bodyFormat: BodyFormat?,
    searchQuery: String,
    isFoldingEnabled: Boolean,
    showLineCountHeader: Boolean,
    showFoldActionsHeader: Boolean,
    isWordWrapEnabled: Boolean,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isSearching = searchQuery.isNotBlank()
    val copyAction = rememberClipboardCopyAction()

    val payloadState by produceState<ProcessedPayloadState?>(
        initialValue = null,
        key1 = code,
        key2 = searchQuery,
        key3 = document
    ) {
        value = if (document != null && !isSearching) {
            ProcessedPayloadState(
                displayedText = document.previewText,
                totalLineCount = document.statistics.totalLines,
                displayedLineCount = minOf(document.statistics.totalLines, document.statistics.previewLineLimit),
                isTruncated = document.statistics.isTruncated
            )
        } else {
            withContext(Dispatchers.Default) {
                val lines = if (!isSearching) {
                    code.lines()
                } else {
                    code.lines().filter { it.contains(searchQuery, ignoreCase = true) }
                }
                val total = lines.size
                val isTruncated = total > LARGE_PAYLOAD_LINE_THRESHOLD
                val previewText = if (isTruncated) {
                    lines.take(LARGE_PAYLOAD_LINE_THRESHOLD).joinToString("\n")
                } else {
                    lines.joinToString("\n")
                }
                ProcessedPayloadState(
                    displayedText = previewText,
                    totalLineCount = total,
                    displayedLineCount = if (isTruncated) LARGE_PAYLOAD_LINE_THRESHOLD else total,
                    isTruncated = isTruncated
                )
            }
        }
    }

    val activePayload = payloadState ?: if (document != null && !isSearching) {
        ProcessedPayloadState(
            displayedText = document.previewText,
            totalLineCount = document.statistics.totalLines,
            displayedLineCount = minOf(document.statistics.totalLines, document.statistics.previewLineLimit),
            isTruncated = document.statistics.isTruncated
        )
    } else {
        ProcessedPayloadState(
            displayedText = code,
            totalLineCount = code.count { it == '\n' } + 1,
            displayedLineCount = code.count { it == '\n' } + 1,
            isTruncated = false
        )
    }

    var rawLinesState by remember { mutableStateOf<List<String>?>(null) }
    var foldRegionsState by remember { mutableStateOf<List<FoldRegion>>(emptyList()) }
    var collapsedFoldStartLines by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(activePayload.displayedText) {
        rawLinesState = null
        collapsedFoldStartLines = emptySet()
        val lines = withContext(Dispatchers.Default) {
            activePayload.displayedText.split("\n")
        }
        rawLinesState = lines
        val folds = document?.folding ?: withContext(Dispatchers.Default) {
            FoldManager.calculateFolds(lines, respectLineThreshold = false)
        }
        foldRegionsState = folds
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EditorColors.BackgroundDark, RoundedCornerShape(4.dp))
            .padding(CodeEditorTokens.ContainerPadding)
    ) {
        EditorHeaderToolbar(
            totalLines = activePayload.totalLineCount,
            showLineCountHeader = showLineCountHeader,
            showFoldActionsHeader = showFoldActionsHeader && !isSearching,
            hasFoldRegions = foldRegionsState.isNotEmpty(),
            isHighPerformanceMode = false,
            isTruncated = activePayload.isTruncated,
            displayedLines = activePayload.displayedLineCount,
            onCopyAll = {
                coroutineScope.launch(Dispatchers.IO) {
                    copyAction(code)
                }
            },
            onExpandAll = {
                collapsedFoldStartLines = emptySet()
            },
            onCollapseAll = {
                collapsedFoldStartLines = foldRegionsState.map { it.startLine }.toSet()
            }
        )

        val currentRawLines = rawLinesState
        if (currentRawLines != null) {
            LazyCodeBody(
                mode = LazyCodeBodyMode.ReadOnly,
                rawLines = currentRawLines,
                foldRegions = foldRegionsState,
                collapsedFoldStartLines = collapsedFoldStartLines,
                onToggleFold = { lineIndex ->
                    collapsedFoldStartLines = if (lineIndex in collapsedFoldStartLines) {
                        collapsedFoldStartLines - lineIndex
                    } else {
                        collapsedFoldStartLines + lineIndex
                    }
                },
                isFoldingEnabled = isFoldingEnabled && !isSearching,
                isWordWrapEnabled = isWordWrapEnabled,
                languageHint = document?.statistics?.language ?: languageHint,
                fontSize = style.fontSize,
                lineHeight = style.lineHeight,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            KNetBodyLoadingPlaceholder(
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
