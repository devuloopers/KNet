package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenizedDocument
import com.devuloopers.knet.ui.desktop.codeeditor.render.SemanticTokenRenderer
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorSemanticColors
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Measures wrapped geometry off the UI thread with a job-owned font resolver and cacheless measurer.
 * The screen's resolver must never cross into the worker: its native font collection is also used
 * by UI rendering and can outlive cancellation briefly while a native measurement returns.
 * Geometry resets for document/width/font/fold changes; a syntax-only refresh waits for an active thumb
 * gesture to finish. Cacheless measurement avoids retaining a second copy of the document's layouts.
 */
@Composable
internal fun rememberEditorLineHeights(
    snapshot: EditorDocumentSnapshot,
    geometryMap: EditorVisualLineMap,
    textWidth: Int,
    wordWrap: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    tokenizedDocument: EditorTokenizedDocument?,
    colors: CodeEditorSemanticColors,
    isScrollbarDragged: Boolean,
): EditorLineHeightIndex? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val style = remember(fontSize, lineHeight) {
        CodeEditorTokens.editorTextStyle(fontSize, lineHeight).copy(fontFamily = FontFamily.Monospace)
    }
    val minimumHeight = with(density) { CodeEditorTokens.GutterLineHeightDp.roundToPx() }
    var heights by remember(snapshot, geometryMap, textWidth, wordWrap, style, density, layoutDirection) {
        mutableStateOf<EditorLineHeightIndex?>(null)
    }
    val dragging by rememberUpdatedState(isScrollbarDragged)
    LaunchedEffect(snapshot, geometryMap, textWidth, wordWrap, style, density, layoutDirection, tokenizedDocument) {
        if (textWidth <= 0) return@LaunchedEffect
        val prepared = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            // Create per job, inside the worker. A cancelled native call may still be returning
            // when the next width/document job begins; neither job may share its font collection.
            val measurer = TextMeasurer(createFontFamilyResolver(), density, layoutDirection, cacheSize = 0)
            val singleLineHeight = if (wordWrap) {
                measurer.measure("M", style = style).size.height.coerceAtLeast(minimumHeight)
            } else minimumHeight
            // A conservative upper bound over supported ASCII weights makes short source rows O(length).
            // Non-ASCII, tabs and longer lines always use the same paragraph engine as the visible Text.
            val ascii = (32..126).map(Int::toChar).joinToString("")
            val maximumAdvance = listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold).maxOf { weight ->
                val sample = measurer.measure(ascii, style = style.copy(fontWeight = weight), softWrap = false)
                (ascii.indices.maxOf { sample.getBoundingBox(it).width }).coerceAtLeast(1f)
            }
            val guaranteedColumns = floor(textWidth / ceil(maximumAdvance)).toInt().coerceAtLeast(0)
            EditorLineHeightIndex.build(geometryMap.visibleLineCount, singleLineHeight) { row ->
                if (row % 128 == 0) context.ensureActive()
                if (!wordWrap) singleLineHeight else {
                    val line = geometryMap.lazyLine(snapshot, row)
                    if (line.displayText.length <= guaranteedColumns && line.displayText.all { it in ' '..'~' }) {
                        singleLineHeight
                    } else {
                        val tokens = tokenizedDocument?.takeIf { it.snapshot.version == snapshot.version }
                            ?.tokensForLine(line.originalLineIndex).orEmpty()
                        val annotated = SemanticTokenRenderer.renderLine(line.displayText, tokens, emptyList(), null, colors)
                        measureEditorRowHeight(measurer, annotated, style, textWidth, minimumHeight)
                    }
                }
            }
        }
        // A changing font weight must not replace the adapter underneath an in-flight drag.
        snapshotFlow { dragging }.first { !it }
        heights = prepared
    }
    return heights
}

/** Measures exactly the text height used by a wrapped editor row, excluding its separately measured gutter. */
internal fun measureEditorRowHeight(
    measurer: TextMeasurer,
    text: androidx.compose.ui.text.AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    textWidth: Int,
    minimumHeight: Int,
): Int = measurer.measure(text, style = style, softWrap = true,
    constraints = Constraints(maxWidth = textWidth)).size.height.coerceAtLeast(minimumHeight)
