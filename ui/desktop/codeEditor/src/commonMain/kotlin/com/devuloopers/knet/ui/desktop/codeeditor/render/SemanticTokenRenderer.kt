package com.devuloopers.knet.ui.desktop.codeeditor.render

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorToken
import com.devuloopers.knet.ui.desktop.codeeditor.language.EditorTokenCategory
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorSemanticColors

/**
 * Converts UI-neutral semantic tokens and search ranges into Compose text spans.
 */
internal object SemanticTokenRenderer {
    /**
     * Builds one styled logical line.
     *
     * @param lineText Source or folded-display text.
     * @param tokens Semantic tokens produced for the source line.
     * @param searchMatches Search ranges located on this logical line.
     * @param activeSearchMatch Active search range, or `null`.
     * @param colors Active semantic color scheme.
     * @return Styled line preserving source offsets.
     */
    fun renderLine(
        lineText: String,
        tokens: List<EditorToken>,
        searchMatches: List<EditorRange>,
        activeSearchMatch: EditorRange?,
        colors: CodeEditorSemanticColors
    ): AnnotatedString {
        val builder = AnnotatedString.Builder(lineText)
        for (token in tokens) {
            val start = token.startOffset.coerceIn(0, lineText.length)
            val end = token.endOffset.coerceIn(start, lineText.length)
            if (start < end) builder.addStyle(styleFor(token.category, colors), start, end)
        }
        for (match in searchMatches) {
            val start = match.start.column.coerceIn(0, lineText.length)
            val end = match.end.column.coerceIn(start, lineText.length)
            if (start < end) {
                val background = if (match == activeSearchMatch) colors.activeSearchMatch else colors.searchMatch
                builder.addStyle(SpanStyle(background = background), start, end)
            }
        }
        return builder.toAnnotatedString()
    }

    private fun styleFor(category: EditorTokenCategory, colors: CodeEditorSemanticColors): SpanStyle {
        return when (category) {
            EditorTokenCategory.Standard.Keyword -> SpanStyle(colors.keyword, fontWeight = FontWeight.Bold)
            EditorTokenCategory.Standard.String -> SpanStyle(colors.string)
            EditorTokenCategory.Standard.Number -> SpanStyle(colors.number)
            EditorTokenCategory.Standard.Boolean -> SpanStyle(colors.boolean, fontWeight = FontWeight.Bold)
            EditorTokenCategory.Standard.Comment -> SpanStyle(colors.comment)
            EditorTokenCategory.Standard.Identifier -> SpanStyle(colors.identifier)
            EditorTokenCategory.Standard.Property -> SpanStyle(colors.property, fontWeight = FontWeight.SemiBold)
            EditorTokenCategory.Standard.Separator -> SpanStyle(colors.separator)
            EditorTokenCategory.Standard.Tag -> SpanStyle(colors.tag, fontWeight = FontWeight.Bold)
            EditorTokenCategory.Standard.Attribute -> SpanStyle(colors.attribute)
            EditorTokenCategory.Standard.Directive -> SpanStyle(colors.directive, fontWeight = FontWeight.SemiBold)
            EditorTokenCategory.Standard.Variable -> SpanStyle(colors.variable)
            EditorTokenCategory.Standard.Type -> SpanStyle(colors.type, fontWeight = FontWeight.SemiBold)
            EditorTokenCategory.Standard.Declaration -> SpanStyle(colors.declaration)
            is EditorTokenCategory.Custom -> SpanStyle(colors.custom[category.id] ?: colors.identifier)
        }
    }
}
