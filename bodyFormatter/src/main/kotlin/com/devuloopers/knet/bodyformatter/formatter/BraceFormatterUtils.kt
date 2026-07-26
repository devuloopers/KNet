package com.devuloopers.knet.bodyformatter.formatter

/**
 * Shared utility extensions for builder-based brace formatting to eliminate code duplication.
 */
fun StringBuilder.appendOpenBrace(indentLevel: Int): Int {
    val temp = this.toString().trimEnd()
    this.clear().append(temp)
    this.append(" {\n")
    val newLevel = indentLevel + 1
    this.append("  ".repeat(newLevel))
    return newLevel
}

fun StringBuilder.appendCloseBrace(indentLevel: Int, suffix: String = "\n"): Int {
    val newLevel = (indentLevel - 1).coerceAtLeast(0)
    if (this.isNotEmpty() && this.last() != '\n') {
        this.append('\n')
    }
    val lastLineStart = this.lastIndexOf("\n")
    if (lastLineStart >= 0) {
        val lastLine = this.substring(lastLineStart + 1)
        if (lastLine.isBlank()) {
            this.delete(lastLineStart + 1, this.length)
        }
    }
    this.append("  ".repeat(newLevel)).append("}").append(suffix)
    return newLevel
}
