package com.devuloopers.knet.ui.desktop.codeeditor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

/**
 * Theme colors for semantic editor tokens and search matches.
 *
 * @property keyword Language keyword color.
 * @property string String literal color.
 * @property number Numeric literal color.
 * @property boolean Boolean and null literal color.
 * @property comment Comment color.
 * @property identifier Default identifier color.
 * @property property Object-property color.
 * @property separator Structural punctuation color.
 * @property tag Markup tag color.
 * @property attribute Markup attribute color.
 * @property directive Directive color.
 * @property variable Variable-reference color.
 * @property type Type-name color.
 * @property declaration Markup declaration color.
 * @property searchMatch Search-match background color.
 * @property activeSearchMatch Active search-match background color.
 * @property custom Colors for namespaced custom token categories.
 */
data class CodeEditorSemanticColors(
    val keyword: Color = Color(0xFFFF7B72),
    val string: Color = Color(0xFFE6EDF3),
    val number: Color = Color(0xFFD2A8FF),
    val boolean: Color = Color(0xFFFFAB70),
    val comment: Color = Color(0xFF8B949E),
    val identifier: Color = Color(0xFFE6EDF3),
    val property: Color = Color(0xFF79C0FF),
    val separator: Color = Color(0xFF7D8590),
    val tag: Color = Color(0xFF79C0FF),
    val attribute: Color = Color(0xFFFFAB70),
    val directive: Color = Color(0xFFD2A8FF),
    val variable: Color = Color(0xFFFFAB70),
    val type: Color = Color(0xFF79C0FF),
    val declaration: Color = Color(0xFFD2A8FF),
    val searchMatch: Color = Color(0x665A4A00),
    val activeSearchMatch: Color = Color(0xAA8A6D00),
    val custom: Map<String, Color> = emptyMap()
)

/**
 * Visual style configuration object for [com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor] typography and metrics.
 *
 * @property fontSize Monospace font size for code text and line numbers.
 * @property lineHeight Vertical line height for text rows.
 * @property backgroundColor Background color of the editor container.
 * @property semanticColors Semantic syntax and search colors.
 */
data class CodeEditorStyle(
    val fontSize: TextUnit = CodeEditorTokens.FontSize,
    val lineHeight: TextUnit = CodeEditorTokens.LineHeight,
    val backgroundColor: Color = Color(0xFF0D1117),
    val semanticColors: CodeEditorSemanticColors = CodeEditorSemanticColors()
)
