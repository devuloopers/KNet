package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Result data class produced by [DocumentPreviewGenerator].
 */
data class PreviewResult(
    val previewText: String,
    val totalLines: Int,
    val previewLines: Int,
    val isTruncated: Boolean
)

/**
 * High-performance single-pass preview generator for large text payloads.
 *
 * Scans text once to calculate total lines and determine preview truncation index
 * without allocating intermediate string lists for full documents.
 *
 * Adheres to KNet Editor Regression Rule: Avoids allocations proportional to document size.
 */
object DocumentPreviewGenerator {

    /**
     * Generates preview text and metrics in a single pass.
     *
     * @param text Full raw or formatted document text.
     * @param maxPreviewLines Maximum line preview threshold.
     * @return [PreviewResult] containing preview substring and line statistics.
     */
    fun generatePreview(
        text: String,
        maxPreviewLines: Int
    ): PreviewResult {
        if (text.isEmpty()) {
            return PreviewResult("", 1, 1, false)
        }

        var newlineCount = 0
        var previewEndIndex = -1

        for (i in text.indices) {
            if (text[i] == '\n') {
                newlineCount++
                if (newlineCount == maxPreviewLines && previewEndIndex == -1) {
                    previewEndIndex = i
                }
            }
        }

        val totalLines = newlineCount + 1
        val isTruncated = totalLines > maxPreviewLines

        val previewText = if (isTruncated && previewEndIndex != -1) {
            text.substring(0, previewEndIndex)
        } else {
            text
        }

        val previewLines = if (isTruncated) maxPreviewLines else totalLines

        return PreviewResult(
            previewText = previewText,
            totalLines = totalLines,
            previewLines = previewLines,
            isTruncated = isTruncated
        )
    }
}
