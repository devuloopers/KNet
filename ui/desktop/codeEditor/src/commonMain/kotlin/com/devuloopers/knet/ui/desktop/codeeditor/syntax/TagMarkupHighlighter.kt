package com.devuloopers.knet.ui.desktop.codeeditor.syntax

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.ArrayDeque

/**
 * Shared utility for tag-based markup highlighters (HTML, XML, SVG).
 */
object TagMarkupHighlighter {

    val tagColor = Color(0xFF79C0FF)
    val attrColor = Color(0xFFFFAB70)
    val stringColor = Color(0xFFA5D6FF)
    val commentColor = Color(0xFF8B949E)
    val prologColor = Color(0xFFD2A8FF)
    val textColor = Color.White

    /**
     * Calculates line folding ranges for tag-based markup documents.
     */
    fun calculateFoldRanges(lines: List<String>, voidTags: Set<String> = emptySet()): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val tagStack = ArrayDeque<Pair<Int, String>>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("<") && (trimmed.endsWith(">") || trimmed.contains(">"))) {
                val firstTag = trimmed.substringAfter("<").substringBefore(">").trim()
                val isClosing = firstTag.startsWith("/")
                val tagName = firstTag.removePrefix("/").substringBefore(" ").substringBefore("/").lowercase()
                val isSelfClosing = firstTag.endsWith("/") || voidTags.contains(tagName)
                val isCommentOrProlog = firstTag.startsWith("!") || firstTag.startsWith("?")
                val hasSameLineClosing =
                    !isClosing && (trimmed.contains("</$tagName>", ignoreCase = true) || trimmed.contains(
                        "</$tagName ",
                        ignoreCase = true
                    ))

                if (tagName.isNotBlank() && !isCommentOrProlog && !isSelfClosing && !hasSameLineClosing) {
                    if (isClosing) {
                        if (tagStack.isNotEmpty()) {
                            val (startIndex, startTag) = tagStack.peek()
                            if (startTag.equals(tagName, ignoreCase = true)) {
                                tagStack.pop()
                                if (index > startIndex) {
                                    foldRanges[startIndex] = index
                                }
                            }
                        }
                    } else {
                        tagStack.push(index to tagName)
                    }
                }
            }
        }

        return foldRanges
    }

    /**
     * Builds syntax-highlighted AnnotatedString for HTML/XML lines.
     */
    fun buildMarkupAnnotatedString(lineText: String, isXml: Boolean = false): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < lineText.length) {
                if (lineText[i] == '<') {
                    val endTag = lineText.indexOf('>', i)
                    if (endTag == -1) {
                        append(lineText.substring(i))
                        break
                    }
                    val fullTag = lineText.substring(i, endTag + 1)
                    val isClosing = fullTag.startsWith("</")
                    val isComment = fullTag.startsWith("<!--")
                    val isPrologOrCdata = isXml && (fullTag.startsWith("<?") || fullTag.startsWith("<!"))

                    if (isComment) {
                        withStyle(SpanStyle(color = commentColor)) {
                            append(fullTag)
                        }
                    } else if (isPrologOrCdata) {
                        withStyle(SpanStyle(color = prologColor, fontWeight = FontWeight.Bold)) {
                            append(fullTag)
                        }
                    } else {
                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                            append(if (isClosing) "</" else "<")
                        }

                        val tagInner = fullTag.removePrefix("</").removePrefix("<").removeSuffix(">")
                        val parts = tagInner.split(" ", limit = 2)
                        val tagName = parts[0]
                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                            append(tagName)
                        }

                        if (parts.size > 1) {
                            append(" ")
                            val attrs = parts[1]
                            var inQuote = false
                            val currentToken = StringBuilder()
                            for (ch in attrs) {
                                if (ch == '"' || ch == '\'') {
                                    inQuote = !inQuote
                                    currentToken.append(ch)
                                    if (!inQuote) {
                                        withStyle(SpanStyle(color = stringColor)) {
                                            append(currentToken.toString())
                                        }
                                        currentToken.clear()
                                    }
                                } else if (ch == '=' && !inQuote) {
                                    withStyle(SpanStyle(color = attrColor)) {
                                        append(currentToken.toString())
                                    }
                                    currentToken.clear()
                                    withStyle(SpanStyle(color = Color.White)) {
                                        append("=")
                                    }
                                } else {
                                    currentToken.append(ch)
                                }
                            }
                            if (currentToken.isNotEmpty()) {
                                withStyle(SpanStyle(color = if (inQuote) stringColor else textColor)) {
                                    append(currentToken.toString())
                                }
                            }
                        }

                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                            append(">")
                        }
                    }
                    i = endTag + 1
                } else {
                    val nextTag = lineText.indexOf('<', i)
                    val textChunk = if (nextTag == -1) lineText.substring(i) else lineText.substring(i, nextTag)
                    withStyle(SpanStyle(color = textColor)) {
                        append(textChunk)
                    }
                    i = if (nextTag == -1) lineText.length else nextTag
                }
            }
        }
    }
}

@Composable
fun TagMarkupLineText(
    lineText: String,
    isXml: Boolean = false,
    modifier: Modifier = Modifier
) {
    val annotated = remember(lineText, isXml) {
        TagMarkupHighlighter.buildMarkupAnnotatedString(lineText, isXml)
    }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        softWrap = true,
        modifier = modifier
    )
}
