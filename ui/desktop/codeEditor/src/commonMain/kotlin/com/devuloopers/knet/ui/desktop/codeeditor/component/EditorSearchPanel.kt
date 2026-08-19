package com.devuloopers.knet.ui.desktop.codeeditor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchOptions
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchFailure
import com.devuloopers.knet.ui.desktop.codeeditor.search.EditorSearchResult
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.theme.EditorColors

/** Built-in non-destructive find/replace surface backed by editor state. */
@Composable
internal fun EditorSearchPanel(
    options: EditorSearchOptions,
    replacement: String,
    result: EditorSearchResult?,
    activeMatchIndex: Int,
    isEditable: Boolean,
    strings: CodeEditorStrings,
    onOptionsChange: (EditorSearchOptions) -> Unit,
    onReplacementChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val resultCount = result?.matches?.size ?: 0
    val searchFailure = result?.failure as? EditorSearchFailure.InvalidRegularExpression
    val resultLabel = when {
        options.query.isEmpty() -> ""
        searchFailure != null -> searchFailure.description
        resultCount == 0 -> strings.noMatches
        else -> "${activeMatchIndex + 1} / $resultCount"
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .widthIn(max = CodeEditorTokens.SearchPanelMaximumWidth)
            .background(EditorColors.SurfaceDark, RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius))
            .border(
                CodeEditorTokens.BorderWidth,
                EditorColors.BorderDark,
                RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
            )
            .padding(CodeEditorTokens.SearchPanelPadding),
        verticalArrangement = Arrangement.spacedBy(CodeEditorTokens.SearchControlSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CodeEditorTokens.SearchControlSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorSearchField(
                value = options.query,
                placeholder = strings.find,
                onValueChange = { onOptionsChange(options.copy(query = it)) },
                onPrevious = onPrevious,
                onNext = onNext,
                onClose = onClose,
                modifier = Modifier.width(CodeEditorTokens.SearchFieldWidth).focusRequester(focusRequester)
            )
            Text(
                text = resultLabel,
                color = EditorColors.TextSecondary,
                style = CodeEditorTokens.editorTextStyle(fontSize = CodeEditorTokens.HeaderFontSize),
                maxLines = 1,
                softWrap = false
            )
            EditorSearchToggle(
                label = "Aa",
                contentDescription = strings.matchCase,
                selected = options.matchCase,
                onClick = { onOptionsChange(options.copy(matchCase = !options.matchCase)) }
            )
            EditorSearchToggle(
                label = "W",
                contentDescription = strings.wholeWord,
                selected = options.wholeWord,
                onClick = { onOptionsChange(options.copy(wholeWord = !options.wholeWord)) }
            )
            EditorSearchToggle(
                label = ".*",
                contentDescription = strings.regularExpression,
                selected = options.useRegularExpression,
                onClick = {
                    onOptionsChange(options.copy(useRegularExpression = !options.useRegularExpression))
                }
            )
            EditorSearchIconAction(strings.previousMatch, onPrevious) {
                Icon(
                    KNetIcons.Back,
                    contentDescription = null,
                    tint = EditorColors.TextSecondary
                )
            }
            EditorSearchIconAction(strings.nextMatch, onNext) {
                Icon(
                    KNetIcons.ChevronRight,
                    contentDescription = null,
                    tint = EditorColors.TextSecondary
                )
            }
            EditorSearchIconAction(strings.closeSearch, onClose) {
                Icon(
                    KNetIcons.Close,
                    contentDescription = null,
                    tint = EditorColors.TextSecondary
                )
            }
        }

        if (isEditable) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CodeEditorTokens.SearchControlSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorSearchField(
                    value = replacement,
                    placeholder = strings.replaceWith,
                    onValueChange = onReplacementChange,
                    onPrevious = onPrevious,
                    onNext = onReplace,
                    onClose = onClose,
                    modifier = Modifier.width(CodeEditorTokens.SearchFieldWidth)
                )
                EditorSearchTextAction(strings.replace, enabled = resultCount > 0, onClick = onReplace)
                EditorSearchTextAction(strings.replaceAll, enabled = resultCount > 0, onClick = onReplaceAll)
            }
        }
    }
}

@Composable
private fun EditorSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(EditorColors.ActiveBlue),
        textStyle = TextStyle(
            color = EditorColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = CodeEditorTokens.FontSize
        ),
        modifier = modifier
            .height(CodeEditorTokens.SearchFieldHeight)
            .background(EditorColors.BackgroundDark, RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius))
            .border(
                CodeEditorTokens.BorderWidth,
                EditorColors.BorderDark,
                RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
            )
            .padding(horizontal = CodeEditorTokens.HeaderActionHorizontalPadding)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onClose()
                        true
                    }
                    Key.Enter -> {
                        if (event.isShiftPressed) onPrevious() else onNext()
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { innerField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = EditorColors.TextMuted,
                        style = CodeEditorTokens.editorTextStyle(fontSize = CodeEditorTokens.FontSize),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                innerField()
            }
        }
    )
}

@Composable
private fun EditorSearchToggle(
    label: String,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(CodeEditorTokens.SearchActionSize)
            .semantics { this.contentDescription = contentDescription }
            .background(
                if (selected) EditorColors.ActiveBlue.copy(alpha = 0.25f) else EditorColors.SurfaceDark,
                RoundedCornerShape(CodeEditorTokens.HeaderActionCornerRadius)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) EditorColors.ActiveBlue else EditorColors.TextSecondary,
            style = CodeEditorTokens.editorTextStyle(fontSize = CodeEditorTokens.HeaderFontSize),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
        )
    }
}

@Composable
private fun EditorSearchIconAction(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(CodeEditorTokens.SearchActionSize)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun EditorSearchTextAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (enabled) EditorColors.ActiveBlue else EditorColors.TextMuted,
        style = CodeEditorTokens.editorTextStyle(fontSize = CodeEditorTokens.HeaderFontSize),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .height(CodeEditorTokens.SearchActionSize)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CodeEditorTokens.HeaderActionHorizontalPadding)
    )
}
