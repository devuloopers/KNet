package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Represents a collapsible block region in a code document.
 *
 * @property startLine The 0-indexed line where the fold starts (e.g. line containing '{' or '[').
 * @property endLine The 0-indexed line where the fold ends (e.g. line containing '}' or ']').
 * @property closingSymbol The character or string token indicating closing (e.g. "}", "]").
 */
data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val closingSymbol: String = "}"
)
