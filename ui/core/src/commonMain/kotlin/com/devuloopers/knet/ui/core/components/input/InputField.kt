package com.devuloopers.knet.ui.core.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.textCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * High-density single line text input field using cohesive parameter objects (< 7 parameters total).
 * Supports precision text overflow hover popups anchored below the field when text exceeds viewport bounds.
 */
@Composable
fun KNetTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    config: InputFieldConfig = InputFieldConfig.Default,
    state: InputFieldState = InputFieldState.Default,
    slots: InputFieldSlots = InputFieldSlots.Empty
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val dimensions = KNetTheme.dimensions
    var wasFocused by remember { mutableStateOf(false) }

    val backgroundColor = config.backgroundColor ?: themeColors.surfaceVariant
    val borderColor = config.borderColor ?: when {
        state.isError -> themeColors.semantic.error
        wasFocused -> themeColors.borderFocused
        else -> themeColors.border
    }
    val textStyle = typography.bodySmall.copy(
        color = if (state.enabled) themeColors.textPrimary else themeColors.textMuted
    )

    val isPassword = config.visualTransformation is PasswordVisualTransformation

    Column(
        modifier = modifier
    ) {
        OverflowTextPopupHost(
            text = value.text,
            textStyle = textStyle,
            enabled = config.showHoverPopupOnOverflow && !isPassword,
            horizontalContentPadding = 16.dp,
            hoverDebounceMs = config.hoverDebounceMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(config.fieldHeight ?: dimensions.inputHeightStandard)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.small)
                    .background(backgroundColor)
                    .then(if (borderColor != androidx.compose.ui.graphics.Color.Transparent) Modifier.border(1.dp, borderColor, shapes.small) else Modifier)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !wasFocused && config.autoSelectAllOnFocus && value.text.isNotEmpty()) {
                            onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                        }
                        wasFocused = focusState.isFocused
                    }
                    .textCursor(),
                enabled = state.enabled,
                readOnly = state.readOnly,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(themeColors.accent),
                visualTransformation = config.visualTransformation,
                keyboardOptions = config.keyboardOptions,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (slots.prefix != null) {
                            slots.prefix.invoke()
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.text.isEmpty() && config.placeholder.isNotEmpty()) {
                                Text(
                                    text = config.placeholder,
                                    style = typography.bodySmall.copy(color = themeColors.textMuted),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        if (slots.suffix != null) {
                            slots.suffix.invoke()
                        }
                    }
                }
            )
        }

        if (config.supportingText != null) {
            Text(
                text = config.supportingText,
                style = typography.caption.copy(color = if (state.isError) themeColors.semantic.error else themeColors.textMuted),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        }
    }
}

/**
 * Convenience String overload for KNetTextField.
 */
@Composable
fun KNetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    config: InputFieldConfig = InputFieldConfig.Default,
    state: InputFieldState = InputFieldState.Default,
    slots: InputFieldSlots = InputFieldSlots.Empty
) {
    var tfv by remember { mutableStateOf(TextFieldValue(text = value)) }
    val currentTfv = if (tfv.text == value) {
        tfv
    } else {
        TextFieldValue(text = value, selection = TextRange(value.length))
    }

    KNetTextField(
        value = currentTfv,
        onValueChange = { newTfv ->
            tfv = newTfv
            onValueChange(newTfv.text)
        },
        modifier = modifier,
        config = config,
        state = state,
        slots = slots
    )
}

/**
 * Password field with toggleable trailing visibility icon.
 */
@Composable
fun KNetPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Password",
    enabled: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }
    KNetTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        config = InputFieldConfig(
            placeholder = placeholder,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            showHoverPopupOnOverflow = false
        ),
        state = InputFieldState(enabled = enabled),
        slots = InputFieldSlots(
            suffix = {
                KNetIconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    icon = if (passwordVisible) KNetIcons.VisibilityOff else KNetIcons.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    enabled = enabled,
                    size = 28.dp,
                    iconSize = 15.dp,
                    tint = KNetTheme.colors.textSecondary
                )
            }
        )
    )
}

/**
 * Multi-line field container composable.
 */
@Composable
fun KNetMultilineField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> themeColors.semantic.error
        focused -> themeColors.borderFocused
        else -> themeColors.border
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shapes.small)
            .background(themeColors.surfaceVariant)
            .border(1.dp, borderColor, shapes.small)
            .onFocusChanged { focused = it.isFocused }
            .textCursor()
            .padding(8.dp),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = false,
        textStyle = typography.bodySmall.copy(
            color = if (enabled) themeColors.textPrimary else themeColors.textMuted
        ),
        cursorBrush = SolidColor(themeColors.accent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = typography.bodySmall.copy(color = themeColors.textMuted)
                    )
                }
                innerTextField()
            }
        }
    )
}

/** String convenience overload for [KNetMultilineField]. */
@Composable
fun KNetMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    val resolvedValue = if (textFieldValue.text == value) textFieldValue else TextFieldValue(value, TextRange(value.length))
    KNetMultilineField(
        value = resolvedValue,
        onValueChange = {
            textFieldValue = it
            onValueChange(it.text)
        },
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError
    )
}
