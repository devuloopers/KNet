package com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.registry.CodeHighlighterRegistry

internal class FsmTokenMakerVisualTransformation(
    private val languageHint: String? = null,
    private val maxCacheEntries: Int = 16
) : VisualTransformation {

    private val tokenCache = object : LinkedHashMap<String, TransformedText>(maxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TransformedText>?): Boolean {
            return size > maxCacheEntries
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        if (rawText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        synchronized(tokenCache) {
            val cached = tokenCache[rawText]
            if (cached != null) return cached
        }

        val highlighter = CodeHighlighterRegistry.resolveByLanguage(languageHint ?: "plain")
        val lines = rawText.lines()

        val builder = AnnotatedString.Builder()
        for (i in lines.indices) {
            val lineText = lines[i]
            builder.append(highlighter.highlightLine(lineText))
            if (i < lines.lastIndex) {
                builder.append("\n")
            }
        }

        val transformedText = TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)

        synchronized(tokenCache) {
            tokenCache[rawText] = transformedText
        }

        return transformedText
    }
}
