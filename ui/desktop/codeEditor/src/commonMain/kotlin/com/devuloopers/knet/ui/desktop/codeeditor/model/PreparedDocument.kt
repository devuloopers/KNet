package com.devuloopers.knet.ui.desktop.codeeditor.model

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion

/**
 * Immutable pre-processed document model produced asynchronously off the UI thread.
 * Contains pre-formatted text, pre-calculated fold regions, language hints, and line metrics.
 *
 * Adheres to KNet UI Specification: Inspector Background Preparation Pipeline v1.0.
 */
data class DocumentStatistics(
    val totalLines: Int = 1,
    val previewLineLimit: Int = 10000,
    val totalCharacters: Int = 0,
    val isTruncated: Boolean = false,
    val language: String = "json"
)

/**
 * Immutable pre-processed document model produced asynchronously off the UI thread.
 * Contains pre-formatted text, pre-calculated fold regions, IDE statistics, and metrics.
 *
 * Adheres to KNet UI Specification: Inspector Background Preparation Pipeline v2.0.
 */
data class PreparedDocument(
    val previewText: String = "",
    val rawText: String = "",
    val formattedText: String = "",
    val statistics: DocumentStatistics = DocumentStatistics(),
    val folding: List<FoldRegion> = emptyList()
)
