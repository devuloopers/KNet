package com.devuloopers.knet.engine.formatter.util

/**
 * Shared utility extension functions for builder-based brace formatting.
 * Eliminates code duplication across CSS, JS, and other structured formatters.
 */

/**
 * Appends an opening brace `{` on the same line, then increases indentation.
 *
 * @param indentLevel Current indentation level.
 * @return New (incremented) indentation level.
 */
fun StringBuilder.appendOpenBrace(indentLevel: Int): Int {
    val temp = this.toString().trimEnd()
    this.clear().append(temp)
    this.append(" {\n")
    val newLevel = indentLevel + 1
    this.append("  ".repeat(newLevel))
    return newLevel
}

/**
 * Appends a closing brace `}` with proper de-indentation and an optional suffix.
 *
 * @param indentLevel Current indentation level.
 * @param suffix Suffix to append after the closing brace (e.g. `"\n"` or `"\n\n"`).
 * @return New (decremented) indentation level.
 */
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
