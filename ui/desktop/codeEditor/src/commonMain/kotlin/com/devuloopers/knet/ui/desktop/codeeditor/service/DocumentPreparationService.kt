package com.devuloopers.knet.ui.desktop.codeeditor.service

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentPreviewGenerator
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.model.DocumentStatistics
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument

/**
 * Encapsulates editor-specific preparation policies, such as preview line limits and IDE truncation.
 *
 * Adheres to KNet UI Specification: Inspector Background Preparation Pipeline v2.0.
 */
object DocumentPreparationService {
    
    /** Maximum displayed line preview threshold for zero-latency large payload rendering. */
    const val EditorPreviewMaxLines = 10000

    /**
     * Prepares document metadata and preview text off the UI thread.
     */
    fun prepare(
        rawText: String,
        formattedText: String,
        language: String
    ): PreparedDocument {
        val activeText = formattedText.ifBlank { rawText }
        val previewResult = DocumentPreviewGenerator.generatePreview(
            text = activeText,
            maxPreviewLines = EditorPreviewMaxLines
        )
        
        val foldRegions = if (!previewResult.isTruncated) {
            FoldManager.calculateFolds(previewResult.previewText.lines())
        } else {
            emptyList()
        }
        
        val statistics = DocumentStatistics(
            totalLines = previewResult.totalLines,
            previewLineLimit = EditorPreviewMaxLines,
            totalCharacters = activeText.length,
            isTruncated = previewResult.isTruncated,
            language = language
        )
        
        return PreparedDocument(
            previewText = previewResult.previewText,
            rawText = rawText,
            formattedText = formattedText,
            statistics = statistics,
            folding = foldRegions
        )
    }
}
