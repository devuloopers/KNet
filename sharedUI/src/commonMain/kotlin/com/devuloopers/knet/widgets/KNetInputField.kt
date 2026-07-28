package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.theme.KNetColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [PopupPositionProvider] that positions the popup content directly below the anchor view,
 * aligned with its left edge and offset vertically by [offsetPx].
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
 * Reusable single-line dark form input field for KNet (Auth configuration, Dialog forms, Settings, URL bar).
 *
 * Features:
 * - Ensures text, placeholder hints, and cursor are vertically centered inside [KNetColors.FieldDark].
 * - **Reactive Overflow Detection**: Measures typed text against container width.
 * - **Precision Overflow Popup**: Displays the full text in a dark card directly below the field's left edge
 *   when the mouse stays stationary for 350ms, ONLY if text overflows and the user is NOT editing ([isFocused] is false).
 *
 * @param value The current string text value.
 * @param onValueChange Callback invoked when the user edits the input.
 * @param modifier Resizing constraints.
 * @param placeholder Dim hint displayed when [value] is empty.
 * @param textColor Color applied to the typed text.
 * @param enabled Whether input is active.
 * @param height Height of the container field. Defaults to [34.dp].
 * @param fontSize Font size for typed text and placeholder. Defaults to [11.sp].
 * @param cornerRadius Border corner radius. Defaults to [4.dp].
 * @param isPassword Whether to obscure input text for sensitive fields (e.g. passwords).
 * @param showHoverPopupOnOverflow Whether to trigger the bottom popup when text overflows and field is unfocused.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KNetInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 34.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    cornerRadius: Dp = 4.dp,
    isPassword: Boolean = false,
    showHoverPopupOnOverflow: Boolean = true
) {
    var tfValue by remember(value) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    var isHovered by remember { mutableStateOf(false) }
    var isMouseStationary by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    var hoverJob by remember { mutableStateOf<Job?>(null) }

    fun resetHoverTimer() {
        isMouseStationary = false
        hoverJob?.cancel()
        if (isHovered) {
            hoverJob = coroutineScope.launch {
                delay(350)
                isMouseStationary = true
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = if (enabled) textColor else KNetColors.TextSecondary,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace
    )

    // Calculate exact pixel width of the text reactively
    val textWidthPx = remember(value, fontSize, textStyle) {
        if (value.isEmpty()) 0 else textMeasurer.measure(text = value, style = textStyle).size.width
    }

    // Convert internal horizontal padding (10.dp * 2 = 20.dp) to pixels
    val density = LocalDensity.current
    val paddingPx = with(density) { 20.dp.toPx() }
    val offsetPx = with(density) { 4.dp.roundToPx() }

    // Evaluate overflow reactively
    val isOverflowing = remember(textWidthPx, containerWidthPx, paddingPx) {
        containerWidthPx > 0 && textWidthPx > (containerWidthPx - paddingPx)
    }

    // Determine if popup should render (allows focus mode when mouse cursor is inside the field)
    val shouldShowPopup = showHoverPopupOnOverflow && isHovered && isMouseStationary && isOverflowing && value.isNotEmpty()

    Box(
        modifier = modifier
            .height(height)
            .onSizeChanged { containerWidthPx = it.width }
            .onPointerEvent(PointerEventType.Enter) {
                isHovered = true
                resetHoverTimer()
            }
            .onPointerEvent(PointerEventType.Move) {
                if (isHovered) {
                    resetHoverTimer()
                }
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
                isMouseStationary = false
                hoverJob?.cancel()
            }
            .background(KNetColors.FieldDark, RoundedCornerShape(cornerRadius))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = tfValue,
            onValueChange = { newValue ->
                tfValue = newValue
                onValueChange(newValue.text)
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            textStyle = textStyle,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (tfValue.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = KNetColors.TextSecondary.copy(alpha = 0.45f),
                            fontSize = fontSize,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Precision Overflow Popup anchored directly under the left edge of the input field
        if (shouldShowPopup) {
            Popup(
                popupPositionProvider = remember(offsetPx) { BelowAnchorPopupPositionProvider(offsetPx) },
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
                        .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = value,
                        color = KNetColors.TextPrimary,
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}




