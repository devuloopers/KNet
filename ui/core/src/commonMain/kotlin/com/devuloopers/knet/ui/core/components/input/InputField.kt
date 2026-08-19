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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
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
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = anchorBounds.left.coerceIn(0, maxX)
        val belowY = anchorBounds.bottom + offsetPx
        val aboveY = anchorBounds.top - popupContentSize.height - offsetPx
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = (if (belowY + popupContentSize.height <= windowSize.height) belowY else aboveY).coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}

private class HoverDelayController {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * High-density single line text input field using cohesive parameter objects (< 7 parameters total).
 * Supports precision text overflow hover popups anchored below the field when text exceeds viewport bounds.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val hoverDelayController = remember { HoverDelayController() }

    var containerWidthPx by remember { mutableStateOf(0) }
    var isHovered by remember { mutableStateOf(false) }
    var isMouseStationary by remember { mutableStateOf(false) }
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

    fun resetHoverTimer() {
        isMouseStationary = false
        hoverDelayController.cancel()
        if (isHovered) {
            hoverDelayController.job = coroutineScope.launch {
                delay(config.hoverDebounceMs)
                isMouseStationary = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose(hoverDelayController::cancel)
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
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(config.fieldHeight ?: dimensions.inputHeightStandard)
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
                    hoverDelayController.cancel()
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
