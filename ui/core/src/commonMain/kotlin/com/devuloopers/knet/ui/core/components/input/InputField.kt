package com.devuloopers.knet.ui.core.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.devuloopers.knet.ui.core.foundation.pointer.textCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * A [PopupPositionProvider] that positions the popup content directly below the anchor composable,
 * aligned with its left edge and offset vertically by [offsetPx] pixels.
 *
 * Uses absolute screen coordinates ([anchorBounds]) ensuring stable positioning regardless of
 * any parent layout structure.
 */
private class BelowAnchorPopupPositionProvider(
    private val offsetPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left
        val y = anchorBounds.bottom + offsetPx
        return IntOffset(x, y)
    }
}

/**
 * High-density single line text input field using cohesive parameter objects (< 7 parameters total).
 * Supports precision text overflow hover popups anchored below the field when text exceeds viewport bounds.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun KNetTextField(
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val backgroundColor = config.backgroundColor ?: themeColors.surfaceVariant
    val borderColor = config.borderColor ?: if (state.isError) themeColors.semantic.error else themeColors.border
    val textStyle = typography.bodySmall.copy(color = themeColors.textPrimary)

    var containerWidthPx by remember { mutableStateOf(0) }
    var isHovered by remember { mutableStateOf(false) }
    var isMouseStationary by remember { mutableStateOf(false) }
    var hoverJob by remember { mutableStateOf<Job?>(null) }

    fun resetHoverTimer() {
        isMouseStationary = false
        hoverJob?.cancel()
        if (isHovered) {
            hoverJob = coroutineScope.launch {
                delay(config.hoverDebounceMs)
                isMouseStationary = true
            }
        }
    }

    // Measure exact text width reactively
    val textMeasurer = rememberTextMeasurer()
    val textWidthPx = remember(value.text, textStyle) {
        if (value.text.isEmpty()) 0 else textMeasurer.measure(text = value.text, style = textStyle).size.width
    }
    val paddingPx = with(density) { 16.dp.toPx() }
    val isOverflowing = remember(textWidthPx, containerWidthPx, paddingPx) {
        containerWidthPx > 0 && textWidthPx > (containerWidthPx - paddingPx)
    }

    val isPassword = config.visualTransformation is PasswordVisualTransformation
    val shouldShowPopup = config.showHoverPopupOnOverflow && !isPassword && isHovered && isMouseStationary && isOverflowing && value.text.isNotEmpty()

    Column(
        modifier = Modifier
            .height(dimensions.inputHeightStandard)
            .then(modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onSizeChanged { containerWidthPx = it.width }
                .onPointerEvent(PointerEventType.Enter) {
                    isHovered = true
                    resetHoverTimer()
                }
                .onPointerEvent(PointerEventType.Move) {
                    if (isHovered) resetHoverTimer()
                }
                .onPointerEvent(PointerEventType.Exit) {
                    isHovered = false
                    isMouseStationary = false
                    hoverJob?.cancel()
                }
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.small)
                    .background(backgroundColor)
                    .then(if (borderColor != androidx.compose.ui.graphics.Color.Transparent) Modifier.border(1.dp, borderColor, shapes.small) else Modifier)
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

            if (shouldShowPopup) {
                Popup(
                    popupPositionProvider = remember(density) {
                        BelowAnchorPopupPositionProvider(with(density) { 4.dp.roundToPx() })
                    },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {}
                            .widthIn(max = 600.dp)
                            .clip(shapes.small)
                            .background(themeColors.surface)
                            .border(1.dp, themeColors.border, shapes.small)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = value.text,
                            style = typography.bodySmall.copy(color = themeColors.textPrimary)
                        )
                    }
                }
            }
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
public fun KNetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    config: InputFieldConfig = InputFieldConfig.Default,
    state: InputFieldState = InputFieldState.Default,
    slots: InputFieldSlots = InputFieldSlots.Empty
) {
    var tfv by remember { mutableStateOf(TextFieldValue(text = value)) }
    val currentTfv = if (tfv.text == value) tfv else TextFieldValue(text = value)

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
 * Convenience inline overload for simple single-line fields with discrete arguments.
 */
@Composable
public fun KNetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    KNetTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        config = InputFieldConfig(
            placeholder = placeholder,
            supportingText = supportingText,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        ),
        state = InputFieldState(
            enabled = enabled,
            readOnly = readOnly,
            isError = isError
        ),
        slots = InputFieldSlots(
            prefix = prefix,
            suffix = suffix
        )
    )
}

/**
 * Convenience inline overload for simple single-line fields with discrete arguments (TextFieldValue).
 */
@Composable
public fun KNetTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    KNetTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        config = InputFieldConfig(
            placeholder = placeholder,
            supportingText = supportingText,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        ),
        state = InputFieldState(
            enabled = enabled,
            readOnly = readOnly,
            isError = isError
        ),
        slots = InputFieldSlots(
            prefix = prefix,
            suffix = suffix
        )
    )
}

/**
 * High-density input field wrapper alias (TextFieldValue variant).
 */
@Composable
public fun KNetInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    if (singleLine) {
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        )
    } else {
        KNetMultilineField(
            value = value.text,
            onValueChange = { onValueChange(TextFieldValue(it)) },
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError
        )
    }
}

/**
 * High-density input field wrapper alias.
 */
@Composable
public fun KNetInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    if (singleLine) {
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        )
    } else {
        KNetMultilineField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError
        )
    }
}

/**
 * Password field with toggleable trailing visibility icon.
 */
@Composable
public fun KNetPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Password",
    enabled: Boolean = true
) {
    KNetTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        config = InputFieldConfig(
            placeholder = placeholder,
            visualTransformation = PasswordVisualTransformation(),
            showHoverPopupOnOverflow = false
        ),
        state = InputFieldState(enabled = enabled)
    )
}

/**
 * Multi-line field container composable.
 */
@Composable
public fun KNetMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val borderColor = if (isError) themeColors.semantic.error else themeColors.border

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shapes.small)
            .background(themeColors.surfaceVariant)
            .border(1.dp, borderColor, shapes.small)
            .textCursor()
            .padding(8.dp),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = false,
        textStyle = typography.bodySmall.copy(color = themeColors.textPrimary),
        cursorBrush = SolidColor(themeColors.accent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
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
