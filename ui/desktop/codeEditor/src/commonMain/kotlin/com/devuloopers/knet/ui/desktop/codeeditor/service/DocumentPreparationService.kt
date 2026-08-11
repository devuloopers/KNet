package com.devuloopers.knet.ui.desktop.codeeditor.service

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentPreviewGenerator
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.model.DocumentStatistics
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument

/**
 * Service encapsulating editor-specific document preparation policies,
 * such as preview line limits, metric generation, and background fold calculations.
 */
object DocumentPreparationService {

    /** Maximum displayed line preview threshold for zero-latency large payload rendering. */
    const val EDITOR_PREVIEW_MAX_LINES: Int = 10000

    /**
     * Prepares document metadata, statistics, and fold regions off the UI thread.
     *
     * @param rawText Original unformatted document string.
     * @param formattedText Pretty-printed or formatted document string (falls back to rawText if blank).
     * @param language Programming or document format language (e.g. "json", "html", "xml").
     * @return [PreparedDocument] containing preview text, statistics, and fold regions.
     */
    fun prepare(
        rawText: String,
        formattedText: String,
        language: String
    ): PreparedDocument {
        val activeText = formattedText.ifBlank { rawText }
        val previewResult = DocumentPreviewGenerator.generatePreview(
            text = activeText,
            maxPreviewLines = EDITOR_PREVIEW_MAX_LINES
        )

        val foldRegions = FoldManager.calculateFolds(
            lines = previewResult.previewText.lines(),
            respectLineThreshold = false
        )

        val statistics = DocumentStatistics(
            totalLines = previewResult.totalLines,
            previewLineLimit = EDITOR_PREVIEW_MAX_LINES,
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
