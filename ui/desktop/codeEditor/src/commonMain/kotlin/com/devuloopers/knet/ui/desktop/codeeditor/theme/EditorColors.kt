package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.graphics.Color

/**
 * Single Source of Truth for all Code Editor & Code Viewer color tokens.
 */
object EditorColors {

    // ─── Container & Background Colors ──────────────────────────────────────────

    /** Deep obsidian background color for the code viewer container. */
    val BackgroundDark = Color(0xFF0F141C)

    /** Dark gutter sidebar background color. */
    val GutterBackground = Color(0xFF131822)

    /** Subdued dark border color for containers and splitters. */
    val BorderDark = Color(0xFF1E2636)

    // ─── Text & Caret Colors ──────────────────────────────────────────────────

    /** Active line number text color in the gutter. */
    val ActiveLineNumber = Color(0xFFE2E8F0)

    /** Inactive line number text color in the gutter. */
    val InactiveLineNumber = Color(0xFF475569)

    /** Secondary label color for line count and truncation warnings. */
    val TextSecondary = Color(0xFF94A3B8)

    /** Vibrant cyan-blue active caret/cursor color. */
    val ActiveBlue = Color(0xFF38BDF8)

    /** Fold arrow icon tint color. */
    val FoldIconTint = Color(0xFF64748B)

    // ─── Syntax Token Highlight Colors ───────────────────────────────────────────

    /** Structural JSON/XML/JS delimiters ({}, [], :, comma). */
    val TokenPunctuation = Color(0xFF94A3B8)

    /** JSON/XML object key names (cyan). */
    val TokenProperty = Color(0xFF38BDF8)

    /** Double-quoted string property values (emerald green). */
    val TokenString = Color(0xFF34D399)

    /** Numeric literal property values (amber yellow). */
    val TokenNumber = Color(0xFFFBBF24)

    /** Boolean true/false and null literals (purple). */
    val TokenKeyword = Color(0xFFA855F7)

    /** HTML/XML tag names (blue). */
    val TokenTag = Color(0xFF60A5FA)

    /** HTML/XML attribute names (violet). */
    val TokenAttribute = Color(0xFFC084FC)

    /** Single & multi-line comment text (slate muted). */
    val TokenComment = Color(0xFF64748B)

    /** Plain unparsed fallback text color. */
    val TokenPlainText = Color(0xFFF8FAFC)
}
